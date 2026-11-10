package pt.sentinela.alertas;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** Corre de 30 em 30 minutos, mesmo com a app fechada: lê os avisos do IPMA e a temperatura,
 *  atualiza o widget e notifica avisos laranja/vermelho novos. */
public class WidgetWorker extends Worker {
    static final String[] LV = {"green", "yellow", "orange", "red"};
    static final String[] LVN = {"Verde", "Amarelo", "Laranja", "Vermelho"};

    public WidgetWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    static String get(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(20000);
        h.setRequestProperty("User-Agent", "Sentinela-Android");
        try (InputStream in = h.getInputStream(); BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            return sb.toString();
        } finally {
            h.disconnect();
        }
    }

    static int idx(String s) {
        for (int i = 0; i < LV.length; i++) if (LV[i].equals(s)) return i;
        return 0;
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        SharedPreferences p = c.getSharedPreferences(SentinelaWidget.PREFS, Context.MODE_PRIVATE);
        String area = p.getString("area", "LRA");
        String areaName = p.getString("areaName", "Leiria");
        String lat = p.getString("lat", "39.7436");
        String lon = p.getString("lon", "-8.8071");
        Locale pt = new Locale("pt", "PT");
        try {
            JSONArray arr = new JSONArray(get("https://api.ipma.pt/open-data/forecast/warnings/warnings_www.json"));
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("Europe/Lisbon"));
            long now = System.currentTimeMillis();
            int best = 0; boolean bestAct = false; long bestS = 0, bestE = 0; String bestType = "", bestText = "";
            int count = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject w = arr.getJSONObject(i);
                if (!area.equals(w.optString("idAreaAviso"))) continue;
                int L = idx(w.optString("awarenessLevelID"));
                if (L <= 0) continue;
                long s = f.parse(w.getString("startTime")).getTime();
                long e = f.parse(w.getString("endTime")).getTime();
                if (e < now) continue;
                count++;
                boolean act = s <= now;
                if (L > best || (L == best && act && !bestAct)) {
                    best = L; bestAct = act; bestS = s; bestE = e;
                    bestType = w.optString("awarenessTypeName"); bestText = w.optString("text");
                }
            }
            SimpleDateFormat hf = new SimpleDateFormat("EEE HH:mm", pt);
            String titulo, sub;
            if (best == 0) {
                titulo = "Sem avisos";
                sub = areaName + " · próximos 3 dias";
            } else {
                titulo = bestType + " · " + LVN[best];
                sub = (bestAct ? "Até " + hf.format(new Date(bestE)) : "A partir de " + hf.format(new Date(bestS)))
                        + (count > 1 ? " · +" + (count - 1) + " avisos" : "");
            }
            p.edit().putInt("lvl", best).putString("titulo", titulo).putString("sub", sub).putLong("upd", now).apply();
            String id = area + "|" + bestType + "|" + best + "|" + bestS;
            if (best >= 2 && p.getBoolean("notify", true) && !id.equals(p.getString("notified", ""))) {
                notifyWarn(c, "Aviso " + LVN[best].toLowerCase(pt) + ": " + bestType,
                        areaName + " · " + hf.format(new Date(bestS)) + " → " + hf.format(new Date(bestE))
                                + (bestText.isEmpty() ? "" : "\n" + bestText));
                p.edit().putString("notified", id).apply();
            }
        } catch (Exception ex) {
            // sem rede ou fonte em baixo: o widget mantém os últimos dados
        }
        try {
            JSONObject j = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m&timezone=Europe%2FLisbon"));
            long t = Math.round(j.getJSONObject("current").getDouble("temperature_2m"));
            p.edit().putString("temp", t + "°").apply();
        } catch (Exception ex) {
            // ignora
        }
        SentinelaWidget.updateAll(c);
        return Result.success();
    }

    static void notifyWarn(Context c, String title, String body) {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            String ch = "sentinela_avisos";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = c.getSystemService(NotificationManager.class);
                if (nm != null && nm.getNotificationChannel(ch) == null)
                    nm.createNotificationChannel(new NotificationChannel(ch, "Avisos IPMA (segundo plano)", NotificationManager.IMPORTANCE_HIGH));
            }
            Intent i = new Intent(c, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, ch)
                    .setSmallIcon(R.drawable.ic_stat_sentinela)
                    .setColor(0xFFFFEB3B)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            NotificationManagerCompat.from(c).notify((int) (System.currentTimeMillis() % 100000), b.build());
        } catch (Exception ignored) {
        }
    }
}

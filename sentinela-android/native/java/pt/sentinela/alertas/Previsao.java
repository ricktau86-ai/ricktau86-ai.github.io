package pt.sentinela.alertas;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Widgets de previsão (chuva 2 h, hora a hora, 5 dias, gráfico 24 h).
 *  Desenhados como imagem para caberem barras e gráficos; dados do Open-Meteo. */
public class Previsao {
    static final int CHUVA = 0, HORAS = 1, DIAS = 2, GRAFICO = 3;
    static final int FG = 0xFFE6E1E5, MU = 0xFF938F99, AZ = 0xFF4FC3F7, LAR = 0xFFFF9800, BG = 0xF01E1E1E, TRILHO = 0xFF3A3A3A;
    static final Locale PT = new Locale("pt", "PT");

    /* ---------- dados ---------- */
    static void fetch(SharedPreferences p, String lat, String lon) {
        try {
            String s = WidgetWorker.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&minutely_15=precipitation&forecast_minutely_15=10"
                    + "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,is_day,wind_gusts_10m&forecast_hours=25"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum&forecast_days=5"
                    + "&timezone=Europe%2FLisbon");
            new JSONObject(s); // só guarda se for JSON válido
            p.edit().putString("prev", s).putLong("prev_upd", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    static String emoji(int c, boolean dia) {
        if (c == 0) return dia ? "☀️" : "🌙";
        if (c <= 2) return dia ? "⛅" : "☁️";
        if (c == 3) return "☁️";
        if (c == 45 || c == 48) return "🌫️";
        if (c >= 51 && c <= 67) return "🌧️";
        if (c >= 71 && c <= 77) return "🌨️";
        if (c >= 80 && c <= 82) return "🌦️";
        if (c == 85 || c == 86) return "🌨️";
        if (c >= 95) return "⛈️";
        return "☁️";
    }

    static String mm(double v) { return v <= 0 ? "–" : String.format(PT, "%.1f mm", v); }

    static long t(String iso) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Lisbon"));
            return f.parse(iso).getTime();
        } catch (Exception e) { return 0; }
    }

    /** índices horários a partir da hora atual */
    static int primeiraHora(JSONArray ht) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < ht.length(); i++) if (t(ht.optString(i)) + 3600000L > now) return i;
        return 0;
    }

    /* ---------- desenho ---------- */
    static void update(Context c, AppWidgetManager m, int[] ids, int tipo) {
        for (int id : ids) {
            try {
                Bundle o = m.getAppWidgetOptions(id);
                int wdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                int hdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
                if (wdp < 120) wdp = 320;
                if (hdp < 60) hdp = tipo == DIAS ? 190 : 150;
                float dens = c.getResources().getDisplayMetrics().density;
                float s = (float) Math.min(dens, Math.sqrt(700000.0 / (wdp * (double) hdp)));
                Bitmap b = Bitmap.createBitmap(Math.round(wdp * s), Math.round(hdp * s), Bitmap.Config.ARGB_8888);
                Canvas k = new Canvas(b);
                k.scale(s, s);
                desenhar(c, k, wdp, hdp, tipo);
                RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_img);
                v.setImageViewBitmap(R.id.w_img, b);
                v.setOnClickPendingIntent(R.id.w_img, Widgets.open(c, null, 60 + tipo));
                m.updateAppWidget(id, v);
            } catch (Exception ignored) { }
        }
    }

    static Paint tp(float sp, int cor, boolean neg) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sp);
        p.setColor(cor);
        if (neg) p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return p;
    }

    static void centro(Canvas k, String s, float x, float y, Paint p) {
        p.setTextAlign(Paint.Align.CENTER);
        k.drawText(s, x, y, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    static void direita(Canvas k, String s, float x, float y, Paint p) {
        p.setTextAlign(Paint.Align.RIGHT);
        k.drawText(s, x, y, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    static Paint cheio(int cor) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(cor);
        return p;
    }

    static void desenhar(Context c, Canvas k, float W, float H, int tipo) throws Exception {
        k.drawRoundRect(new RectF(0, 0, W, H), 22, 22, cheio(BG));
        SharedPreferences p = Widgets.prefs(c);
        String js = p.getString("prev", "");
        String local = p.getString("name", "Leiria");
        float pad = 14;
        if (js.isEmpty()) {
            k.drawText("A carregar a previsão…", pad, 34, tp(14, FG, true));
            k.drawText("Abra a Sentinela uma vez se demorar.", pad, 56, tp(11.5f, MU, false));
            return;
        }
        JSONObject j = new JSONObject(js);
        String upd = Widgets.hhmm(p.getLong("prev_upd", 0));
        if (tipo == CHUVA) chuva(k, W, H, pad, j, local, upd);
        else if (tipo == HORAS) horas(k, W, H, pad, j);
        else if (tipo == DIAS) dias(k, W, H, pad, j, local);
        else grafico(k, W, H, pad, j);
    }

    static void cabecalho(Canvas k, float W, float pad, String titulo, String dir, int corDir) {
        k.drawText(titulo, pad, 26, tp(13.5f, FG, true));
        direita(k, dir, W - pad, 26, tp(11.5f, corDir, false));
    }

    /* A · chuva nas próximas 2 h, de 15 em 15 minutos */
    static void chuva(Canvas k, float W, float H, float pad, JSONObject j, String local, String upd) throws Exception {
        cabecalho(k, W, pad, "🌧️ Chuva nas próximas 2 h", local + " · " + upd, MU);
        JSONObject mq = j.getJSONObject("minutely_15");
        JSONArray mt = mq.getJSONArray("time"), mp = mq.getJSONArray("precipitation");
        long now = System.currentTimeMillis();
        List<Double> v = new ArrayList<>();
        List<String> lb = new ArrayList<>();
        for (int i = 0; i < mt.length() && v.size() < 8; i++) {
            if (t(mt.getString(i)) + 15 * 60000L <= now) continue;
            v.add(mp.isNull(i) ? 0 : mp.getDouble(i));
            lb.add(mt.getString(i).substring(11, 16));
        }
        if (v.isEmpty()) { k.drawText("Sem dados de 15 em 15 minutos", pad, 56, tp(13, MU, false)); return; }
        double tot = 0, max = 0;
        int ini = -1, fim = -1, pico = 0;
        for (int i = 0; i < v.size(); i++) {
            double x = v.get(i);
            tot += x;
            if (x >= 0.1) { if (ini < 0) ini = i; fim = i; }
            if (x > max) { max = x; pico = i; }
        }
        String frase;
        Paint fr = tp(15, FG, true);
        if (ini < 0) frase = "Sem chuva nas próximas 2 horas";
        else if (ini == 0) frase = fim >= v.size() - 1 ? "Chuva durante as próximas 2 h" : "Está a chover · para por volta das " + lb.get(Math.min(fim + 1, v.size() - 1));
        else frase = "Começa a chover por volta das " + lb.get(ini);
        k.drawText(frase, pad, 52, fr);
        float top = 64, base = H - 40, bw = (W - 2 * pad) / v.size();
        double esc = Math.max(1.6, max);
        for (int i = 0; i < v.size(); i++) {
            double x = v.get(i);
            float h = (float) Math.max(3, x / esc * (base - top));
            int cor = x >= 0.5 ? AZ : x >= 0.1 ? 0x9981D4FA : TRILHO;
            k.drawRoundRect(new RectF(pad + i * bw + 2, base - h, pad + (i + 1) * bw - 2, base), 4, 4, cheio(cor));
            if (i % 2 == 0) centro(k, lb.get(i), pad + i * bw + bw / 2, base + 13, tp(9.5f, MU, false));
        }
        String rod = ini < 0 ? "Previsão de modelo (Open-Meteo), não é radar"
                : "Mais forte às " + lb.get(pico) + " · " + String.format(PT, "%.1f", tot) + " mm até às " + lb.get(v.size() - 1);
        k.drawText(rod, pad, H - 10, tp(11.5f, MU, false));
    }

    /* B · hora a hora (8 horas) */
    static void horas(Canvas k, float W, float H, float pad, JSONObject j) throws Exception {
        JSONObject h = j.getJSONObject("hourly");
        JSONArray ht = h.getJSONArray("time"), tt = h.getJSONArray("temperature_2m"), wc = h.getJSONArray("weather_code"),
                pp = h.getJSONArray("precipitation_probability"), pr = h.getJSONArray("precipitation"), dy = h.getJSONArray("is_day"),
                gu = h.getJSONArray("wind_gusts_10m");
        int i0 = primeiraHora(ht), n = Math.min(8, ht.length() - i0);
        if (W < 260) n = Math.min(n, 5);
        double gmax = 0; int gi = i0;
        for (int i = i0; i < i0 + n; i++) if (gu.optDouble(i, 0) > gmax) { gmax = gu.optDouble(i, 0); gi = i; }
        cabecalho(k, W, pad, "Hora a hora", "💨 rajada até " + Math.round(gmax) + " km/h às " + ht.getString(gi).substring(11, 13) + "h",
                gmax >= 90 ? 0xFFFF5449 : gmax >= 60 ? LAR : MU);
        float cw = (W - 2 * pad) / n;
        float esp = Math.max(0, H - 150) / 6; // espalha se o widget for mais alto
        for (int a = 0; a < n; a++) {
            int i = i0 + a;
            float x = pad + a * cw + cw / 2;
            float y = 46 + esp;
            centro(k, ht.getString(i).substring(11, 13) + "h", x, y, tp(11, MU, false));
            centro(k, emoji(wc.optInt(i), dy.optInt(i, 1) == 1), x, y + 22 + esp, tp(17, FG, false));
            centro(k, Math.round(tt.optDouble(i)) + "°", x, y + 41 + esp * 2, tp(13, FG, true));
            int pr100 = pp.isNull(i) ? 0 : pp.getInt(i);
            float bt = y + 48 + esp * 2, bb = bt + 26;
            k.drawRoundRect(new RectF(x - 4, bt, x + 4, bb), 4, 4, cheio(TRILHO));
            if (pr100 > 0) k.drawRoundRect(new RectF(x - 4, bb - Math.max(4, (bb - bt) * pr100 / 100f), x + 4, bb), 4, 4, cheio(AZ));
            centro(k, pr100 + "%", x, bb + 13, tp(10.5f, AZ, false));
            if (cw >= 36) centro(k, mm(pr.optDouble(i, 0)), x, bb + 26, tp(9, MU, false));
        }
    }

    /* C · próximos 5 dias */
    static void dias(Canvas k, float W, float H, float pad, JSONObject j, String local) throws Exception {
        cabecalho(k, W, pad, "Próximos 5 dias", local, MU);
        JSONObject d = j.getJSONObject("daily");
        JSONArray dt = d.getJSONArray("time"), wc = d.getJSONArray("weather_code"), mx = d.getJSONArray("temperature_2m_max"),
                mn = d.getJSONArray("temperature_2m_min"), pp = d.getJSONArray("precipitation_probability_max"), ps = d.getJSONArray("precipitation_sum");
        int n = Math.min(5, dt.length());
        double lo = 99, hi = -99;
        for (int i = 0; i < n; i++) { lo = Math.min(lo, mn.optDouble(i)); hi = Math.max(hi, mx.optDouble(i)); }
        float linha = (H - 40) / n;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US), dia = new SimpleDateFormat("EEE", PT);
        boolean largo = W >= 300;
        for (int i = 0; i < n; i++) {
            float y = 40 + linha * i + linha / 2 + 5;
            String nome = i == 0 ? "Hoje" : dia.format(f.parse(dt.getString(i))).replace(".", "");
            nome = nome.substring(0, 1).toUpperCase(PT) + nome.substring(1);
            k.drawText(nome, pad, y, tp(13, FG, true));
            centro(k, emoji(wc.optInt(i), true), pad + 54, y, tp(16, FG, false));
            k.drawText((pp.isNull(i) ? 0 : pp.getInt(i)) + "%", pad + 74, y, tp(12.5f, AZ, false));
            if (largo) k.drawText(mm(ps.optDouble(i, 0)).replace("–", "0 mm"), pad + 112, y, tp(11, MU, false));
            float bx1 = W - pad - 34 - 70, bx0 = largo ? Math.max(pad + 176, bx1 - 70) : pad + 112;
            direita(k, Math.round(mn.optDouble(i)) + "°", bx0 - 6, y, tp(12, MU, false));
            float by = y - 4;
            k.drawRoundRect(new RectF(bx0, by - 2.5f, bx1 + 60, by + 2.5f), 3, 3, cheio(TRILHO));
            double span = Math.max(1, hi - lo);
            float a = bx0 + (float) ((mn.optDouble(i) - lo) / span) * (bx1 + 60 - bx0), b = bx0 + (float) ((mx.optDouble(i) - lo) / span) * (bx1 + 60 - bx0);
            Paint g = cheio(AZ);
            g.setShader(new LinearGradient(a, 0, Math.max(a + 1, b), 0, AZ, LAR, Shader.TileMode.CLAMP));
            k.drawRoundRect(new RectF(a, by - 2.5f, Math.max(a + 5, b), by + 2.5f), 3, 3, g);
            direita(k, Math.round(mx.optDouble(i)) + "°", W - pad, y, tp(13, FG, true));
        }
    }

    /* D · gráfico 24 h: temperatura (linha) e chuva (barras) */
    static void grafico(Canvas k, float W, float H, float pad, JSONObject j) throws Exception {
        JSONObject h = j.getJSONObject("hourly");
        JSONArray ht = h.getJSONArray("time"), tt = h.getJSONArray("temperature_2m"), pr = h.getJSONArray("precipitation");
        int i0 = primeiraHora(ht), n = Math.min(24, ht.length() - i0);
        k.drawText("Próximas 24 h", pad, 26, tp(13.5f, FG, true));
        Paint l1 = tp(11, LAR, false), l2 = tp(11, AZ, false);
        float wl2 = l2.measureText("▮ chuva");
        direita(k, "▮ chuva", W - pad, 26, l2);
        direita(k, "━ temperatura  ", W - pad - wl2, 26, l1);
        double lo = 99, hi = -99, pmax = 0, ptot = 0;
        for (int i = i0; i < i0 + n; i++) {
            lo = Math.min(lo, tt.optDouble(i)); hi = Math.max(hi, tt.optDouble(i));
            pmax = Math.max(pmax, pr.optDouble(i, 0)); ptot += pr.optDouble(i, 0);
        }
        float top = 44, base = H - 22, x0 = pad, x1 = W - pad, cw = (x1 - x0) / n;
        double esc = Math.max(2.5, pmax);
        for (int a = 0; a < n; a++) {
            double v = pr.optDouble(i0 + a, 0);
            if (v < 0.05) continue;
            float hh = (float) (v / esc * (base - top) * 0.55);
            k.drawRoundRect(new RectF(x0 + a * cw + 1.5f, base - Math.max(2, hh), x0 + (a + 1) * cw - 1.5f, base), 2, 2, cheio(0xD94FC3F7));
        }
        double span = Math.max(3, hi - lo);
        Path path = new Path();
        float maxX = 0, maxY = 0, minX = 0, minY = 0;
        for (int a = 0; a < n; a++) {
            double v = tt.optDouble(i0 + a);
            float x = x0 + a * cw + cw / 2, y = (float) (top + 8 + (hi - v) / span * (base - top - 30));
            if (a == 0) path.moveTo(x, y); else path.lineTo(x, y);
            if (v == hi && maxX == 0) { maxX = x; maxY = y; }
            if (v == lo && minX == 0) { minX = x; minY = y; }
        }
        Paint ln = new Paint(Paint.ANTI_ALIAS_FLAG);
        ln.setStyle(Paint.Style.STROKE);
        ln.setStrokeWidth(2.2f);
        ln.setColor(LAR);
        ln.setStrokeJoin(Paint.Join.ROUND);
        k.drawPath(path, ln);
        centro(k, Math.round(hi) + "°", maxX, maxY - 5, tp(10.5f, LAR, true));
        centro(k, Math.round(lo) + "°", minX, minY + 14, tp(10.5f, LAR, true));
        for (int a = 0; a < n; a += 4) {
            Paint q = tp(9.5f, MU, false);
            String s = ht.getString(i0 + a).substring(11, 13) + "h";
            if (a == 0) k.drawText(s, x0, H - 8, q); else centro(k, s, x0 + a * cw + cw / 2, H - 8, q);
        }
        if (ptot >= 0.1) direita(k, String.format(PT, "%.1f mm em 24 h", ptot), W - pad, H - 8, tp(10, AZ, false));
    }
}

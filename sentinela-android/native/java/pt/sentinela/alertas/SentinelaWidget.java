package pt.sentinela.alertas;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Widget do ecrã inicial: aviso IPMA mais grave da área + temperatura. */
public class SentinelaWidget extends AppWidgetProvider {
    public static final String PREFS = "sentinela_widget";
    static final int[] COLORS = {0xFF4CAF50, 0xFFFFD54F, 0xFFFF9800, 0xFFFF5449};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        render(c, m, ids);
        schedule(c);
        refreshNow(c);
    }

    @Override
    public void onEnabled(Context c) {
        schedule(c);
        refreshNow(c);
    }

    static Constraints net() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    static void schedule(Context c) {
        PeriodicWorkRequest r = new PeriodicWorkRequest.Builder(WidgetWorker.class, 30, TimeUnit.MINUTES)
                .setConstraints(net()).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("sentinela_widget", ExistingPeriodicWorkPolicy.KEEP, r);
    }

    static void refreshNow(Context c) {
        WorkManager.getInstance(c).enqueue(new OneTimeWorkRequest.Builder(WidgetWorker.class).setConstraints(net()).build());
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, SentinelaWidget.class));
        if (ids != null && ids.length > 0) render(c, m, ids);
    }

    static void render(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int lvl = Math.max(0, Math.min(3, p.getInt("lvl", 0)));
        long upd = p.getLong("upd", 0);
        String sub = p.getString("sub", "Toque para abrir a Sentinela");
        if (upd > 0) sub = sub + " · " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(upd));
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_sentinela);
        v.setTextViewText(R.id.w_place, p.getString("name", "Sentinela"));
        v.setTextViewText(R.id.w_temp, p.getString("temp", ""));
        v.setTextViewText(R.id.w_title, p.getString("titulo", "A carregar avisos…"));
        v.setTextColor(R.id.w_title, COLORS[lvl]);
        v.setInt(R.id.w_bar, "setBackgroundColor", COLORS[lvl]);
        v.setTextViewText(R.id.w_sub, sub);
        String storm = p.getString("storm", "");
        v.setTextViewText(R.id.w_storm, storm);
        v.setViewVisibility(R.id.w_storm, storm.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_root, pi);
        for (int id : ids) m.updateAppWidget(id, v);
    }
}

package pt.sentinela.alertas;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Desenho de todos os widgets da Sentinela a partir dos dados guardados pelo WidgetWorker. */
public class Widgets {
    static final int[] LVC = {0xFF4CAF50, 0xFFFFD54F, 0xFFFF9800, 0xFFFF5449};
    static final int[] RCMC = {0xFF938F99, 0xFF4CAF50, 0xFFFFD54F, 0xFFFF9800, 0xFFFF5449, 0xFFD32F2F};
    static final String[] RCMN = {"—", "Reduzido", "Moderado", "Elevado", "Muito elevado", "Máximo"};

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(SentinelaWidget.PREFS, Context.MODE_PRIVATE); }

    static String hhmm(long t) { return t > 0 ? new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t)) : ""; }

    /** Abre a app; com "acao" abre logo no sítio certo (estoubem, ajuda, socorros, tempestades, extra). */
    static PendingIntent open(Context c, String acao, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (acao != null) i.putExtra("acao", acao);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        SentinelaWidget.updateAll(c);
        Class<?>[] cls = {TempoWidget.class, MiniWidget.class, OcorrenciasWidget.class, EstouBemWidget.class, AmbienteWidget.class, TempestadeWidget.class};
        for (Class<?> k : cls) {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, k));
            if (ids == null || ids.length == 0) continue;
            if (k == TempoWidget.class) tempo(c, m, ids);
            else if (k == MiniWidget.class) mini(c, m, ids);
            else if (k == OcorrenciasWidget.class) ocorrencias(c, m, ids);
            else if (k == EstouBemWidget.class) estouBem(c, m, ids);
            else if (k == AmbienteWidget.class) ambiente(c, m, ids);
            else if (k == TempestadeWidget.class) tempestade(c, m, ids);
        }
    }

    static void tempo(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences p = prefs(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_tempo);
        v.setTextViewText(R.id.t_place, p.getString("name", "Sentinela") + " · Tempo");
        v.setTextViewText(R.id.t_temp, p.getString("temp", "—"));
        v.setTextViewText(R.id.t_desc, p.getString("w_desc", "A carregar…"));
        v.setTextViewText(R.id.t_minmax, p.getString("w_minmax", ""));
        v.setTextViewText(R.id.t_rain, p.getString("w_rain", ""));
        v.setTextViewText(R.id.t_gust, p.getString("w_gust", ""));
        long g = p.getLong("w_gustv", 0);
        v.setTextColor(R.id.t_gust, g >= 90 ? 0xFFFF5449 : g >= 60 ? 0xFFFF9800 : 0xFFCAC4D0);
        long u = p.getLong("w_upd", 0);
        v.setTextViewText(R.id.t_upd, u > 0 ? "atualizado " + hhmm(u) : "");
        v.setOnClickPendingIntent(R.id.w_root, open(c, null, 10));
        for (int id : ids) m.updateAppWidget(id, v);
    }

    static void mini(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences p = prefs(c);
        int lvl = Math.max(0, Math.min(3, p.getInt("lvl", 0)));
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_mini);
        v.setTextColor(R.id.m_dot, LVC[lvl]);
        v.setTextViewText(R.id.m_temp, p.getString("temp", "—"));
        v.setOnClickPendingIntent(R.id.w_root, open(c, null, 11));
        for (int id : ids) m.updateAppWidget(id, v);
    }

    static void ocorrencias(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences p = prefs(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_ocorrencias);
        int n = p.getInt("o_count", -1);
        v.setTextViewText(R.id.o_meta, p.getInt("o_radius", p.getInt("radius", 25)) + " km · " + hhmm(p.getLong("o_upd", 0)));
        int[][] rows = {{R.id.o_row0, R.id.o_i0, R.id.o_t0, R.id.o_s0, R.id.o_d0}, {R.id.o_row1, R.id.o_i1, R.id.o_t1, R.id.o_s1, R.id.o_d1}};
        for (int k = 0; k < 2; k++) {
            String t = p.getString("o_" + k + "_t", null);
            if (t == null || n <= k) { v.setViewVisibility(rows[k][0], View.GONE); continue; }
            v.setViewVisibility(rows[k][0], View.VISIBLE);
            v.setTextViewText(rows[k][1], p.getString("o_" + k + "_i", "🚨"));
            v.setTextViewText(rows[k][2], t);
            v.setTextColor(rows[k][2], t.contains("URGENTE") ? 0xFFFFB4AB : 0xFFE6E1E5);
            v.setTextViewText(rows[k][3], p.getString("o_" + k + "_s", ""));
            v.setTextViewText(rows[k][4], p.getString("o_" + k + "_d", ""));
        }
        String more = n < 0 ? "A carregar ocorrências…" : n == 0 ? "Sem ocorrências no raio · Proteção Civil"
                : n > 2 ? "+ " + (n - 2) + (n - 2 == 1 ? " ocorrência" : " ocorrências") + " no raio · Proteção Civil" : "Proteção Civil";
        v.setTextViewText(R.id.o_more, more);
        v.setOnClickPendingIntent(R.id.w_root, open(c, null, 12));
        for (int id : ids) m.updateAppWidget(id, v);
    }

    static void estouBem(Context c, AppWidgetManager m, int[] ids) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_estoubem);
        v.setOnClickPendingIntent(R.id.e_ok, open(c, "estoubem", 20));
        v.setOnClickPendingIntent(R.id.e_help, open(c, "ajuda", 21));
        for (int id : ids) m.updateAppWidget(id, v);
    }

    static void ambiente(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences p = prefs(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_ambiente);
        int r = Math.max(0, Math.min(5, p.getInt("a_rcm", 0)));
        v.setTextViewText(R.id.a_rcm, r > 0 ? r + "/5" : "—");
        v.setTextColor(R.id.a_rcm, RCMC[r]);
        v.setTextViewText(R.id.a_rcmn, RCMN[r]);
        int q = p.getInt("a_aqi", -1);
        String qn = q < 0 ? "—" : q <= 20 ? "Boa" : q <= 40 ? "Razoável" : q <= 60 ? "Moderada" : q <= 80 ? "Fraca" : "Muito fraca";
        v.setTextViewText(R.id.a_aqi, qn);
        v.setTextColor(R.id.a_aqi, q < 0 ? 0xFF938F99 : q <= 40 ? 0xFF4CAF50 : q <= 60 ? 0xFFFFD54F : q <= 80 ? 0xFFFF9800 : 0xFFFF5449);
        String uv = p.getString("a_uv", "");
        int uvi = 0; try { uvi = Integer.parseInt(uv); } catch (Exception ignored) { }
        v.setTextViewText(R.id.a_uv, uv.isEmpty() ? "—" : "UV " + uv);
        v.setTextColor(R.id.a_uv, uvi >= 8 ? 0xFFFF5449 : uvi >= 6 ? 0xFFFF9800 : uvi >= 3 ? 0xFFFFD54F : 0xFF4CAF50);
        v.setOnClickPendingIntent(R.id.w_root, open(c, null, 13));
        for (int id : ids) m.updateAppWidget(id, v);
    }

    static void tempestade(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences p = prefs(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_tempestade);
        int rank = p.getInt("s_rank", -1);
        if (rank <= 0) {
            v.setTextViewText(R.id.s_name, rank < 0 ? "Tempestades" : "Sem tempestades previstas");
            v.setTextViewText(R.id.s_p, "");
            v.setViewVisibility(R.id.s_box, View.GONE);
            v.setViewVisibility(R.id.s_pts, View.GONE);
            v.setTextViewText(R.id.s_foot, rank < 0 ? "A carregar a previsão do ECMWF…" : "Nenhuma depressão bem definida a aproximar-se de Portugal nos próximos 4 dias · ECMWF");
        } else {
            v.setTextViewText(R.id.s_name, p.getString("s_name", "Depressão"));
            v.setTextViewText(R.id.s_p, p.getString("s_p", ""));
            v.setViewVisibility(R.id.s_box, View.VISIBLE);
            v.setTextViewText(R.id.s_l1, p.getString("s_l1", ""));
            v.setTextViewText(R.id.s_l2, p.getString("s_l2", ""));
            v.setTextViewText(R.id.s_l3, p.getString("s_l3", ""));
            int[] pts = {R.id.s_pt0, R.id.s_pt1, R.id.s_pt2};
            boolean any = false;
            for (int k = 0; k < 3; k++) {
                String s = p.getString("s_pt" + k, null);
                v.setViewVisibility(pts[k], s == null ? View.INVISIBLE : View.VISIBLE);
                if (s != null) { v.setTextViewText(pts[k], s); any = true; }
            }
            v.setViewVisibility(R.id.s_pts, any ? View.VISIBLE : View.GONE);
            v.setTextViewText(R.id.s_foot, "ECMWF · " + hhmm(p.getLong("s_upd", 0)) + " · Avisos oficiais: IPMA");
        }
        v.setOnClickPendingIntent(R.id.w_root, open(c, "tempestades", 14));
        for (int id : ids) m.updateAppWidget(id, v);
    }
}

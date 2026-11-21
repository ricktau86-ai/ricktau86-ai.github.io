package pt.sentinela.relogio;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** App do relógio: deslizar (ou rodar a coroa) para passar Aviso → Tempo → Ocorrências → Tempestade → Definições. */
public class MainActivity extends Activity {
    static final int CINZA = 0xFF938F99, CARTAO = 0xFF1E1E1E;
    ScrollView scroll;
    LinearLayout raiz;
    int W, H, padH;
    boolean aCarregar = false;
    final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        W = getResources().getDisplayMetrics().widthPixels;
        H = getResources().getDisplayMetrics().heightPixels;
        padH = Math.round(W * 0.12f);
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(raiz, new ViewGroup.LayoutParams(-1, -2));
        setContentView(scroll);
        agendar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        desenhar();
        scroll.requestFocus(); // para a coroa rodar o ecrã
        SharedPreferences p = Dados.prefs(this);
        if (System.currentTimeMillis() - p.getLong("upd", 0) > 5 * 60000L) atualizar();
    }

    void agendar() {
        Constraints c = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest r = new PeriodicWorkRequest.Builder(Atualizador.class, 30, TimeUnit.MINUTES).setConstraints(c).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sentinela_relogio", ExistingPeriodicWorkPolicy.KEEP, r);
    }

    void atualizar() {
        if (aCarregar) return;
        aCarregar = true;
        desenhar();
        new Thread(() -> {
            Dados.atualizar(getApplicationContext());
            ui.post(() -> { aCarregar = false; desenhar(); });
        }).start();
    }

    /* ---------- peças ---------- */
    int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics())); }

    TextView tv(String s, float sp, int cor, boolean neg) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(cor);
        t.setGravity(Gravity.CENTER);
        if (neg) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    TextView cab(String s) {
        TextView t = tv(s.toUpperCase(Dados.PT), 12, CINZA, false);
        t.setLetterSpacing(0.06f);
        return t;
    }

    LinearLayout seccao(boolean ecraInteiro) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        l.setPadding(padH, Math.round(H * 0.13f), padH, Math.round(H * 0.10f));
        if (ecraInteiro) l.setMinimumHeight(H);
        raiz.addView(l, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    GradientDrawable fundo(int cor, float raio) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(raio));
        return g;
    }

    View espaco(int dpH) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dpH)));
        return v;
    }

    LinearLayout cartao() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(fundo(CARTAO, 16));
        c.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        c.setLayoutParams(lp);
        return c;
    }

    TextView botao(String s, int fundoCor, int textoCor, View.OnClickListener acao) {
        TextView t = tv(s, 15, textoCor, true);
        t.setBackground(fundo(fundoCor, 22));
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(acao);
        return t;
    }

    /* ---------- ecrãs ---------- */
    void desenhar() {
        int y = scroll.getScrollY();
        raiz.removeAllViews();
        SharedPreferences p = Dados.prefs(this);
        if (p.getLong("upd", 0) == 0 && aCarregar) {
            LinearLayout s = seccao(true);
            s.addView(cab("Sentinela"));
            s.addView(espaco(10));
            s.addView(tv("A carregar…", 16, Color.WHITE, false));
            return;
        }
        aviso(p);
        tempo(p);
        ocorrencias(p);
        if (p.getInt("s_rank", 0) > 0) tempestade(p);
        definicoes(p);
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    void aviso(SharedPreferences p) {
        LinearLayout s = seccao(true);
        int lvl = p.getInt("lvl", 0), cor = Dados.corDe(lvl);
        s.addView(cab("Sentinela · " + p.getString("areaName", "Leiria")));
        s.addView(espaco(10));
        TextView c = tv(p.getString("a_ico", "✓"), 30, lvl == 0 ? Color.WHITE : Color.BLACK, true);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(cor);
        g.setStroke(dp(6), (cor & 0x00FFFFFF) | 0x40000000);
        c.setBackground(g);
        int d = Math.round(W * 0.26f);
        s.addView(c, new LinearLayout.LayoutParams(d, d));
        s.addView(espaco(8));
        s.addView(tv(p.getString("a_tipo", "Sem dados"), 19, lvl == 0 ? Color.WHITE : cor, true));
        s.addView(tv(p.getString("a_sub", "Toque em Atualizar"), 14, CINZA, false));
        long u = p.getLong("a_upd", 0);
        s.addView(espaco(6));
        s.addView(tv(aCarregar ? "A atualizar…" : u > 0 ? "IPMA · " + Dados.hora(u) : "IPMA", 12, Dados.recente(u) ? CINZA : 0xFFFFB4AB, false));
        String lista = p.getString("a_lista", "");
        if (p.getInt("a_n", 0) > 1) {
            LinearLayout s2 = seccao(false);
            s2.addView(cab("Todos os avisos"));
            for (String l : lista.split("\n")) {
                String[] f = l.split("\\|");
                if (f.length < 3) continue;
                int n = Integer.parseInt(f[0]);
                LinearLayout k = cartao();
                TextView t = tv(Dados.iconeAviso(f[1]) + " " + f[1] + " · " + Dados.LVN[n], 14, Dados.corDe(n), true);
                t.setGravity(Gravity.START);
                TextView q = tv(f[2], 12, CINZA, false);
                q.setGravity(Gravity.START);
                k.addView(t);
                k.addView(q);
                s2.addView(k);
            }
        }
    }

    void tempo(SharedPreferences p) {
        LinearLayout s = seccao(true);
        s.addView(cab("Tempo"));
        s.addView(tv(p.getString("t_temp", "--") + " " + p.getString("t_ico", ""), 46, Color.WHITE, false));
        s.addView(tv(p.getString("t_desc", ""), 15, Color.WHITE, false));
        s.addView(tv(p.getString("t_minmax", ""), 13, CINZA, false));
        s.addView(espaco(6));
        s.addView(tv(p.getString("t_rain", ""), 14, 0xFF4FC3F7, false));
        long g = p.getLong("t_gustv", 0);
        s.addView(tv(p.getString("t_gust", ""), 14, g >= 70 ? 0xFFFF9800 : g >= 50 ? 0xFFFFEB3B : Color.WHITE, false));
        String horas = p.getString("t_horas", "");
        if (!horas.isEmpty()) {
            LinearLayout s2 = seccao(false);
            s2.addView(cab("Próximas horas"));
            for (String l : horas.split("\n")) {
                String[] f = l.split("\\|");
                if (f.length < 4) continue;
                LinearLayout r = new LinearLayout(this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setPadding(0, dp(5), 0, dp(5));
                r.addView(tv(f[0], 15, CINZA, false), new LinearLayout.LayoutParams(0, -2, 1));
                r.addView(tv(f[1], 17, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1));
                r.addView(tv(f[2], 15, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
                r.addView(tv(f[3], 15, 0xFF4FC3F7, false), new LinearLayout.LayoutParams(0, -2, 1));
                s2.addView(r);
            }
            s2.addView(tv("Open-Meteo", 11, CINZA, false));
        }
    }

    void ocorrencias(SharedPreferences p) {
        LinearLayout s = seccao(true);
        int n = p.getInt("o_count", 0);
        s.addView(cab("Ocorrências · " + p.getInt("radius", 25) + " km"));
        String lista = p.getString("o_lista", "");
        if (p.getLong("o_upd", 0) == 0) {
            s.addView(espaco(10));
            s.addView(tv("Sem dados ainda", 15, CINZA, false));
            return;
        }
        if (n == 0) {
            s.addView(espaco(10));
            s.addView(tv("✓", 30, 0xFF4CAF50, true));
            s.addView(tv("Nada num raio de " + p.getInt("radius", 25) + " km", 15, Color.WHITE, false));
        }
        for (String l : lista.split("\n")) {
            String[] f = l.split("\\|");
            if (f.length < 5) continue;
            LinearLayout k = cartao();
            LinearLayout linha = new LinearLayout(this);
            linha.setOrientation(LinearLayout.HORIZONTAL);
            linha.setGravity(Gravity.CENTER_VERTICAL);
            linha.addView(tv(f[0], 18, Color.WHITE, false));
            TextView t = tv(" " + f[1], 14, Color.WHITE, true);
            t.setGravity(Gravity.START);
            t.setMaxLines(2);
            linha.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            linha.addView(tv(f[3], 13, Color.WHITE, true));
            k.addView(linha);
            if ("1".equals(f[4])) {
                TextView u = tv("URGENTE", 10, 0xFFFFB4AB, true);
                u.setBackground(fundo(0xFF5C1612, 8));
                u.setPadding(dp(6), dp(1), dp(6), dp(1));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.gravity = Gravity.START;
                k.addView(u, lp);
            }
            TextView sub = tv(f[2], 12, CINZA, false);
            sub.setGravity(Gravity.START);
            k.addView(sub);
            s.addView(k);
        }
        if (n > 5) s.addView(tv("+" + (n - 5) + " mais longe", 12, CINZA, false));
        s.addView(espaco(4));
        s.addView(tv("Proteção Civil · " + Dados.hora(p.getLong("o_upd", 0)), 11, CINZA, false));
    }

    void tempestade(SharedPreferences p) {
        LinearLayout s = seccao(true);
        s.addView(cab("Tempestade"));
        s.addView(tv(p.getString("s_nome", "Depressão"), 18, Color.WHITE, true));
        TextView pr = tv(p.getString("s_p", ""), 12, 0xFFFFB4AB, true);
        pr.setBackground(fundo(0xFF5C1612, 8));
        pr.setPadding(dp(8), dp(2), dp(8), dp(2));
        s.addView(pr, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout k = cartao();
        k.setBackground(fundo(0xFF3B3610, 18));
        k.addView(tv(p.getString("s_l1", ""), 14, 0xFFFFF8C4, true));
        k.addView(tv(p.getString("s_l2", ""), 14, 0xFFFFF8C4, false));
        k.addView(tv(p.getString("s_l3", ""), 11, 0xFFF1E7A2, false));
        s.addView(k);
        s.addView(espaco(6));
        s.addView(tv("Previsão ECMWF · avisos oficiais: IPMA", 11, CINZA, false));
    }

    void definicoes(SharedPreferences p) {
        LinearLayout s = seccao(true);
        s.addView(cab("Definições"));
        s.addView(botao("📍 " + p.getString("areaName", "Leiria"), CARTAO, Color.WHITE,
                v -> startActivity(new Intent(this, DistritosActivity.class))));
        int r = p.getInt("radius", 25);
        s.addView(botao("Raio: " + r + " km", CARTAO, Color.WHITE, v -> {
            int nr = r == 10 ? 25 : r == 25 ? 50 : 10;
            p.edit().putInt("radius", nr).putLong("upd", 0).apply();
            atualizar();
        }));
        s.addView(botao(aCarregar ? "A atualizar…" : "↻ Atualizar agora", 0xFFFFEB3B, 0xFF1A1700, v -> atualizar()));
        long u = p.getLong("upd", 0);
        s.addView(espaco(6));
        s.addView(tv(u > 0 ? "Atualizado " + Dados.hora(u) + " · sozinho de 30 em 30 min" : "Ainda sem dados", 11, CINZA, false));
    }
}

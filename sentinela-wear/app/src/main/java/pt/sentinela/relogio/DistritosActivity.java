package pt.sentinela.relogio;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Escolher o distrito (zona de avisos do IPMA) e o ponto usado para o tempo e as ocorrências. */
public class DistritosActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int W = getResources().getDisplayMetrics().widthPixels, H = getResources().getDisplayMetrics().heightPixels;
        float dp = getResources().getDisplayMetrics().density;
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.BLACK);
        sv.setFocusable(true);
        sv.setFocusableInTouchMode(true);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int ph = Math.round(W * 0.14f);
        l.setPadding(ph, Math.round(H * 0.18f), ph, Math.round(H * 0.25f));
        TextView t = new TextView(this);
        t.setText("ESCOLHE O DISTRITO");
        t.setTextColor(0xFF938F99);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        String atual = Dados.prefs(this).getString("area", "LRA");
        for (String[] d : Dados.DISTRITOS) {
            TextView o = new TextView(this);
            o.setText(d[1]);
            o.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            boolean sel = d[0].equals(atual);
            o.setTextColor(sel ? 0xFF1A1700 : Color.WHITE);
            o.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setColor(sel ? 0xFFFFEB3B : 0xFF1E1E1E);
            g.setCornerRadius(20 * dp);
            o.setBackground(g);
            int p = Math.round(10 * dp);
            o.setPadding(p, p, p, p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Math.round(6 * dp);
            o.setOnClickListener(v -> {
                Dados.prefs(this).edit().putString("area", d[0]).putString("areaName", d[1])
                        .putString("lat", d[2]).putString("lon", d[3]).putLong("upd", 0).apply();
                finish();
            });
            l.addView(o, lp);
        }
        sv.addView(l, new ViewGroup.LayoutParams(-1, -2));
        setContentView(sv);
        sv.requestFocus();
    }
}

package pt.sentinela.relogio;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;

/** Mosaico: chuva nas próximas 2 h, de 15 em 15 minutos. */
public class ChuvaTile extends TileService {
    static final int AZ = 0xFF4FC3F7, CINZA = 0xFF938F99;

    LayoutElementBuilders.LayoutElement layout() {
        SharedPreferences p = Dados.prefs(this);
        List<String> h = new ArrayList<>();
        List<Double> v = new ArrayList<>();
        for (String l : p.getString("t_q15", "").split("\n")) {
            String[] f = l.split("\\|");
            if (f.length < 2) continue;
            h.add(f[0]);
            try { v.add(Double.parseDouble(f[1])); } catch (Exception e) { v.add(0.0); }
        }
        LayoutElementBuilders.Column.Builder col = new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER);
        col.addContent(SentinelaTile.texto("CHUVA · 2 H", 11, CINZA, false, 1));
        col.addContent(SentinelaTile.esp(4));
        if (v.isEmpty()) {
            col.addContent(SentinelaTile.texto("Abra a app para carregar", 14, 0xFFFFFFFF, false, 2));
            return col.build();
        }
        int ini = -1, fim = -1;
        double tot = 0, max = 0;
        for (int i = 0; i < v.size(); i++) {
            if (v.get(i) >= 0.1) { if (ini < 0) ini = i; fim = i; }
            tot += v.get(i);
            max = Math.max(max, v.get(i));
        }
        String frase = ini < 0 ? "Sem chuva em 2 h" : ini == 0 ? (fim >= v.size() - 1 ? "Chuva nas próximas 2 h" : "Para por volta das " + h.get(Math.min(fim + 1, v.size() - 1))) : "Chove por volta das " + h.get(ini);
        col.addContent(SentinelaTile.texto(frase, 15, ini < 0 ? 0xFFFFFFFF : AZ, true, 2));
        col.addContent(SentinelaTile.esp(8));
        LayoutElementBuilders.Row.Builder row = new LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM);
        double esc = Math.max(1.6, max);
        for (int i = 0; i < v.size(); i++) {
            double x = v.get(i);
            if (i > 0) row.addContent(TileUtil.larg(3));
            row.addContent(TileUtil.barra(17, (float) (x / esc * 46), x >= 0.5 ? AZ : x >= 0.1 ? 0x9981D4FA : 0xFF3A3A3A));
        }
        col.addContent(row.build());
        col.addContent(SentinelaTile.esp(3));
        col.addContent(SentinelaTile.texto(h.get(0) + "  →  " + h.get(h.size() - 1), 10, CINZA, false, 1));
        col.addContent(SentinelaTile.esp(4));
        col.addContent(SentinelaTile.texto(ini < 0 ? "previsão Open-Meteo" : String.format(Dados.PT, "%.1f mm em 2 h", tot), 11, CINZA, false, 1));
        return col.build();
    }

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(@NonNull RequestBuilders.TileRequest req) {
        return TileUtil.tile(this, layout());
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(@NonNull RequestBuilders.ResourcesRequest req) {
        return TileUtil.recursos();
    }
}

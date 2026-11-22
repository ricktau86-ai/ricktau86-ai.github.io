package pt.sentinela.relogio;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;
import com.google.common.util.concurrent.ListenableFuture;

/** Mosaico: próximas 5 horas (hora, ícone, temperatura, probabilidade de chuva). */
public class HorasTile extends TileService {
    static final int AZ = 0xFF4FC3F7, CINZA = 0xFF938F99;

    LayoutElementBuilders.LayoutElement layout() {
        SharedPreferences p = Dados.prefs(this);
        LayoutElementBuilders.Column.Builder col = new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER);
        col.addContent(SentinelaTile.texto("HORA A HORA", 11, CINZA, false, 1));
        col.addContent(SentinelaTile.esp(6));
        String s = p.getString("t_h1", "");
        if (s.isEmpty()) {
            col.addContent(SentinelaTile.texto("Abra a app para carregar", 14, 0xFFFFFFFF, false, 2));
            return col.build();
        }
        LayoutElementBuilders.Row.Builder row = new LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP);
        boolean primeiro = true;
        for (String l : s.split("\n")) {
            String[] f = l.split("\\|");
            if (f.length < 4) continue;
            int pr = 0;
            try { pr = Integer.parseInt(f[3].replace("%", "").trim()); } catch (Exception ignored) { }
            if (!primeiro) row.addContent(TileUtil.larg(4));
            primeiro = false;
            LayoutElementBuilders.Column.Builder c = new LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .setWidth(androidx.wear.protolayout.DimensionBuilders.dp(30));
            c.addContent(SentinelaTile.texto(f[0], 11, CINZA, false, 1));
            c.addContent(SentinelaTile.texto(f[1], 16, 0xFFFFFFFF, false, 1));
            c.addContent(SentinelaTile.texto(f[2], 13, 0xFFFFFFFF, true, 1));
            c.addContent(SentinelaTile.esp(3));
            c.addContent(new LayoutElementBuilders.Box.Builder()
                    .setHeight(androidx.wear.protolayout.DimensionBuilders.dp(24))
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                    .addContent(TileUtil.barra(7, 24, 0xFF3A3A3A))
                    .addContent(TileUtil.barra(7, Math.max(2, 24f * pr / 100f), AZ))
                    .build());
            c.addContent(SentinelaTile.texto(pr + "%", 10, AZ, false, 1));
            row.addContent(c.build());
        }
        col.addContent(row.build());
        String g = p.getString("t_h1_gust", "");
        if (!g.isEmpty()) {
            col.addContent(SentinelaTile.esp(4));
            col.addContent(SentinelaTile.texto(g, 11, CINZA, false, 1));
        }
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

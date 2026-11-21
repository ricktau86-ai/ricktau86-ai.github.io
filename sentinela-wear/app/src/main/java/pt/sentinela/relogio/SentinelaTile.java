package pt.sentinela.relogio;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Mosaico: aviso, tempo e ocorrência mais próxima de relance. Tocar abre a app. */
public class SentinelaTile extends TileService {
    static final String RES = "1";

    static LayoutElementBuilders.Text texto(String s, float sp, int cor, boolean neg, int linhas) {
        LayoutElementBuilders.FontStyle.Builder f = new LayoutElementBuilders.FontStyle.Builder()
                .setSize(DimensionBuilders.sp(sp))
                .setColor(ColorBuilders.argb(cor));
        if (neg) f.setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD);
        return new LayoutElementBuilders.Text.Builder()
                .setText(s)
                .setFontStyle(f.build())
                .setMaxLines(linhas)
                .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                .build();
    }

    static LayoutElementBuilders.Spacer esp(float dp) {
        return new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(dp)).build();
    }

    LayoutElementBuilders.LayoutElement layout() {
        SharedPreferences p = Dados.prefs(this);
        int lvl = p.getInt("lvl", 0);
        int cor = lvl == 0 ? 0xFFFFFFFF : Dados.corDe(lvl);
        LayoutElementBuilders.Column.Builder col = new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setWidth(DimensionBuilders.expand());
        col.addContent(texto("SENTINELA · " + p.getString("areaName", "Leiria").toUpperCase(Dados.PT), 11, 0xFF938F99, false, 1));
        col.addContent(esp(4));
        col.addContent(texto(p.getString("a_tipo", "Abra a app para carregar"), 17, cor, true, 2));
        String sub = p.getString("a_sub", "");
        if (!sub.isEmpty()) col.addContent(texto(sub, 12, 0xFF938F99, false, 1));
        col.addContent(esp(8));
        String tempo = p.getString("t_temp", "--") + " " + p.getString("t_ico", "");
        String chuva = p.getString("t_rain_curto", "");
        col.addContent(texto(chuva.isEmpty() ? tempo + " · " + p.getString("t_desc", "") : tempo + "   🌧 " + chuva, 14, 0xFFFFFFFF, false, 1));
        int sr = p.getInt("s_rank", 0);
        String linha;
        int corLinha = 0xFFFFFFFF;
        if (sr > 0) {
            linha = "🌀 " + p.getString("s_nome", "Depressão") + " · " + p.getString("s_curto", "");
            corLinha = 0xFFFFF8C4;
        } else {
            String o = p.getString("o_lista", "");
            if (!o.isEmpty()) {
                String[] f = o.split("\n")[0].split("\\|");
                linha = f.length >= 4 ? f[0] + " " + f[1] + " · " + f[3] : "";
            } else if (p.getLong("o_upd", 0) > 0) {
                linha = "✓ Sem ocorrências a " + p.getInt("radius", 25) + " km";
            } else linha = "";
        }
        if (!linha.isEmpty()) {
            col.addContent(esp(8));
            col.addContent(new LayoutElementBuilders.Box.Builder()
                    .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                            .setBackground(new ModifiersBuilders.Background.Builder()
                                    .setColor(ColorBuilders.argb(sr > 0 ? 0xFF3B3610 : 0xFF1E1E1E))
                                    .setCorner(new ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(14)).build())
                                    .build())
                            .setPadding(new ModifiersBuilders.Padding.Builder()
                                    .setStart(DimensionBuilders.dp(10)).setEnd(DimensionBuilders.dp(10))
                                    .setTop(DimensionBuilders.dp(5)).setBottom(DimensionBuilders.dp(5)).build())
                            .build())
                    .addContent(texto(linha, 12, corLinha, false, 2))
                    .build());
        }
        long u = p.getLong("upd", 0);
        col.addContent(esp(6));
        col.addContent(texto(u > 0 ? "atualizado " + Dados.hora(u) : "", 10, 0xFF938F99, false, 1));

        ModifiersBuilders.Clickable abrir = new ModifiersBuilders.Clickable.Builder()
                .setId("abrir")
                .setOnClick(new ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(new ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(getPackageName())
                                .setClassName(MainActivity.class.getName())
                                .build())
                        .build())
                .build();
        return new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setClickable(abrir)
                        .setPadding(new ModifiersBuilders.Padding.Builder()
                                .setStart(DimensionBuilders.dp(22)).setEnd(DimensionBuilders.dp(22)).build())
                        .build())
                .addContent(col.build())
                .build();
    }

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(@NonNull RequestBuilders.TileRequest req) {
        // se os dados forem antigos, pede uma atualização em segundo plano (o mosaico refresca quando chegar)
        if (System.currentTimeMillis() - Dados.prefs(this).getLong("upd", 0) > 30 * 60000L) {
            new Thread(() -> Dados.atualizar(getApplicationContext())).start();
        }
        TileBuilders.Tile t = new TileBuilders.Tile.Builder()
                .setResourcesVersion(RES)
                .setFreshnessIntervalMillis(30 * 60000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout()))
                .build();
        return Futures.immediateFuture(t);
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(@NonNull RequestBuilders.ResourcesRequest req) {
        return Futures.immediateFuture(new ResourceBuilders.Resources.Builder().setVersion(RES).build());
    }
}

package pt.sentinela.relogio;

import android.content.Context;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.TileBuilders;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Peças comuns aos mosaicos. */
class TileUtil {
    static LayoutElementBuilders.Box barra(float largura, float altura, int cor) {
        return new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(largura))
                .setHeight(DimensionBuilders.dp(Math.max(2, altura)))
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setBackground(new ModifiersBuilders.Background.Builder()
                                .setColor(ColorBuilders.argb(cor))
                                .setCorner(new ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(Math.min(3, largura / 2))).build())
                                .build())
                        .build())
                .build();
    }

    static LayoutElementBuilders.Spacer larg(float dp) {
        return new LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(dp)).build();
    }

    static LayoutElementBuilders.LayoutElement raiz(Context c, LayoutElementBuilders.LayoutElement conteudo) {
        ModifiersBuilders.Clickable abrir = new ModifiersBuilders.Clickable.Builder()
                .setId("abrir")
                .setOnClick(new ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(new ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(c.getPackageName())
                                .setClassName(MainActivity.class.getName())
                                .build())
                        .build())
                .build();
        return new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(new ModifiersBuilders.Modifiers.Builder().setClickable(abrir).build())
                .addContent(conteudo)
                .build();
    }

    static ListenableFuture<TileBuilders.Tile> tile(Context c, LayoutElementBuilders.LayoutElement conteudo) {
        if (System.currentTimeMillis() - Dados.prefs(c).getLong("upd", 0) > 30 * 60000L) {
            new Thread(() -> Dados.atualizar(c.getApplicationContext())).start();
        }
        return Futures.immediateFuture(new TileBuilders.Tile.Builder()
                .setResourcesVersion(SentinelaTile.RES)
                .setFreshnessIntervalMillis(15 * 60000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(raiz(c, conteudo)))
                .build());
    }

    static ListenableFuture<ResourceBuilders.Resources> recursos() {
        return Futures.immediateFuture(new ResourceBuilders.Resources.Builder().setVersion(SentinelaTile.RES).build());
    }
}

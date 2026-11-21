package pt.sentinela.alertas;

import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Recebe da app o local escolhido e pede ao widget/verificação em segundo plano para atualizar. */
@CapacitorPlugin(name = "SentinelaWidget")
public class WidgetPlugin extends Plugin {
    @PluginMethod
    public void save(PluginCall call) {
        Context c = getContext();
        SharedPreferences.Editor e = c.getSharedPreferences(SentinelaWidget.PREFS, Context.MODE_PRIVATE).edit();
        e.putString("lat", String.valueOf(call.getDouble("lat", 39.7436)));
        e.putString("lon", String.valueOf(call.getDouble("lon", -8.8071)));
        e.putString("name", call.getString("name", "Leiria"));
        e.putString("area", call.getString("area", "LRA"));
        e.putString("areaName", call.getString("areaName", "Leiria"));
        Integer rad = call.getInt("radius", 25);
        e.putInt("radius", rad == null ? 25 : Math.max(5, Math.min(300, rad)));
        e.putBoolean("notify", Boolean.TRUE.equals(call.getBoolean("notify", true)));
        e.apply();
        SentinelaWidget.schedule(c);
        SentinelaWidget.refreshNow(c);
        call.resolve();
    }

    /** Ação pendente vinda de um widget ou atalho (ex.: "estoubem"), lida pela app ao arrancar. */
    @PluginMethod
    public void consumeAction(PluginCall call) {
        com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
        r.put("acao", MainActivity.pendingAction == null ? "" : MainActivity.pendingAction);
        MainActivity.pendingAction = null;
        call.resolve(r);
    }
}

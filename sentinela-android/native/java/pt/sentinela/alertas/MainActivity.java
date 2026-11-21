package pt.sentinela.alertas;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    /** Ação pedida por um widget ou atalho do ícone (estoubem, ajuda, socorros, tempestades). */
    static volatile String pendingAction = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(WidgetPlugin.class);
        super.onCreate(savedInstanceState);
        take(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        take(intent);
        if (pendingAction != null && getBridge() != null) {
            String a = pendingAction.replaceAll("[^a-z]", "");
            pendingAction = null;
            getBridge().triggerWindowJSEvent("sentinelaAcao", "{\"acao\":\"" + a + "\"}");
        }
    }

    private void take(Intent i) {
        if (i != null && i.hasExtra("acao")) {
            pendingAction = i.getStringExtra("acao");
            i.removeExtra("acao");
        }
    }
}

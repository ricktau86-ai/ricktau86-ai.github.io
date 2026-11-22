package pt.sentinela.alertas;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

public class ChuvaWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Previsao.update(c, m, ids, Previsao.CHUVA);
        SentinelaWidget.schedule(c);
        SentinelaWidget.refreshNow(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        Previsao.update(c, m, new int[]{id}, Previsao.CHUVA);
    }

    @Override
    public void onEnabled(Context c) {
        SentinelaWidget.schedule(c);
        SentinelaWidget.refreshNow(c);
    }
}

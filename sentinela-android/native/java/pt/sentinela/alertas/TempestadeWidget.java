package pt.sentinela.alertas;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class TempestadeWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Widgets.tempestade(c, m, ids);
        SentinelaWidget.schedule(c);
        SentinelaWidget.refreshNow(c);
    }

    @Override
    public void onEnabled(Context c) {
        SentinelaWidget.schedule(c);
        SentinelaWidget.refreshNow(c);
    }
}

package pt.sentinela.relogio;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationText;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;

/** Complicação para o mostrador: temperatura + nível do aviso (curta) ou aviso completo (longa). */
public class SentinelaComplicacao extends ComplicationDataSourceService {
    static final String[] CURTO = {"OK", "AMA", "LAR", "VERM"};

    static ComplicationText txt(String s) { return new PlainComplicationText.Builder(s).build(); }

    PendingIntent abrir() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    MonochromaticImage icone() {
        return new MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_compl)).build();
    }

    ComplicationData dados(ComplicationType tipo, int lvl, String temp, String avisoTipo, String avisoSub) {
        String desc = lvl == 0 ? "Sem avisos, " + temp : avisoTipo + ", " + temp;
        if (tipo == ComplicationType.LONG_TEXT) {
            String texto = lvl == 0 ? "Sem avisos · " + temp : avisoTipo + " · " + temp;
            return new LongTextComplicationData.Builder(txt(texto), txt(desc))
                    .setTitle(txt(lvl == 0 ? "Sentinela" : avisoSub))
                    .setMonochromaticImage(icone())
                    .setTapAction(abrir()).build();
        }
        return new ShortTextComplicationData.Builder(txt(temp), txt(desc))
                .setTitle(txt(CURTO[Math.max(0, Math.min(3, lvl))]))
                .setMonochromaticImage(icone())
                .setTapAction(abrir()).build();
    }

    @Override
    public void onComplicationRequest(@NonNull ComplicationRequest request, @NonNull ComplicationRequestListener listener) {
        SharedPreferences p = Dados.prefs(this);
        ComplicationData d = dados(request.getComplicationType(), p.getInt("lvl", 0), p.getString("t_temp", "--"),
                p.getString("a_tipo", "Sem avisos"), p.getString("a_sub", ""));
        try { listener.onComplicationData(d); } catch (RemoteException ignored) { }
    }

    @Nullable
    @Override
    public ComplicationData getPreviewData(@NonNull ComplicationType type) {
        if (type != ComplicationType.SHORT_TEXT && type != ComplicationType.LONG_TEXT) return null;
        return dados(type, 2, "14°", "Vento · Laranja", "até sáb 21:00");
    }
}

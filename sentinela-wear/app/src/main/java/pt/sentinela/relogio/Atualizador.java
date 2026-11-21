package pt.sentinela.relogio;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Corre de 30 em 30 minutos com a app fechada: atualiza os dados, o mosaico e a complicação. */
public class Atualizador extends Worker {
    public Atualizador(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull
    @Override
    public Result doWork() {
        Dados.atualizar(getApplicationContext());
        return Result.success();
    }
}

# Como gerar o .apk da App Nonô

Este repositório já tem um projeto **Capacitor** pronto na pasta `android/`.
Não é preciso saber programar Android — só precisas de instalar o **Android
Studio** (gratuito, único requisito) e carregar num botão.

## Passo a passo

1. **Instala o Android Studio**: https://developer.android.com/studio
   (na primeira abertura ele próprio instala o Android SDK — precisa de
   internet normal, sem restrições, ao contrário do ambiente onde isto foi
   preparado).

2. **Clona/faz pull deste repositório** para o teu computador:
   ```
   git clone https://github.com/ricktau86-ai/ricktau86-ai.github.io.git
   cd ricktau86-ai.github.io
   git checkout claude/repository-verification-us4w1s
   ```

3. **Abre a pasta `android/`** no Android Studio (`File → Open` → escolhe a
   pasta `android`, não a pasta raiz do repo).

4. Espera o Gradle sincronizar sozinho (primeira vez demora alguns minutos —
   está a descarregar as dependências do Android).

5. Para testar rápido no telemóvel (via USB, com "Depuração USB" ativada nas
   Opções de Programador do Android):
   `Run → Run 'app'`

6. Para gerar o `.apk` para instalar no teu telemóvel e no da Tânia:
   `Build → Build App Bundle(s) / APK(s) → Build APK(s)`
   O ficheiro fica em `android/app/build/outputs/apk/debug/app-debug.apk`.
   Envia esse ficheiro por WhatsApp/Drive para os dois telemóveis e instala
   (o Android vai pedir para autorizar "instalar de fontes desconhecidas" —
   normal, por não vir da Play Store).

   Nota: isto gera um APK de **debug**, assinado com uma chave automática de
   testes — suficiente para instalar nos vossos telemóveis. Se um dia
   quiseres publicar na Play Store, é preciso gerar uma versão **release**
   assinada com uma chave própria (`Build → Generate Signed App Bundle/APK`).

## Se alterares o código da app (index.html, etc.)

O projeto Android usa uma cópia dos ficheiros dentro de `www/`. Sempre que
mexeres em `index.html`, `manifest.json`, `sw.js` ou nos ícones na raiz do
repo, corre antes de recompilar:

```
./sync-www.sh
```

(isto copia os ficheiros atualizados para `www/` e sincroniza com `android/`)

## O que funciona offline e o que não funciona (resumo da auditoria)

- **Funciona sempre offline, mesmo sem nunca teres feito login**: Caneta
  (calculadora manual), Regra de 3, lista de 209 alimentos, Jogo/Quiz.
- **Funciona offline depois de já teres feito login online pelo menos uma
  vez**: sincronização de dados da família (plano de tratamento, despensa,
  calendário, crescimento) — ficam em cache local do Firestore.
- **Nunca funciona sem internet**: o assistente de IA "online" (GPT-4o).
- **Risco a testar nos telemóveis**: o assistente "IA Local" (dentro do
  Modo Offline) usa um modelo de IA (Phi-3.5) via WebGPU — precisa de
  descarregar ~1-2GB na primeira vez (com internet) e depende do WebView
  do Android suportar WebGPU. Pode não funcionar em todos os telemóveis;
  testa depois de instalado.

## Aviso de segurança a resolver

A password da conta Firebase fica gravada no telemóvel cifrada com um XOR
simples cuja chave está escrita no próprio código-fonte (público no GitHub).
Isto não impede a app de funcionar, mas convém corrigir antes de dependeres
disto no dia a dia — ver detalhes no relatório da conversa.

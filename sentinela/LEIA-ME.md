# Sentinela — avisos e emergência (PT)

App web gratuita, sem contas e sem publicidade. Instala-se no Android como uma app normal (PWA).

## O que tem
- **Início**: estado dos avisos IPMA do teu distrito, tempo agora, qualidade do ar, risco de incêndio (hoje/amanhã), ocorrências da Proteção Civil perto de ti e sismos recentes.
- **Avisos**: todos os avisos meteorológicos do IPMA (o teu distrito ou o país), filtros por nível, histórico guardado no telemóvel.
- **Mapa**: ocorrências ANEPC, sismos dos últimos 30 dias, radar de chuva, risco de incêndio por concelho, hospitais/bombeiros/polícia/pontos de encontro (OpenStreetMap).
- **Guias**: mochila de emergência (com lista para ir marcando), sismo, tsunami, incêndio rural, cheias, tempestade, calor, frio, abrigo em casa, números úteis.
- **Notificações**: avisos do distrito (nível mínimo à escolha), ocorrências no raio, sismos M3+.

## Fontes de dados (todas públicas e gratuitas)
- IPMA — api.ipma.pt (avisos, sismos, risco de incêndio)
- fogos.pt — ocorrências publicadas pela ANEPC
- Open-Meteo — tempo e qualidade do ar (modelo CAMS)
- RainViewer — radar
- OpenStreetMap — mapa, pesquisa e pontos úteis

## Como pôr a funcionar (GitHub Pages, grátis)
1. Cria um repositório novo no GitHub (ex.: `sentinela`), público.
2. Carrega todos os ficheiros desta pasta (index.html, sw.js, manifest.webmanifest, pastas `icons` e `vendor`).
3. Settings → Pages → Source: "Deploy from a branch" → branch `main`, pasta `/ (root)` → Save.
4. Ao fim de 1–2 minutos fica em `https://<o-teu-utilizador>.github.io/sentinela/`.

Também funciona em qualquer servidor com HTTPS (ex.: o VPS). Tem de ser HTTPS para a localização, as notificações e a instalação funcionarem.

## Instalar no Android
Abre o endereço no Chrome → menu ⋮ → "Instalar app" (ou "Adicionar ao ecrã principal").

Se quiseres um APK a sério: mete o endereço em https://www.pwabuilder.com e gera o pacote Android.

## Limitações (sem rodeios)
- **Notificações com a app fechada**: o Android pode suspender apps web. Não substitui os alertas oficiais por SMS/cell broadcast. Para alertas garantidos em segundo plano é preciso um pequeno servidor a enviar push (ou Telegram).
- **Bloqueios do browser (CORS)**: se alguma fonte recusar pedidos diretos do browser, o cartão respetivo mostra erro. Nesse caso, preenche o campo "Proxy" nas Definições com um proxy teu.
- A área de avisos é escolhida pela sede de distrito mais próxima; perto de fronteiras entre distritos, confirma ou fixa a área nas Definições.
- Não é um serviço oficial. Em emergência: **112**.

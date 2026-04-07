# 🦄 App Nonô — Gestão de Diabetes da Leonor

App familiar para gestão de diabetes Tipo 1 (Tandem t:slim X2 + Dexcom G6).

## Funcionalidades
- 💉 Calculadora de bólus com plano de tratamento
- 🍽️ Regra de 3 para contagem de hidratos
- 🧃 Despensa de consumíveis com alertas de stock
- 🧬 Medicina (Plano de tratamento + Cetonemias)
- 📅 Calendário de consultas + estado da bomba
- 📏 Crescimento (Peso/Altura/IMC com gráficos)
- 🎮 Quiz educativo sobre diabetes
- 🤖 Assistente IA (OpenAI GPT-4o)
- 📵 Modo Offline com 209 alimentos
- 🔔 Lembretes com repetição (Ferro, Cateter, Sensor...)

## PWA — Instalar como App
Acede a `https://[teu-username].github.io/appnono/` e:
- **Android**: Menu Chrome → "Adicionar ao ecrã inicial"
- **iOS**: Safari → Partilhar → "Adicionar ao ecrã de início"

## Estrutura de ficheiros
```
index.html    — App principal
sw.js         — Service Worker (cache offline)
manifest.json — PWA manifest
icon-192.png  — Ícone 192x192 (substituir pela imagem do unicórnio)
icon-512.png  — Ícone 512x512 (substituir pela imagem do unicórnio)
```

## Configuração Firebase
As credenciais Firebase já estão no código (`app-nono`).

## Stack
- HTML/CSS/JS puro (sem frameworks)
- Firebase Auth + Firestore
- Chart.js (inlined)
- Google Fonts (Nunito + Fredoka One)
- Service Worker PWA

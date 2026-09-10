#!/usr/bin/env bash
# Copia os ficheiros da app (raiz do repo, usados pelo GitHub Pages) para dentro
# do projeto Capacitor (www/), para depois seres sincronizados para o projeto Android.
#
# Corre isto sempre que alterares index.html, manifest.json, sw.js ou os ícones,
# antes de recompilar o APK no Android Studio (ou corre `npx cap sync android`
# a seguir, que já chama isto por baixo via webDir).
set -e
cd "$(dirname "$0")"
cp index.html manifest.json sw.js icon-192.png icon-512.png www/
npx cap sync android
echo "OK — www/ atualizado e sincronizado com android/"

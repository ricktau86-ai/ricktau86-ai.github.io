/* Sentinela — separador "Notícias" (Leiria, Portugal, Mundo) a partir de feeds RSS públicos.
   Ficheiro à parte: se falhar, o resto da app continua a funcionar. Mostra só título, fonte e hora; o artigo abre no site. */
(function () {
  "use strict";
  const GN = (q) => `https://news.google.com/rss/search?q=${encodeURIComponent(q)}&hl=pt-PT&gl=PT&ceid=PT:pt-150`;
  const SECS = {
    leiria: { nome: "Leiria", feeds: [
      ["Diário de Leiria", "https://www.diarioleiria.pt/feed/"],
      ["Região de Leiria", "https://www.regiaodeleiria.pt/feed/"],
      ["Google Notícias", GN("Leiria when:3d")] ] },
    portugal: { nome: "Portugal", feeds: [
      ["Destaques", "https://news.google.com/rss?hl=pt-PT&gl=PT&ceid=PT:pt-150"],
      ["RTP", "https://www.rtp.pt/noticias/rss/pais"],
      ["Observador", "https://observador.pt/feed/"],
      ["CM", "https://www.cmjornal.pt/rss"],
      ["Notícias ao Minuto", "https://www.noticiasaominuto.com/rss/ultima-hora"] ] },
    mundo: { nome: "Mundo", feeds: [
      ["Destaques", "https://news.google.com/rss/headlines/section/topic/WORLD?hl=pt-PT&gl=PT&ceid=PT:pt-150"],
      ["RTP Mundo", "https://www.rtp.pt/noticias/rss/mundo"] ] }
  };
  const N = { sec: "leiria", data: {}, err: {}, busy: {} };
  try { const s = localStorage.getItem("sent:newsSec"); if (s && SECS[s]) N.sec = s; } catch (e) { }
  const $ = (s) => document.querySelector(s);
  const safe = (f) => function () { try { return f.apply(this, arguments); } catch (e) { console.warn("noticias", e); } };
  const normT = (t) => String(t || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9 ]/g, "").slice(0, 70);
  const dec = (t) => { const d = document.createElement("textarea"); d.innerHTML = t; return d.value; };
  const clean = (t) => dec(dec(String(t || "").replace(/<!\[CDATA\[|\]\]>/g, "")).replace(/<!\[CDATA\[|\]\]>/g, "").replace(/<[^>]+>/g, " ")).replace(/\s+/g, " ").trim();

  function parse(xml, label) {
    const doc = new DOMParser().parseFromString(xml, "text/xml");
    return [...doc.querySelectorAll("item")].slice(0, 40).map(it => {
      const g = (sel) => { const e = it.getElementsByTagName(sel)[0]; return e ? e.textContent : ""; };
      let title = clean(g("title")), source = clean(g("source")) || label;
      if (source && title.endsWith(" - " + source)) title = title.slice(0, -(source.length + 3));
      let img = "";
      const m = it.getElementsByTagName("media:content")[0] || it.getElementsByTagName("media:thumbnail")[0] || it.getElementsByTagName("enclosure")[0];
      if (m && /image|jpg|jpeg|png|webp/i.test((m.getAttribute("type") || "") + (m.getAttribute("url") || ""))) img = m.getAttribute("url") || "";
      if (!img) { const mm = (g("description") + g("content:encoded")).match(/<img[^>]+src=["']([^"']+)/i); if (mm) img = mm[1]; }
      const d = new Date(g("pubDate") || g("dc:date"));
      let sum = clean(g("description")); if (sum.length < 40) sum = clean(g("content:encoded"));
      if (/news\.google\./.test(g("link")) || sum.replace(/\W/g, "").toLowerCase() === title.replace(/\W/g, "").toLowerCase() || title.startsWith(sum.slice(0, 40))) sum = "";
      sum = sum.replace(/\s*(\[(…|\.\.\.)\]|The post .*$|O artigo .* apareceu primeiro em .*$|Continue a ler.*$|Ler mais.*$)/i, "").trim();
      if (sum.length > 700) sum = sum.slice(0, 700).replace(/\s+\S*$/, "") + "…";
      return { title, link: g("link").trim(), source, date: isNaN(d) ? null : d, img, sum };
    }).filter(x => x.title && x.link);
  }

  async function load(sec, force) {
    const key = "sent:news:" + sec;
    if (!force) { try { const c = JSON.parse(localStorage.getItem(key) || "null"); if (c && Date.now() - c.t < 10 * 60e3) { N.data[sec] = { t: c.t, items: c.items.map(x => ({ ...x, date: x.date ? new Date(x.date) : null })) }; return; } } catch (e) { } }
    N.busy[sec] = true; render();
    const all = []; let ok = 0;
    await Promise.all(SECS[sec].feeds.map(async ([label, url]) => {
      try {
        const ctrl = new AbortController(); const tm = setTimeout(() => ctrl.abort(), 15000);
        const r = await fetch(url, { signal: ctrl.signal, cache: "no-store", headers: { "Accept": "application/rss+xml, application/xml, text/xml" } }); clearTimeout(tm);
        if (!r.ok) throw new Error("HTTP " + r.status);
        const buf = await r.arrayBuffer(); let txt = new TextDecoder("utf-8").decode(buf);
        const enc = (txt.slice(0, 200).match(/encoding=["']([\w-]+)/i) || [])[1];
        if ((enc && !/utf-?8/i.test(enc)) || /\uFFFD/.test(txt.slice(0, 5000))) { try { txt = new TextDecoder(enc && !/utf-?8/i.test(enc) ? enc : "windows-1252").decode(buf); } catch (e) { } }
        all.push(...parse(txt, label)); ok++;
      } catch (e) { }
    }));
    N.busy[sec] = false;
    if (!ok) { N.err[sec] = true; if (!N.data[sec]) { try { const c = JSON.parse(localStorage.getItem(key) || "null"); if (c) N.data[sec] = { t: c.t, items: c.items.map(x => ({ ...x, date: x.date ? new Date(x.date) : null })), stale: true }; } catch (e) { } } render(); return; }
    N.err[sec] = false;
    const seen = new Set(); const now = Date.now();
    const items = all.filter(x => !x.date || x.date.getTime() <= now + 3600e3)
      .sort((a, b) => (b.date || 0) - (a.date || 0))
      .filter(x => { const k = normT(x.title); if (seen.has(k)) return false; seen.add(k); return true; }).slice(0, 60);
    N.data[sec] = { t: Date.now(), items };
    try { localStorage.setItem(key, JSON.stringify({ t: Date.now(), items })); } catch (e) { }
    render();
  }

  const render = safe(function () {
    const v = $("#v-noticias"); if (!v || v.hidden) return;
    const d = N.data[N.sec];
    let h = `<div class="dhead"><h1>Notícias</h1><span class="sp"></span><button class="rbtn${N.busy[N.sec] ? " spin" : ""}" id="nwRef" aria-label="Atualizar"><svg class="i s" viewBox="0 0 24 24"><path d="M20 12a8 8 0 1 1-2.3-5.6"/><path d="M20 4v5h-5"/></svg></button></div>
      <div class="places">${Object.entries(SECS).map(([k, s]) => `<button class="chip" data-sec="${k}" aria-pressed="${k === N.sec}">${s.nome}</button>`).join("")}</div>`;
    if (!d) {
      h += N.err[N.sec] ? `<div class="w"><div class="err">Não foi possível ler as notícias agora.${typeof NATIVE !== "undefined" && !NATIVE ? " No browser, os sites de notícias bloqueiam a leitura; use a app Android." : " Verifique a ligação e toque em atualizar."}</div></div>`
        : `<div class="w"><div class="empty">A carregar notícias…</div></div>`;
    } else {
      const it = d.items;
      if (!it.length) h += `<div class="w"><div class="empty">Sem notícias.</div></div>`;
      else {
        const top = it.find(x => x.img) || it[0];
        N.cur = it;
        h += `<a class="w" href="${esc(top.link)}" data-ni="${it.indexOf(top)}" target="_blank" rel="noopener" style="text-decoration:none;color:inherit;padding:0;overflow:hidden">
          ${top.img ? `<div style="height:170px;background:#2d2d2d url('${esc(top.img)}') center/cover"></div>` : ""}
          <div style="padding:14px 16px 16px;display:flex;flex-direction:column;gap:6px"><span class="small" style="color:var(--y);font-weight:500">${esc(top.source)}${top.date ? " · " + ago(top.date) : ""}</span><span style="font-size:19px;font-weight:700;line-height:1.3;text-wrap:balance">${esc(top.title)}</span></div></a>`;
        h += `<div class="w" style="gap:0;padding:6px 16px">` + it.filter(x => x !== top).slice(0, 40).map(x => `<a href="${esc(x.link)}" data-ni="${it.indexOf(x)}" target="_blank" rel="noopener" style="display:flex;gap:12px;padding:12px 0;border-top:1px solid var(--line);text-decoration:none;color:inherit;align-items:flex-start">
          <span style="flex:1;min-width:0"><span style="display:block;font-weight:600;line-height:1.35">${esc(x.title)}</span><span class="small muted">${esc(x.source)}${x.date ? " · " + ago(x.date) : ""}</span></span>
          ${x.img ? `<span style="width:72px;height:56px;border-radius:10px;flex:none;background:#2d2d2d url('${esc(x.img)}') center/cover"></span>` : ""}</a>`).join("").replace('border-top:1px solid var(--line)', 'border-top:0') + `</div>`;
      }
      h += `<div class="foot">${d.stale ? "Sem ligação — a mostrar notícias guardadas. " : ""}Atualizado ${ago(d.t)}. Fontes: ${SECS[N.sec].feeds.map(f => f[0] === "Destaques" || f[0] === "Google Notícias" ? "Google Notícias" : f[0]).filter((x, i, a) => a.indexOf(x) === i).join(", ")}. Toque numa notícia para a ler no site.</div>`;
    }
    v.innerHTML = h;
    v.querySelectorAll("[data-sec]").forEach(b => b.onclick = () => { N.sec = b.dataset.sec; try { localStorage.setItem("sent:newsSec", N.sec); } catch (e) { } render(); if (!N.data[N.sec]) load(N.sec); else if (Date.now() - N.data[N.sec].t > 10 * 60e3) load(N.sec, true); });
    const rf = $("#nwRef"); if (rf) rf.onclick = () => load(N.sec, true);
    v.querySelectorAll("[data-ni]").forEach(a => a.onclick = (ev) => { ev.preventDefault(); sintese(N.cur[+a.dataset.ni]); });
  });

  /* Síntese: o resumo que o próprio jornal publica no feed; o artigo completo abre no site */
  const sintese = safe(function (x) {
    if (!x) return;
    openSheet(`${x.img ? `<div style="height:180px;border-radius:16px;margin:0 0 12px;background:#2d2d2d url('${esc(x.img)}') center/cover"></div>` : ""}
      <span class="small" style="color:var(--y);font-weight:500">${esc(x.source)}${x.date ? " · " + fDT(x.date) + " (" + ago(x.date) + ")" : ""}</span>
      <h3>${esc(x.title)}</h3>
      ${x.sum ? `<p style="font-size:16px;line-height:1.55;color:var(--fg)">${esc(x.sum)}</p>` : `<p class="muted">Esta fonte não publica resumo. Toque em "Ler no site" para ver a notícia completa.</p>`}
      <div class="row"><a class="btn y" href="${esc(x.link)}" target="_blank" rel="noopener">Ler no site</a><button class="btn" id="nwSh">Partilhar</button></div>
      <p class="small muted">Resumo publicado por ${esc(x.source)} no seu feed de notícias.</p>`);
    const b = document.getElementById("nwSh"); if (b) b.onclick = () => shareIt(x.title, x.title, x.link);
  });

  const prevShow = window.extraShow;
  window.extraShow = function (v) { try { if (prevShow) prevShow(v); } catch (e) { } if (v === "noticias") { render(); load(N.sec); } };
  setInterval(() => { const v = $("#v-noticias"); if (v && !v.hidden) load(N.sec); }, 10 * 60e3);
})();

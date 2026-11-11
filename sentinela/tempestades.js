/* Sentinela — separador "Tempestades".
   Mostra o que o processamento no GitHub calcula com dados abertos do ECMWF:
   mapas animados (rajadas, chuva, isóbaras), trajetória das depressões nos cenários do ensemble,
   cone com 80% dos cenários, entrada em terra mais provável e a evolução em Leiria.
   Ficheiro à parte: se falhar, o resto da app continua a funcionar. */
(function () {
  "use strict";
  const BASE = "https://raw.githubusercontent.com/ricktau86-ai/ricktau86-ai.github.io/sentinela-tempestades/";
  const T = { data: null, err: null, busy: false, map: null, idx: 0, play: false, speed: 1, timer: null,
    layers: { vento: true, chuva: false, isobaras: true }, showMembers: false, showPrev: true, ov: {}, stormLayer: null, imgCache: {} };
  try { const s = JSON.parse(localStorage.getItem("sent:tLayers") || "null"); if (s) Object.assign(T.layers, s); } catch (e) { }
  const $ = (s) => document.querySelector(s);
  const runLbl = (iso) => { const d = new Date(iso + "Z"); return `${String(d.getUTCHours()).padStart(2, "0")} UTC de ${new Intl.DateTimeFormat("pt-PT", { weekday: "short", day: "numeric", timeZone: "UTC" }).format(d)}`; };
  try { const st = document.createElement("style"); st.textContent = ".ttip{background:rgba(18,18,18,.85);color:#fff;border:0;border-radius:6px;padding:2px 6px;font:600 11px/1.3 var(--font);box-shadow:none}.ttip::before{display:none}.tlab{position:absolute;left:8px;top:-8px;white-space:nowrap;background:rgba(18,18,18,.78);color:#fff;border-radius:5px;padding:1px 5px;font:600 10.5px/1.3 var(--font);pointer-events:none}"; document.head.appendChild(st); } catch (e) { }
  const safe = (f) => function () { try { return f.apply(this, arguments); } catch (e) { console.warn("tempestades", e); } };
  const DFh = new Intl.DateTimeFormat("pt-PT", { weekday: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  const fh = (iso) => { const d = new Date(iso); return DFh.format(d).replace(",", ""); };
  const hh = (iso) => new Intl.DateTimeFormat("pt-PT", { hour: "2-digit", minute: "2-digit" }).format(new Date(iso));
  const dia = (iso) => new Intl.DateTimeFormat("pt-PT", { weekday: "long", day: "numeric", month: "short" }).format(new Date(iso));

  async function load(force) {
    if (T.busy) return; T.busy = true; render();
    try {
      const r = await fetch(BASE + "data.json?t=" + (force ? Date.now() : Math.floor(Date.now() / 300000)), { cache: "no-store" });
      if (!r.ok) throw new Error("HTTP " + r.status);
      T.data = await r.json(); T.err = null;
      try { localStorage.setItem("sent:tData", JSON.stringify(T.data)); } catch (e) { }
      const now = Date.now(); const ts = T.data.frames.times.map(t => new Date(t).getTime());
      T.idx = Math.max(0, ts.findIndex(t => t >= now - 90 * 60e3)); if (T.idx < 0) T.idx = 0;
    } catch (e) {
      T.err = e;
      if (!T.data) { try { T.data = JSON.parse(localStorage.getItem("sent:tData") || "null"); if (T.data) T.data._stale = true; } catch (x) { } }
    }
    T.busy = false; render();
  }

  /* ---------- mapa ---------- */
  function ensureMap() {
    const el = $("#tmap"); if (!el || !window.L) return;
    if (T.map && T.map._container === el) { T.map.invalidateSize(); return; }
    if (T.map) { try { T.map.remove(); } catch (e) { } }
    T.map = L.map(el, { zoomControl: false, attributionControl: true, worldCopyJump: true, minZoom: 3, maxZoom: 9 }).setView([39, -14], 4);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19, className: "osm-dark", attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> · ECMWF' }).addTo(T.map);
    T.ov = {}; T.stormLayer = null; T.posLayer = null;
  }
  function frameUrl(layer, i) { return BASE + "frames/" + T.data.frames.layers[layer][i]; }
  function preload() {
    if (!T.data) return; const f = T.data.frames;
    ["vento", "chuva", "isobaras"].filter(k => T.layers[k]).forEach(k => f.layers[k].forEach((n, i) => { const u = frameUrl(k, i); if (!T.imgCache[u]) { const im = new Image(); im.src = u; T.imgCache[u] = im; } }));
  }
  const drawFrame = safe(function () {
    if (!T.map || !T.data) return; const f = T.data.frames; const b = f.bounds;
    ["chuva", "vento", "isobaras"].forEach(k => {
      if (!T.layers[k]) { if (T.ov[k]) { T.map.removeLayer(T.ov[k]); T.ov[k] = null; } return; }
      const u = frameUrl(k, T.idx);
      if (!T.ov[k]) T.ov[k] = L.imageOverlay(u, b, { opacity: k === "isobaras" ? 1 : 0.85, interactive: false }).addTo(T.map);
      else T.ov[k].setUrl(u);
    });
    const lab = $("#tTime"); if (lab) lab.textContent = fh(f.times[T.idx]) + (new Date(f.times[T.idx]) < new Date() ? "" : "");
    const sl = $("#tSlider"); if (sl && +sl.value !== T.idx) sl.value = T.idx;
    // posição prevista de cada depressão neste instante
    if (T.posLayer) T.map.removeLayer(T.posLayer); T.posLayer = L.layerGroup().addTo(T.map);
    const tnow = new Date(f.times[T.idx]).getTime();
    (T.data.tempestades || []).forEach(s => {
      const m = s.media; let a = null, c = null;
      for (let i = 0; i < m.length - 1; i++) { const t0 = new Date(m[i].t).getTime(), t1 = new Date(m[i + 1].t).getTime(); if (tnow >= t0 && tnow <= t1) { a = m[i]; c = m[i + 1]; break; } }
      if (!a) return; const k = (tnow - new Date(a.t)) / (new Date(c.t) - new Date(a.t));
      const la = a.lat + (c.lat - a.lat) * k, lo = a.lon + (c.lon - a.lon) * k;
      L.marker([la, lo], { icon: L.divIcon({ className: "", iconSize: [26, 26], iconAnchor: [13, 13], html: `<div style="width:26px;height:26px;border-radius:50%;background:#ff5449;border:3px solid #fff;box-shadow:0 0 0 4px rgba(255,84,73,.35);display:grid;place-items:center;color:#fff;font:900 11px/1 var(--font)">B</div>` }), interactive: false }).addTo(T.posLayer);
    });
  });
  const drawStorms = safe(function () {
    if (!T.map || !T.data) return;
    if (T.stormLayer) T.map.removeLayer(T.stormLayer);
    const g = T.stormLayer = L.layerGroup().addTo(T.map);
    const storms = T.data.tempestades || []; const allPts = [];
    storms.forEach((s, si) => {
      // cone com 80% dos cenários
      s.cone.forEach(ring => L.polygon(ring, { color: "#ffffff", weight: 1.5, opacity: .9, fillColor: "#ffffff", fillOpacity: .12, interactive: false }).addTo(g));
      if (T.showMembers) s.trajetorias.forEach(tr => L.polyline(tr, { color: "#ffd54f", weight: 1, opacity: .35, interactive: false }).addTo(g));
      // previsão anterior
      const prev = T.showPrev && T.data.anterior && (T.data.anterior.tempestades || []).find(p => km(p.media[0].lat, p.media[0].lon, s.media[0].lat, s.media[0].lon) < 800 || p.id === s.id);
      if (prev) L.polyline(prev.media.map(q => [q.lat, q.lon]), { color: "#90caf9", weight: 2, opacity: .8, dashArray: "2 6", interactive: false }).addTo(g);
      // trajetória média com marcas de 6 em 6 h
      L.polyline(s.media.map(q => [q.lat, q.lon]), { color: "#ffffff", weight: 2.5, dashArray: "8 6", interactive: false }).addTo(g);
      s.media.forEach((q, i) => {
        allPts.push([q.lat, q.lon]);
        const hU = new Date(q.t).getUTCHours();
        if (i && hU % 6 !== 0) return;
        const short = new Intl.DateTimeFormat("pt-PT", { weekday: "short" }).format(new Date(q.t)).replace(".", "") + " " + hh(q.t).replace(":00", "h");
        const lbl = i ? short : `${s.nome || "Depressão"} agora`;
        L.circleMarker([q.lat, q.lon], { radius: i ? 4 : 7, color: "#121212", weight: 2, fillColor: i ? "#ffffff" : "#ff5449", fillOpacity: 1 })
          .bindTooltip(`${lbl}<br>${Math.round(q.p)} hPa · ±${q.r80} km`, { permanent: false, direction: "right", className: "ttip", offset: [6, 0] })
          .addTo(g);
        if (i === 0 || hU % 12 === 0) L.marker([q.lat, q.lon], { interactive: false, icon: L.divIcon({ className: "", iconSize: [0, 0], html: `<span class="tlab">${esc(lbl)}</span>` }) }).addTo(g);
      });
      const lf = s.entrada_em_terra;
      if (lf) lf.pontos.forEach(p => L.circleMarker(p, { radius: 3, stroke: false, fillColor: "#ff5449", fillOpacity: .8, interactive: false }).addTo(g));
    });
    if (!T.fitted) { T.map.fitBounds([[31.5, -24], [45.5, 2]]); T.fitted = true; }
  });

  function startPlay() { stopPlay(); T.play = true; const n = T.data.frames.times.length; T.timer = setInterval(() => { T.idx = (T.idx + 1) % n; drawFrame(); }, 900 / T.speed); updPlayBtn(); }
  function stopPlay() { T.play = false; if (T.timer) clearInterval(T.timer); T.timer = null; updPlayBtn(); }
  function updPlayBtn() { const b = $("#tPlay"); if (b) b.innerHTML = T.play ? '<svg class="i" viewBox="0 0 24 24"><path d="M8 5v14M16 5v14"/></svg>' : '<svg class="i" viewBox="0 0 24 24"><path d="M7 4l13 8-13 8z"/></svg>'; }

  /* ---------- gráfico de Leiria (SVG simples) ---------- */
  function chartLeiria() {
    const L1 = T.data.leiria; if (!L1 || !L1.hres) return "";
    const H = L1.hres, E = L1.ens_pressao || []; const W = 340, Hh = 150, pad = 28;
    const t0 = new Date(H[0].t).getTime(), t1 = new Date(H[H.length - 1].t).getTime();
    const X = (t) => pad + (new Date(t).getTime() - t0) / (t1 - t0) * (W - pad - 8);
    const ps = [...H.map(h => h.p), ...E.filter(e => new Date(e.t).getTime() <= t1).flatMap(e => [e.p10, e.p90])];
    const pmin = Math.floor(Math.min(...ps) / 5) * 5, pmax = Math.ceil(Math.max(...ps) / 5) * 5;
    const Yp = (p) => 8 + (pmax - p) / (pmax - pmin) * (Hh - 40);
    const gmax = Math.max(60, ...H.map(h => h.rajada || 0)); const rmax = Math.max(10, ...H.map(h => h.chuva3h || 0));
    const Eb = E.filter(e => new Date(e.t).getTime() <= t1);
    const band = Eb.length > 1 ? `<path d="M${Eb.map(e => `${X(e.t)},${Yp(e.p90)}`).join("L")}L${Eb.slice().reverse().map(e => `${X(e.t)},${Yp(e.p10)}`).join("L")}Z" fill="rgba(144,202,249,.25)"/>` : "";
    const bw = (W - pad - 8) / H.length * 0.7;
    const bars = H.map(h => `<rect x="${X(h.t) - bw / 2}" y="${Hh - 4 - (h.chuva3h / rmax) * 26}" width="${bw}" height="${(h.chuva3h / rmax) * 26}" fill="#4fc3f7" opacity=".8"/>`).join("");
    const gl = H.filter(h => h.rajada != null);
    const gust = `<polyline fill="none" stroke="#ff9800" stroke-width="2" points="${gl.map(h => `${X(h.t)},${8 + (1 - h.rajada / gmax) * (Hh - 40)}`).join(" ")}"/>`;
    const pres = `<polyline fill="none" stroke="#e6e1e5" stroke-width="2" points="${H.map(h => `${X(h.t)},${Yp(h.p)}`).join(" ")}"/>`;
    const nowX = Date.now() > t0 && Date.now() < t1 ? `<line x1="${X(new Date().toISOString())}" x2="${X(new Date().toISOString())}" y1="4" y2="${Hh - 4}" stroke="#ffeb3b" stroke-dasharray="3 3"/>` : "";
    const days = []; for (let t = t0; t <= t1; t += 3600e3) { const d = new Date(t); if (d.getHours() === 0) days.push(`<line x1="${X(d.toISOString())}" x2="${X(d.toISOString())}" y1="4" y2="${Hh - 4}" stroke="#3a3a3a"/><text x="${X(d.toISOString()) + 3}" y="${Hh - 32}" fill="#938f99" font-size="9">${new Intl.DateTimeFormat("pt-PT", { weekday: "short" }).format(d)}</text>`); }
    const gpk = gl.reduce((m, h) => h.rajada > m.rajada ? h : m, gl[0] || { rajada: 0 });
    const tot = H.reduce((s, h) => s + (h.chuva3h || 0), 0); const ppk = H.reduce((m, h) => h.p < m.p ? h : m, H[0]);
    return `<div class="w"><div class="wh"><h2>Leiria — próximas 72 h</h2><span class="small muted">ECMWF</span></div>
      <div class="env"><div><span class="k">Rajada máxima</span><span class="v" style="color:${gpk.rajada >= 90 ? "#ff5449" : gpk.rajada >= 70 ? "#ff9800" : "inherit"}">${gpk.rajada || "—"} km/h</span><span class="q" style="--qc:var(--fg2)">${gpk.t ? fh(gpk.t) : ""}</span></div>
      <div><span class="k">Chuva acumulada</span><span class="v">${Math.round(tot)} mm</span><span class="q" style="--qc:var(--fg2)">em 72 h</span></div>
      <div><span class="k">Pressão mínima</span><span class="v">${Math.round(ppk.p)} hPa</span><span class="q" style="--qc:var(--fg2)">${fh(ppk.t)}</span></div></div>
      <div style="overflow-x:auto"><svg viewBox="0 0 ${W} ${Hh}" style="width:100%;max-width:${W * 1.4}px;height:auto;display:block">${days}${band}${bars}${pres}${gust}${nowX}
        <text x="2" y="${Yp(pmax) + 4}" fill="#938f99" font-size="9">${pmax}</text><text x="2" y="${Yp(pmin)}" fill="#938f99" font-size="9">${pmin}</text></svg></div>
      <div class="small muted"><span style="color:#e6e1e5">━</span> pressão (hPa) com <span style="color:#90caf9">faixa de 80% dos cenários</span> · <span style="color:#ff9800">━</span> rajadas (máx. ${Math.round(gmax)} km/h no topo) · <span style="color:#4fc3f7">▮</span> chuva por 3 h<br>Valores do modelo para a quadrícula de Leiria (~25 km). Os avisos do IPMA abrangem todo o distrito, incluindo o litoral, onde o vento costuma ser mais forte.</div></div>`;
  }

  /* ---------- separador ---------- */
  const render = safe(function () {
    const v = $("#v-tempestades"); if (!v || v.hidden) return;
    const d = T.data;
    let h = `<div class="dhead"><h1>Tempestades</h1><span class="sp"></span><button class="rbtn${T.busy ? " spin" : ""}" id="tRef" aria-label="Atualizar"><svg class="i s" viewBox="0 0 24 24"><path d="M20 12a8 8 0 1 1-2.3-5.6"/><path d="M20 4v5h-5"/></svg></button></div>`;
    if (!d) { v.innerHTML = h + `<div class="w"><div class="${T.err ? "err" : "empty"}">${T.err ? "Não foi possível obter os dados das tempestades. Tente atualizar." : "A carregar a previsão do ECMWF…"}</div></div>`; bind(); return; }
    const storms = d.tempestades || [];
    h += `<div class="small muted">ECMWF · corrida principal das ${runLbl(d.hres_run)} e ensemble das ${runLbl(d.ens_run)} (${d.cenarios} cenários) · processado ${ago(new Date(d.gerado))}${d._stale ? " · sem ligação, dados guardados" : ""}</div>`;
    h += `<div class="w" style="padding:0;overflow:hidden;gap:0">
      <div id="tmap" style="height:58vh;min-height:320px;max-height:560px;position:relative"></div>
      <div style="padding:10px 12px;display:flex;flex-direction:column;gap:8px">
        <div style="display:flex;gap:10px;align-items:center"><button class="rbtn" id="tPlay" aria-label="Reproduzir" style="background:var(--y);color:var(--on-y)"></button>
          <input type="range" id="tSlider" min="0" max="${d.frames.times.length - 1}" value="${T.idx}" style="flex:1;accent-color:var(--y)">
          <button class="chip" id="tSpeed" style="padding:6px 10px">${T.speed}×</button></div>
        <div style="display:flex;justify-content:space-between;align-items:center"><b id="tTime" class="mono"></b><span class="small muted">previsão</span></div>
        <div class="places" style="margin:0">
          <button class="chip" data-ly="vento" aria-pressed="${T.layers.vento}">Rajadas</button><button class="chip" data-ly="chuva" aria-pressed="${T.layers.chuva}">Chuva</button>
          <button class="chip" data-ly="isobaras" aria-pressed="${T.layers.isobaras}">Isóbaras</button><button class="chip" id="tMem" aria-pressed="${T.showMembers}">Cenários</button>
          <button class="chip" id="tPrev" aria-pressed="${T.showPrev}">Previsão anterior</button></div>
        <div class="small muted" id="tLeg"></div></div></div>`;
    // cartões das tempestades
    if (!storms.length) h += `<div class="w"><div class="clear"><div class="ok">${ic(P.check)}</div><div><b>Sem depressões bem definidas a aproximar-se de Portugal</b><span class="small muted">nos próximos 4 dias, segundo o ensemble do ECMWF</span></div></div></div>`;
    storms.forEach(s => {
      const lf = s.entrada_em_terra; const m0 = s.media[0];
      const nome = s.nome ? `Depressão ${s.nome}` : "Depressão";
      let ch = "";
      const prev = d.anterior && (d.anterior.tempestades || []).find(p => km(p.media[0].lat, p.media[0].lon, m0.lat, m0.lon) < 800);
      if (prev) { const shifts = s.media.map(q => { const pq = prev.media.find(x => x.t === q.t); return pq ? km(q.lat, q.lon, pq.lat, pq.lon) : null; }).filter(x => x != null); if (shifts.length) { const mx = Math.max(...shifts); ch = `<div class="small" style="color:${mx > 150 ? "#ff9800" : "var(--fg2)"}">${mx > 150 ? "Trajetória mudou" : "Trajetória estável"} face à corrida anterior (desvio máximo ${Math.round(mx)} km).</div>`; } }
      h += `<div class="w"><div class="wh"><h2>${esc(nome)}</h2><span class="tag" style="background:#5c1612;color:#ffb4ab">${Math.round(s.pmin)} hPa</span></div>
        ${lf ? `<div class="banner y" style="flex-direction:column;gap:4px"><div class="b-t">Entrada em terra mais provável entre ${esc(lf.troco[0])} e ${esc(lf.troco[1])}</div>
          <div class="b-s">Mais provável perto de <b>${esc(lf.mais_provavel)}</b>, ${dia(lf.janela[0])}, entre as ${hh(lf.janela[0])} e as ${hh(lf.janela[1])} (margem ${hh(lf.margem[0])}–${hh(lf.margem[1])}, hora de Portugal continental).</div>
          <div class="b-s">${lf.cenarios} de ${lf.de} cenários entram em terra.</div></div>`
          : `<div class="small muted">A maioria dos cenários não leva o centro a terra na Península nos próximos 4 dias. Passagem mais próxima de Portugal: ${s.dist_pt_min_km} km.</div>`}
        ${ch}
        <div class="small muted">Posição inicial ${m0.lat.toFixed(1)}°N ${Math.abs(m0.lon).toFixed(1)}°${m0.lon < 0 ? "W" : "E"} · ${Math.round(m0.p)} hPa · seguida em ${s.cenarios} cenários</div>
        <div style="overflow-x:auto"><div style="display:flex;gap:6px;width:max-content">${s.media.filter(q => new Date(q.t).getUTCHours() % 6 === 0).map(q => `<div style="background:var(--s2);border-radius:10px;padding:6px 8px;font-size:12px;min-width:76px"><b>${fh(q.t)}</b><br><span class="mono">${Math.round(q.p)} hPa</span><br><span class="muted">±${q.r80} km</span></div>`).join("")}</div></div>
        <div class="small muted">"±km": raio que contém 80% dos cenários nesse instante.</div></div>`;
    });
    h += chartLeiria();
    h += `<div class="w"><div class="small muted" style="line-height:1.55"><b style="color:var(--fg)">Como ler.</b> O cone branco mostra onde deverá passar o centro da depressão: contém 80% dos ${d.cenarios} cenários do ensemble do ECMWF. A linha tracejada é a trajetória média; os pontos são posições previstas de 6 em 6 horas. Os pontos vermelhos na costa são onde cada cenário entra em terra. Vento forte, chuva e agitação marítima estendem-se muito para além do cone.<br><br>
      <b style="color:var(--fg)">Limitações.</b> Produto experimental, calculado automaticamente. Os dados abertos do ECMWF chegam com 7 a 9 horas de atraso; usa só o ECMWF (sem outros modelos). Depressões fracas ou com vários centros podem não ser seguidas. <b style="color:var(--y)">Os avisos oficiais são os do IPMA.</b></div></div>
      <div class="foot">${esc(d.fonte)}. Mapas e cálculos: Sentinela.</div>`;
    v.innerHTML = h; bind();
    setTimeout(() => { ensureMap(); drawStorms(); drawFrame(); preload(); legend(); updPlayBtn(); }, 30);
  });
  function legend() {
    const l = $("#tLeg"); if (!l) return; const parts = [];
    if (T.layers.vento) parts.push(`Rajadas km/h: <span style="color:#6bbff2">40</span> <span style="color:#fae05a">55</span> <span style="color:#ff9e26">70</span> <span style="color:#f24d33">85</span> <span style="color:#d91a73">100</span> <span style="color:#9e33bf">115</span> <span style="color:#fff">130+</span>`);
    if (T.layers.chuva) parts.push(`Chuva mm/3 h: <span style="color:#8cccff">0,5</span> <span style="color:#4099ff">2</span> <span style="color:#1a59f2">5</span> <span style="color:#8c40f2">10</span> <span style="color:#e633bf">20</span> <span style="color:#ff6666">40+</span>`);
    if (T.layers.isobaras) parts.push(`Isóbaras de 4 em 4 hPa · <b style="color:#ff5449">B</b> baixa · <b style="color:#4fc3f7">A</b> alta`);
    l.innerHTML = parts.join("<br>");
  }
  function bind() {
    const r = $("#tRef"); if (r) r.onclick = () => { T.fitted = false; load(true); };
    const p = $("#tPlay"); if (p) p.onclick = () => T.play ? stopPlay() : startPlay();
    const s = $("#tSlider"); if (s) s.oninput = () => { stopPlay(); T.idx = +s.value; drawFrame(); };
    const sp = $("#tSpeed"); if (sp) sp.onclick = () => { T.speed = T.speed === 1 ? 2 : T.speed === 2 ? 4 : 1; sp.textContent = T.speed + "×"; if (T.play) startPlay(); };
    document.querySelectorAll("#v-tempestades [data-ly]").forEach(b => b.onclick = () => { const k = b.dataset.ly; T.layers[k] = !T.layers[k]; b.setAttribute("aria-pressed", T.layers[k]); try { localStorage.setItem("sent:tLayers", JSON.stringify(T.layers)); } catch (e) { } drawFrame(); preload(); legend(); });
    const m = $("#tMem"); if (m) m.onclick = () => { T.showMembers = !T.showMembers; m.setAttribute("aria-pressed", T.showMembers); drawStorms(); };
    const pv = $("#tPrev"); if (pv) pv.onclick = () => { T.showPrev = !T.showPrev; pv.setAttribute("aria-pressed", T.showPrev); drawStorms(); };
  }

  const prevShow = window.extraShow;
  window.extraShow = function (v) {
    try { if (prevShow) prevShow(v); } catch (e) { }
    if (v === "tempestades") { if (T.map) { try { T.map.remove(); } catch (e) { } T.map = null; } T.fitted = false; render(); if (!T.data || Date.now() - new Date(T.data.gerado).getTime() > 30 * 60e3) load(); }
    else stopPlay();
  };
  setInterval(() => { const v = $("#v-tempestades"); if (v && !v.hidden && !T.play) load(); }, 20 * 60e3);
})();

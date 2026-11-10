/* Sentinela — separadores "Estradas" e "Extra".
   Ficheiro à parte: se algo aqui falhar, o Painel, o Mapa e o Perfil continuam a funcionar. */
(function () {
  "use strict";
  const X = { firms: null, firmsT: 0, firmsErr: null, pollen: null, pollenErr: null, rad: null, lastWidget: "" };
  const safe = (f) => function () { try { return f.apply(this, arguments); } catch (e) { console.warn("extra", e); } };
  const $ = (s) => document.querySelector(s);

  /* ---------------- ESTRADAS ---------------- */
  const ROAD_RE = /acidente|rodovi|colis|despiste|atropel|capotamento|infraestruturas e vias|vias de comunica|movimento de massa|derrocada|queda de [aá]rvore|queda de estrutura|desabamento|inunda|alagamento|limpeza de via|piso escorregadio|obstru/i;
  const isRoad = (a) => a.kind === "car" || ROAD_RE.test((a.nat || "") + " " + (a.cat || ""));
  const roadLabel = (a) => /acidente|rodovi|colis|despiste|atropel|capotamento/i.test(a.nat) ? "Acidente" :
    /[aá]rvore/i.test(a.nat) ? "Queda de árvore" : /movimento de massa|derrocada|desabamento/i.test(a.nat) ? "Derrocada" :
    /inunda|alagamento/i.test(a.nat) ? "Inundação" : "Via afetada";

  const renderRoads = safe(function () {
    const v = $("#v-estradas"); if (!v || v.hidden) return;
    const R = X.rad || Math.max(cfg.radius, 25);
    const places = [{ ...cfg.me, name: cfg.me.name || "A minha localização" }, ...cfg.cities];
    const all = (S.act || []).filter(a => !a.stale && isRoad(a));
    let html = `<div class="ptitle"><h1>Estradas</h1></div>
      <div class="places">${[10, 25, 50, 100, 300].map(r => `<button class="chip" data-r="${r}" aria-pressed="${r === R}">${r === 300 ? "Todo o país" : r + " km"}</button>`).join("")}</div>`;
    if (!S.actSrc && !(S.act || []).length) {
      html += `<div class="w"><div class="empty">A carregar as ocorrências da Proteção Civil… Abra o Painel se demorar.</div></div>`;
    } else {
      html += `<div class="banner"><div><div class="b-t">${all.length} ocorrências com impacto em estradas no país</div><div class="b-s">Acidentes, quedas de árvores, derrocadas, inundações e intervenções em vias, segundo a Proteção Civil.</div></div></div>`;
      for (const p of places) {
        const list = all.map(a => ({ ...a, d: km(p.lat, p.lon, a.lat, a.lon) })).filter(a => R >= 300 || a.d <= R).sort((a, b) => a.d - b.d);
        html += `<div class="w"><div class="wh"><h2>${esc(p.name)}</h2><span class="small muted">${list.length} ${R >= 300 ? "no país" : "até " + R + " km"}</span></div>` +
          (list.length ? list.slice(0, 15).map(a => actRow(a).replace('<span class="t">', `<span class="t"><span class="tag g">${roadLabel(a)}</span> `)).join("") +
            (list.length > 15 ? `<div class="small muted">E mais ${list.length - 15}.</div>` : "")
            : `<div class="clear"><div class="ok">${ic(P.check)}</div><div><b>Sem ocorrências nas estradas</b><span class="small muted">num raio de ${R} km</span></div></div>`) + `</div>`;
        if (R >= 300) break;
      }
      html += `<div class="w"><div class="wh"><h2>Mais informação</h2></div>
        <a class="item" style="padding:10px 0;text-decoration:none" href="https://www.infraestruturasdeportugal.pt/pt-pt" target="_blank" rel="noopener"><span class="ii">${ic(P.car)}</span><span class="it"><b>Infraestruturas de Portugal</b><span>Cortes e condicionamentos anunciados</span></span></a>
        <div class="small muted">A IP não publica dados abertos de cortes em tempo real, por isso esta lista usa só as ocorrências da Proteção Civil.</div></div>
        <div class="foot">Fonte: ${esc(S.actSrc || "Proteção Civil")}. Ocorrências abertas há mais de 3 dias ${cfg.showLong ? "incluídas" : "escondidas (Perfil → Atividade de emergência)"}.</div>`;
    }
    v.innerHTML = html;
    v.querySelectorAll("[data-r]").forEach(b => b.onclick = () => { X.rad = +b.dataset.r; renderRoads(); });
    v.querySelectorAll("[data-a]").forEach(e => e.onclick = () => sheetAct(S.act.find(x => x.id === e.dataset.a)));
  });

  /* ---------------- FOGOS POR SATÉLITE (NASA FIRMS) ---------------- */
  async function loadFirms() {
    if (Date.now() - X.firmsT < 20 * 60e3 && X.firms) return;
    const urls = ["https://firms.modaps.eosdis.nasa.gov/data/active_fire/noaa-20-viirs-c2/csv/J1_VIIRS_C2_Europe_24h.csv",
      "https://firms.modaps.eosdis.nasa.gov/data/active_fire/suomi-npp-viirs-c2/csv/SUOMI_VIIRS_C2_Europe_24h.csv"];
    const out = []; let ok = 0;
    for (const u of urls) {
      try {
        const r = await fetch(u, { cache: "no-store" }); if (!r.ok) throw new Error("HTTP " + r.status);
        const lines = (await r.text()).trim().split(/\r?\n/); const head = lines.shift().split(",");
        const ix = (k) => head.indexOf(k); ok++;
        for (const l of lines) {
          const c = l.split(","); const lat = +c[ix("latitude")], lon = +c[ix("longitude")];
          if (!(lat > 36.8 && lat < 42.3 && lon > -9.7 && lon < -6.1)) continue;
          if (c[ix("confidence")] === "low") continue;
          const t = c[ix("acq_time")].padStart(4, "0");
          out.push({ lat, lon, frp: +c[ix("frp")], sat: c[ix("satellite")], dn: c[ix("daynight")], time: new Date(`${c[ix("acq_date")]}T${t.slice(0, 2)}:${t.slice(2)}:00Z`) });
        }
      } catch (e) { X.firmsErr = e; }
    }
    if (!ok) throw X.firmsErr || new Error("sem dados");
    const seen = new Set(); X.firms = out.filter(f => { const k = f.lat.toFixed(2) + "," + f.lon.toFixed(2); if (seen.has(k)) return false; seen.add(k); return true; });
    X.firmsT = Date.now(); X.firmsErr = null;
  }
  let firmsLayer = null;
  const showFirmsOnMap = safe(function () {
    go("mapa");
    setTimeout(safe(() => {
      if (!window.L || typeof map === "undefined" || !map) return;
      if (firmsLayer) map.removeLayer(firmsLayer);
      firmsLayer = L.layerGroup();
      (X.firms || []).forEach(f => L.circleMarker([f.lat, f.lon], { radius: 6 + Math.min(8, Math.sqrt(f.frp || 1)), color: "#121212", weight: 1, fillColor: "#ff3d00", fillOpacity: .9 })
        .on("click", () => sheetFirms(f)).addTo(firmsLayer));
      firmsLayer.addTo(map);
      const h = here(); map.setView([h.lat, h.lon], 8);
      const lg = $("#mlegend"); if (lg) lg.textContent = `${(X.firms || []).length} focos de calor por satélite (24 h) · NASA FIRMS`;
    }), 400);
  });
  function sheetFirms(f) {
    const h = here();
    openSheet(`<span class="tag" style="background:#3b1d14;color:#ff7043">Satélite</span><h3>Foco de calor detetado</h3><div class="muted">${fKm(km(h.lat, h.lon, f.lat, f.lon))} de ${esc(h.name)}</div>
      <dl class="kv"><dt>Detetado</dt><dd>${fDT(f.time)} (${ago(f.time)})</dd><dt>Satélite</dt><dd>${f.sat === "N20" ? "NOAA-20" : f.sat === "N" ? "Suomi NPP" : esc(f.sat)} · ${f.dn === "D" ? "dia" : "noite"}</dd><dt>Potência radiativa</dt><dd>${num(f.frp, 1)} MW</dd></dl>
      <p class="small">Pode ser um incêndio, uma queimada ou uma fonte de calor industrial. Confirme nas ocorrências da Proteção Civil.</p>
      <div class="row"><a class="btn y" href="${dirUrl(f.lat, f.lon)}" target="_blank" rel="noopener">Obter direções</a><a class="btn" href="https://firms.modaps.eosdis.nasa.gov/map/#d:24hrs;@${f.lon},${f.lat},11z" target="_blank" rel="noopener">Ver na NASA</a></div>`);
  }

  /* ---------------- PÓLEN ---------------- */
  const POL = [["grass_pollen", "Gramíneas", [10, 50]], ["olive_pollen", "Oliveira", [30, 100]], ["birch_pollen", "Bétula", [30, 100]],
    ["alder_pollen", "Amieiro", [30, 100]], ["mugwort_pollen", "Artemísia", [10, 50]], ["ragweed_pollen", "Ambrósia", [10, 50]]];
  async function loadPollen() {
    const h = here();
    const r = await getJSON(`https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${h.lat}&longitude=${h.lon}&current=${POL.map(p => p[0]).join(",")}&hourly=${POL.map(p => p[0]).join(",")}&forecast_days=3&timezone=Europe%2FLisbon`, { key: "pollen" });
    X.pollen = r.data;
  }
  const polLevel = (v, t) => v == null ? ["—", "#938f99"] : v < 1 ? ["Nulo", "#4caf50"] : v < t[0] ? ["Baixo", "#8bc34a"] : v < t[1] ? ["Moderado", "#ffd54f"] : ["Elevado", "#ff5449"];

  /* ---------------- ESTOU BEM ---------------- */
  function imOk(ok) {
    const send = (lat, lon, acc) => {
      const where = lat != null ? `https://maps.google.com/?q=${lat.toFixed(5)},${lon.toFixed(5)}` : "";
      const msg = (ok ? "✅ Estou bem." : "🆘 Preciso de ajuda.") + ` (${TF.format(new Date())})` + (where ? `\nA minha localização${acc ? ` (±${Math.round(acc)} m)` : ""}: ${where}` : "") + "\n— enviado pela Sentinela";
      const enc = encodeURIComponent(msg);
      openSheet(`<h3>${ok ? "Avisar que estou bem" : "Pedir ajuda"}</h3><p style="white-space:pre-line;background:var(--s2);border-radius:12px;padding:12px;color:var(--fg)">${esc(msg)}</p>
        <div class="row"><a class="btn y" href="https://wa.me/?text=${enc}" target="_blank" rel="noopener">WhatsApp</a><a class="btn" href="sms:?body=${enc}">SMS</a><button class="btn" id="okShare">Partilhar / copiar</button></div>
        ${ok ? "" : `<p class="small">Em perigo imediato ligue <b style="color:var(--y)">112</b>.</p>`}<p class="small muted">Escolhe o contacto ou grupo no WhatsApp. Nada é enviado sem confirmares.</p>`);
      $("#okShare").onclick = () => shareIt(ok ? "Estou bem" : "Preciso de ajuda", msg, "");
    };
    toast("A obter localização…");
    if (!navigator.geolocation) return send(cfg.me.lat, cfg.me.lon);
    navigator.geolocation.getCurrentPosition(p => send(p.coords.latitude, p.coords.longitude, p.coords.accuracy), () => send(cfg.gps ? cfg.me.lat : null, cfg.gps ? cfg.me.lon : null), { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
  }

  /* ---------------- SEPARADOR EXTRA ---------------- */
  const renderExtra = safe(function () {
    const v = $("#v-extra"); if (!v || v.hidden) return;
    const h = here();
    let html = `<div class="ptitle"><h1>Extra</h1></div>
      <div class="w"><div class="wh"><h2>Estou bem</h2></div><div class="small muted">Numa emergência, avise a família num toque, com a sua localização.</div>
        <div class="row" style="display:flex;gap:8px"><button class="btn y" style="flex:1" id="btOk">${ic(P.check, "i s")}Estou bem</button><button class="btn" style="flex:1;background:#93000a;color:#ffdad6" id="btHelp">${ic(P.alert, "i s")}Preciso de ajuda</button></div></div>`;
    // Fogos por satélite
    html += `<div class="w"><div class="wh"><h2>Fogos por satélite</h2>${X.firms ? `<button class="lnk" id="firmsMap">Ver no mapa</button>` : ""}</div>`;
    if (!NATIVE && !X.firms) html += `<div class="empty">Disponível na app Android (a NASA não permite a leitura a partir do browser).</div>`;
    else if (!X.firms) html += X.firmsErr ? `<div class="err">A NASA não respondeu agora.</div>` : `<div class="empty">A carregar…</div>`;
    else {
      const near = X.firms.map(f => ({ ...f, d: km(h.lat, h.lon, f.lat, f.lon) })).filter(f => f.d <= 50).sort((a, b) => a.d - b.d);
      html += `<div class="small muted">${X.firms.length} focos de calor em Portugal continental nas últimas 24 h · ${near.length} a menos de 50 km de ${esc(h.name)}</div>` +
        near.slice(0, 6).map((f, i) => `<button class="act" data-f="${i}"><span class="ai" style="--ac:#ff7043;--ac-bg:#3b1d14">${ic(P.fire)}</span><span class="am"><span class="t">Foco de calor</span><span class="m">${fDT(f.time)} · ${num(f.frp, 1)} MW</span></span><span class="ar">${fKm(f.d)}</span></button>`).join("") +
        `<div class="small muted">Satélites NOAA-20 e Suomi NPP (NASA FIRMS). Inclui queimadas e fontes de calor industriais; não é uma ocorrência confirmada.</div>`;
      X._near = near;
    }
    html += `</div>`;
    // Pólen
    html += `<div class="w"><div class="wh"><h2>Pólen</h2><span class="small muted">${esc(h.name)}</span></div>`;
    const pc = X.pollen && X.pollen.current;
    if (!pc) html += X.pollenErr ? `<div class="err">Sem dados de pólen agora.</div>` : `<div class="empty">A carregar…</div>`;
    else {
      html += `<div class="env">` + POL.map(([k, n, t]) => { const [lb, c] = polLevel(pc[k], t); return `<div><span class="k">${n}</span><span class="v">${pc[k] == null ? "—" : Math.round(pc[k])}</span><span class="q" style="--qc:${c}">${lb}</span></div>`; }).join("") + `</div>
        <div class="small muted">Grãos por m³, previsão do modelo europeu CAMS (Open-Meteo). Níveis aproximados. Fora da época de floração os valores ficam perto de zero.</div>`;
    }
    html += `</div>`;
    // Telegram e widget
    html += `<div class="w"><div class="wh"><h2>Alertas no Telegram</h2></div>
      <div class="small muted">Avisos com a app fechada e o telemóvel bloqueado: avisos do IPMA, ocorrências da Proteção Civil, sismos e focos de calor perto dos locais configurados. Verifica de 5 em 5 minutos (o GitHub pode atrasar alguns minutos).</div>
      <ol class="small" style="margin:0;padding-left:18px;display:flex;flex-direction:column;gap:4px;color:var(--fg2)">
        <li>No Telegram, fale com <b>@BotFather</b>, envie <b>/newbot</b> e copie o código (token) que ele dá.</li>
        <li>No GitHub: repositório <b>ricktau86-ai.github.io</b> → Settings → Secrets and variables → Actions → New repository secret. Nome <b>TELEGRAM_TOKEN</b>, valor: o token.</li>
        <li>Envie "olá" ao seu bot novo. Em até 5 minutos chega "Sentinela ligada".</li></ol>
      <a class="small" href="https://github.com/ricktau86-ai/ricktau86-ai.github.io/settings/secrets/actions/new" target="_blank" rel="noopener">Abrir a página dos segredos no GitHub</a>
      <div class="small muted">Os locais vigiados estão no ficheiro sentinela/alertas/config.json (Leiria por defeito).</div></div>
      <div class="w"><div class="wh"><h2>Widget no ecrã inicial</h2></div>
      <div class="small muted">${NATIVE ? `Toque sem largar no ecrã inicial → Widgets → Sentinela. Mostra o aviso mais grave do IPMA para ${esc(areaName(nearestArea(h.lat, h.lon)))} e a temperatura, e atualiza de 30 em 30 minutos mesmo com a app fechada. Avisos laranja e vermelhos novos geram notificação.` : "Disponível na app Android."}</div>
      ${NATIVE && window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SentinelaWidget ? `<button class="btn o" id="wRefresh">Atualizar widget agora</button>` : ""}
      <div class="small muted">Em telemóveis Xiaomi ative "Início automático" e tire a Sentinela da poupança de bateria, senão o sistema pode parar a verificação em segundo plano.</div></div>
      <div class="foot">Fontes: NASA FIRMS, CAMS/Open-Meteo, IPMA, Proteção Civil.</div>`;
    v.innerHTML = html;
    $("#btOk").onclick = () => imOk(true); $("#btHelp").onclick = () => imOk(false);
    const fm = $("#firmsMap"); if (fm) fm.onclick = showFirmsOnMap;
    v.querySelectorAll("[data-f]").forEach(b => b.onclick = () => sheetFirms(X._near[+b.dataset.f]));
    const wr = $("#wRefresh"); if (wr) wr.onclick = () => { pushWidget(true); toast("Widget a atualizar"); };
  });

  /* ---------------- WIDGET: passa o local à parte nativa ---------------- */
  const pushWidget = safe(function (force) {
    const pl = NATIVE && window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SentinelaWidget; if (!pl) return;
    const m = cfg.me; const area = nearestArea(m.lat, m.lon);
    const sig = [m.lat.toFixed(3), m.lon.toFixed(3), area, cfg.nAlerts].join("|");
    if (!force && sig === X.lastWidget) return; X.lastWidget = sig;
    pl.save({ lat: m.lat, lon: m.lon, name: m.name || "Sentinela", area, areaName: areaName(area), notify: !!cfg.nAlerts }).catch(() => { });
  });

  /* ---------------- ciclo ---------------- */
  async function tick() {
    const jobs = [loadPollen().then(() => X.pollenErr = null).catch(e => X.pollenErr = e)];
    if (NATIVE) jobs.push(loadFirms().catch(e => X.firmsErr = e));
    await Promise.all(jobs);
    renderExtra(); renderRoads(); pushWidget(false);
  }
  window.extraShow = safe(function (v) { if (v === "estradas") renderRoads(); if (v === "extra") { renderExtra(); if (!X.pollen) tick(); } });
  setTimeout(tick, 2500);
  setInterval(tick, 10 * 60e3);
  setInterval(() => { renderRoads(); }, 30e3);
})();

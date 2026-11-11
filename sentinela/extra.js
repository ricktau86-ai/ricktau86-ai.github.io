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

  /* ---------------- ESTOU BEM / PRECISO DE AJUDA ---------------- */
  const ESTADOS = {
    ok: [["ok", "✅", "Estou bem"], ["semluz", "🔦", "Estou bem, mas estou sem luz"], ["semrede", "📵", "Estou bem, mas com pouca rede (posso demorar a responder)"],
      ["caminho", "🚗", "Estou a caminho de casa"], ["seguro", "🏠", "Estou num local seguro"], ["evacuei", "🚶", "Saí de casa por precaução (evacuação)"]],
    help: [["ajuda", "🆘", "Preciso de ajuda"], ["ferido", "🩹", "Estou ferido"], ["preso", "🚧", "Estou preso / não consigo sair do local"],
      ["transporte", "🚙", "Preciso de transporte"], ["medicacao", "💊", "Preciso de medicação ou material médico"], ["agua", "💧", "Estou sem água ou comida"]]
  };
  const getContacts = () => { try { return JSON.parse(localStorage.getItem("sent:contactos") || "[]"); } catch (e) { return []; } };
  const setContacts = (c) => { try { localStorage.setItem("sent:contactos", JSON.stringify(c)); } catch (e) { } };
  const waNum = (t) => { let d = String(t).replace(/\D/g, ""); if (d.startsWith("00")) d = d.slice(2); if (d.length === 9) d = "351" + d; return d; };
  const smsNum = (t) => String(t).replace(/[^\d+]/g, "");

  function imOk(ok) {
    const st = ok ? ESTADOS.ok : ESTADOS.help;
    openSheet(`<h3>${ok ? "Avisar que estou bem" : "Pedir ajuda"}</h3>
      <div class="group" style="margin:8px 0">${st.map(([k, e, t], i) => `<label class="item" style="cursor:pointer"><input type="radio" name="eb" value="${k}" ${i ? "" : "checked"} style="accent-color:var(--y);width:20px;height:20px"><span class="it"><b>${e} ${esc(t)}</b></span></label>`).join("")}</div>
      <div class="w" style="padding:12px;gap:8px">
        <label class="small" for="ebPess">Pessoas comigo</label><select class="sel" id="ebPess" style="max-width:100%"><option value="">Não indicar</option><option>Estou sozinho</option>${[2, 3, 4, 5, 6].map(n => `<option>Somos ${n}</option>`).join("")}<option>Somos mais de 6</option></select>
        <label class="small" for="ebNota">Nota (opcional)</label><input class="inp" id="ebNota" maxlength="200" placeholder="${ok ? "Ex.: a Leonor está comigo" : "Ex.: 2.º andar, porta das traseiras"}">
        <label class="item" style="padding:4px 0;cursor:pointer;border:0"><input type="checkbox" id="ebLoc" checked style="accent-color:var(--y);width:20px;height:20px"><span class="it"><b style="font-size:14px">Incluir a minha localização</b></span></label>
      </div>
      <button class="btn y full" id="ebGo">Preparar mensagem</button>
      ${ok ? "" : `<a class="btn full" href="tel:112" style="margin-top:8px;background:#93000a;color:#ffdad6">Ligar 112</a>`}`);
    $("#ebGo").onclick = () => {
      const sel = (document.querySelector('input[name="eb"]:checked') || {}).value || st[0][0];
      const [, e, t] = st.find(x => x[0] === sel); const pess = $("#ebPess").value, nota = $("#ebNota").value.trim(), withLoc = $("#ebLoc").checked;
      toast(withLoc ? "A obter localização…" : "A preparar…");
      const build = async (lat, lon, acc) => {
        let perto = "";
        if (lat != null) { try { const c = new AbortController(); setTimeout(() => c.abort(), 3500); const r = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}&zoom=16&accept-language=pt`, { signal: c.signal }); const j = await r.json(); const a = j.address || {}; perto = [a.road, a.village || a.town || a.city || a.suburb].filter(Boolean).join(", "); } catch (x) { } }
        let bat = ""; try { if (navigator.getBattery) { const b = await navigator.getBattery(); bat = Math.round(b.level * 100) + "%" + (b.charging ? " (a carregar)" : ""); } } catch (x) { }
        const lines = [`${e} ${t}.`];
        if (pess) lines.push(pess + ".");
        if (nota) lines.push(nota);
        if (lat != null) lines.push(`📍 ${perto ? perto + " — " : ""}https://maps.google.com/?q=${lat.toFixed(5)},${lon.toFixed(5)}${acc ? ` (±${Math.round(acc)} m)` : ""}`);
        lines.push(`🕒 ${new Intl.DateTimeFormat("pt-PT", { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date())}${bat ? ` · 🔋 ${bat}` : ""}`);
        lines.push("— enviado pela Sentinela");
        showSend(lines.join("\n"), ok);
      };
      if (!withLoc || !navigator.geolocation) return build(null, null);
      navigator.geolocation.getCurrentPosition(p => build(p.coords.latitude, p.coords.longitude, p.coords.accuracy), () => build(cfg.gps ? cfg.me.lat : null, cfg.gps ? cfg.me.lon : null), { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
    };
  }
  function showSend(msg, ok) {
    const enc = encodeURIComponent(msg); const cs = getContacts();
    openSheet(`<h3>${ok ? "Avisar que estou bem" : "Pedir ajuda"}</h3><p style="white-space:pre-line;background:var(--s2);border-radius:12px;padding:12px;color:var(--fg);font-size:14px">${esc(msg)}</p>
      ${cs.length ? `<div class="gtitle" style="margin:6px 0">Os meus contactos</div><div class="group">` + cs.map(c => `<div class="item" style="cursor:default"><span class="it"><b>${esc(c.nome)}</b><span class="mono">${esc(c.tel)}</span></span>
        <a class="btn y" style="padding:8px 12px" href="https://wa.me/${waNum(c.tel)}?text=${enc}" target="_blank" rel="noopener">WhatsApp</a><a class="btn" style="padding:8px 12px" href="sms:${smsNum(c.tel)}?body=${enc}">SMS</a></div>`).join("") + `</div>
        ${cs.length > 1 ? `<a class="btn full" style="margin-top:8px" href="sms:${cs.map(c => smsNum(c.tel)).join(",")}?body=${enc}">SMS para todos (${cs.length})</a>` : ""}` : ""}
      <div class="row"><a class="btn${cs.length ? "" : " y"}" href="https://wa.me/?text=${enc}" target="_blank" rel="noopener">WhatsApp (escolher)</a><a class="btn" href="sms:?body=${enc}">SMS (escolher)</a><button class="btn" id="okShare">Partilhar / copiar</button></div>
      ${ok ? "" : `<p class="small">Em perigo imediato ligue <a href="tel:112" style="color:var(--y);font-weight:700">112</a>.</p>`}
      <p class="small muted">${cs.length ? "" : "Pode guardar contactos de emergência em Extra → Contactos de emergência. "}Nada é enviado sem confirmar no WhatsApp ou nas mensagens.</p>`);
    $("#okShare").onclick = () => shareIt(ok ? "Estou bem" : "Preciso de ajuda", msg, "");
  }
  function editContacts() {
    const draw = () => {
      const cs = getContacts();
      openSheet(`<h3>Contactos de emergência</h3><p class="small">Ficam só neste telemóvel (e na cópia de segurança). Servem para enviar o "Estou bem" num toque.</p>
        <div class="group">${cs.length ? cs.map((c, i) => `<div class="item" style="cursor:default"><span class="it"><b>${esc(c.nome)}</b><span class="mono">${esc(c.tel)}</span></span><button class="btn" style="padding:8px 10px" data-del="${i}" aria-label="Apagar">${ic(P.trash, "i s")}</button></div>`).join("") : `<div class="item" style="cursor:default"><span class="it"><span>Ainda não há contactos.</span></span></div>`}</div>
        <div class="w" style="padding:12px;gap:8px;margin-top:10px"><input class="inp" id="ctNome" placeholder="Nome (ex.: Tânia)" maxlength="40"><input class="inp" id="ctTel" type="tel" placeholder="Telemóvel (ex.: 912 345 678)" maxlength="20"><button class="btn y" id="ctAdd">Adicionar</button></div>`);
      document.querySelectorAll("#sheet [data-del]").forEach(b => b.onclick = () => { const c = getContacts(); c.splice(+b.dataset.del, 1); setContacts(c); draw(); renderExtra(); });
      $("#ctAdd").onclick = () => { const n = $("#ctNome").value.trim(), t = $("#ctTel").value.trim(); if (!n || t.replace(/\D/g, "").length < 9) { toast("Indique o nome e um número válido"); return; } const c = getContacts(); c.push({ nome: n, tel: t }); setContacts(c); draw(); renderExtra(); };
    };
    draw();
  }

  /* ---------------- CÓPIA DE SEGURANÇA ---------------- */
  const SKIP = /^sent:(c:|news:|tData$|seen|seenInit$|procivLayers$)/;
  function backupData() {
    const data = {};
    for (let i = 0; i < localStorage.length; i++) { const k = localStorage.key(i); if (k && k.startsWith("sent:") && !SKIP.test(k)) data[k] = localStorage.getItem(k); }
    return { app: "Sentinela", versao: 1, criado: new Date().toISOString(), dados: data };
  }
  async function doBackup() {
    const json = JSON.stringify(backupData());
    const name = `sentinela-copia-${new Date().toISOString().slice(0, 10)}.json`;
    const Pl = window.Capacitor && window.Capacitor.Plugins;
    if (NATIVE && Pl && Pl.Filesystem && Pl.Share) {
      try {
        const w = await Pl.Filesystem.writeFile({ path: name, data: json, directory: "CACHE", encoding: "utf8" });
        await Pl.Share.share({ title: "Cópia de segurança da Sentinela", text: "Cópia de segurança da Sentinela", files: [w.uri], dialogTitle: "Guardar a cópia (Drive, email, WhatsApp…)" });
        return;
      } catch (e) { if (/cancel/i.test(String(e && e.message))) return; }
    }
    showCode(json);
  }
  function showCode(json) {
    const code = btoa(unescape(encodeURIComponent(json)));
    openSheet(`<h3>Código da cópia de segurança</h3><p class="small">Copie este código e guarde-o (por exemplo, envie-o para si por email ou WhatsApp). Para recuperar, cole-o em "Restaurar".</p>
      <textarea class="inp" id="bkCode" readonly style="height:140px;font-family:var(--mono);font-size:11px">${esc(code)}</textarea>
      <div class="row"><button class="btn y" id="bkCopy">Copiar código</button></div>`);
    $("#bkCopy").onclick = async () => { try { await navigator.clipboard.writeText(code); toast("Código copiado"); } catch (e) { const t = $("#bkCode"); t.focus(); t.select(); toast("Selecione e copie o código"); } };
  }
  function restoreFrom(text) {
    let obj = null; text = String(text || "").trim();
    try { obj = JSON.parse(text); } catch (e) { try { obj = JSON.parse(decodeURIComponent(escape(atob(text.replace(/\s/g, ""))))); } catch (x) { } }
    if (!obj || obj.app !== "Sentinela" || !obj.dados) { toast("Este ficheiro ou código não é uma cópia da Sentinela"); return; }
    const n = Object.keys(obj.dados).length;
    openSheet(`<h3>Restaurar a cópia?</h3><p>Cópia de ${fDT(new Date(obj.criado))} com ${n} conjuntos de dados (definições, cidades seguidas, contactos, kit, favoritos…). Substitui os dados atuais deste telemóvel.</p>
      <div class="row"><button class="btn" id="rsNo">Cancelar</button><button class="btn y" id="rsYes">Restaurar</button></div>`);
    $("#rsNo").onclick = closeSheet;
    $("#rsYes").onclick = () => { try { Object.entries(obj.dados).forEach(([k, v]) => { if (k.startsWith("sent:")) localStorage.setItem(k, v); }); } catch (e) { } toast("Cópia restaurada. A reiniciar…"); setTimeout(() => location.reload(), 900); };
  }
  function doRestore() {
    openSheet(`<h3>Restaurar cópia de segurança</h3><p class="small">Escolha o ficheiro <b>sentinela-copia-….json</b> que guardou, ou cole o código.</p>
      <input type="file" id="rsFile" accept=".json,application/json,text/plain" class="inp">
      <textarea class="inp" id="rsCode" placeholder="…ou cole aqui o código" style="height:100px;margin-top:8px;font-family:var(--mono);font-size:11px"></textarea>
      <div class="row"><button class="btn y" id="rsGo">Restaurar código colado</button></div>`);
    $("#rsFile").onchange = (e) => { const f = e.target.files && e.target.files[0]; if (!f) return; const r = new FileReader(); r.onload = () => restoreFrom(r.result); r.readAsText(f); };
    $("#rsGo").onclick = () => restoreFrom($("#rsCode").value);
  }

  /* ---------------- SEPARADOR EXTRA ---------------- */
  const renderExtra = safe(function () {
    const v = $("#v-extra"); if (!v || v.hidden) return;
    const h = here();
    let html = `<div class="ptitle"><h1>Extra</h1></div>
      <div class="w"><div class="wh"><h2>Estou bem</h2><button class="lnk" id="btCt">Contactos (${getContacts().length})</button></div><div class="small muted">Numa emergência, avise a família num toque: estado, localização, hora e bateria.</div>
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
    // Cópia de segurança
    html += `<div class="w"><div class="wh"><h2>Cópia de segurança</h2></div>
      <div class="small muted">Guarde as definições, cidades seguidas, contactos de emergência, kit, favoritos e abrigos offline. Use para recuperar tudo depois de reinstalar a app ou mudar de telemóvel.</div>
      <div class="row" style="display:flex;gap:8px"><button class="btn y" style="flex:1" id="bkSave">${ic(P.dl, "i s")}Guardar cópia</button><button class="btn" style="flex:1" id="bkLoad">Restaurar</button></div>
      <div class="small muted">A cópia fica onde escolher (Google Drive, email, WhatsApp…). Não é enviada para nenhum servidor da Sentinela.</div></div>`;
    // Widget
    html += `<div class="w"><div class="wh"><h2>Widget no ecrã inicial</h2></div>
      <div class="small muted">${NATIVE ? `Toque sem largar no ecrã inicial → Widgets → Sentinela. Mostra o aviso mais grave do IPMA para ${esc(areaName(nearestArea(h.lat, h.lon)))} e a temperatura, e atualiza de 30 em 30 minutos mesmo com a app fechada. Avisos laranja e vermelhos novos geram notificação.` : "Disponível na app Android."}</div>
      ${NATIVE && window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SentinelaWidget ? `<button class="btn o" id="wRefresh">Atualizar widget agora</button>` : ""}
      <div class="small muted">Em telemóveis Xiaomi ative "Início automático" e tire a Sentinela da poupança de bateria, senão o sistema pode parar a verificação em segundo plano.</div></div>
      <div class="foot">Fontes: NASA FIRMS, CAMS/Open-Meteo, IPMA, Proteção Civil.</div>`;
    v.innerHTML = html;
    $("#btOk").onclick = () => imOk(true); $("#btHelp").onclick = () => imOk(false);
    $("#btCt").onclick = editContacts; $("#bkSave").onclick = doBackup; $("#bkLoad").onclick = doRestore;
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

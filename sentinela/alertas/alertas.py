#!/usr/bin/env python3
"""Sentinela — alertas para o Telegram (corre no GitHub Actions de 5 em 5 minutos).
Fontes: IPMA (avisos e sismos), Proteção Civil (camada pública OcorrenciasSite), NASA FIRMS (focos de calor).
Precisa do segredo TELEGRAM_TOKEN. O chat é descoberto sozinho: basta enviar uma mensagem ao bot."""
import json, math, os, sys, time, html, csv, io, urllib.request, urllib.parse
from datetime import datetime, timezone, timedelta

AQUI = os.path.dirname(os.path.abspath(__file__))
CFG = json.load(open(os.path.join(AQUI, "config.json"), encoding="utf-8"))
ESTADO = sys.argv[1] if len(sys.argv) > 1 else "estado.json"
TOKEN = os.environ.get("TELEGRAM_TOKEN", "").strip()
UA = {"User-Agent": "Sentinela/1.0 (+https://ricktau86-ai.github.io/sentinela/)"}
LVL = {"green": 0, "yellow": 1, "orange": 2, "red": 3}
LVN = {"yellow": "AMARELO", "orange": "LARANJA", "red": "VERMELHO"}
LVE = {"yellow": "🟡", "orange": "🟠", "red": "🔴"}
AREAS = {"VCT":("Viana do Castelo",41.693,-8.832),"BRG":("Braga",41.545,-8.427),"VRL":("Vila Real",41.300,-7.745),"BGC":("Bragança",41.806,-6.757),
 "PTO":("Porto",41.150,-8.611),"AVR":("Aveiro",40.640,-8.654),"VIS":("Viseu",40.657,-7.913),"GDA":("Guarda",40.537,-7.268),"CBR":("Coimbra",40.203,-8.410),
 "CBO":("Castelo Branco",39.822,-7.491),"LRA":("Leiria",39.744,-8.807),"STM":("Santarém",39.236,-8.686),"PTG":("Portalegre",39.297,-7.430),"LSB":("Lisboa",38.722,-9.139),
 "STB":("Setúbal",38.524,-8.893),"EVR":("Évora",38.571,-7.909),"BJA":("Beja",38.015,-7.863),"FAR":("Faro",37.019,-7.930),
 "MCN":("Madeira – Costa Norte",32.83,-16.95),"MCS":("Madeira – Costa Sul",32.65,-16.92),"MRM":("Madeira – Regiões Montanhosas",32.75,-16.97),"MPS":("Porto Santo",33.07,-16.34),
 "AOR":("Açores – Grupo Oriental",37.75,-25.67),"ACE":("Açores – Grupo Central",38.65,-27.22),"AOC":("Açores – Grupo Ocidental",39.45,-31.13)}
LX = timezone(timedelta(hours=0))  # Portugal continental; a hora de verão é tratada pela própria fonte
DIAS = ["seg", "ter", "qua", "qui", "sex", "sáb", "dom"]

def km(a, b, c, d):
    r = math.pi / 180; x = (c - a) * r; y = (d - b) * r
    h = math.sin(x/2)**2 + math.cos(a*r)*math.cos(c*r)*math.sin(y/2)**2
    return 2 * 6371 * math.asin(math.sqrt(h))

def get(url, raw=False):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=40) as r:
        b = r.read().decode("utf-8", "replace")
    return b if raw else json.loads(b)

def tg(method, **params):
    data = urllib.parse.urlencode(params).encode()
    req = urllib.request.Request(f"https://api.telegram.org/bot{TOKEN}/{method}", data=data)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read())

def fdt(s):
    try:
        d = datetime.fromisoformat(s)
        return f"{DIAS[d.weekday()]} {d.day}/{d.month} {d:%H:%M}"
    except Exception:
        return s

def area_de(lat, lon):
    return min(AREAS, key=lambda k: km(lat, lon, AREAS[k][1], AREAS[k][2]))

def mais_perto(lat, lon):
    l = min(CFG["locais"], key=lambda p: km(lat, lon, p["lat"], p["lon"]))
    return l, km(lat, lon, l["lat"], l["lon"])

def eventos():
    ev, falhas = [], []
    agora = datetime.now()
    # 1) Avisos IPMA
    try:
        areas = {area_de(p["lat"], p["lon"]) for p in CFG["locais"]}
        for w in get("https://api.ipma.pt/open-data/forecast/warnings/warnings_www.json"):
            lv = w.get("awarenessLevelID")
            if w.get("idAreaAviso") not in areas or LVL.get(lv, 0) < LVL[CFG.get("ipma_nivel_minimo", "yellow")]: continue
            try:
                if datetime.fromisoformat(w["endTime"]) < agora: continue
            except Exception: pass
            k = f"w|{w['idAreaAviso']}|{w['awarenessTypeName']}|{lv}|{w['startTime']}"
            txt = (f"{LVE[lv]} <b>Aviso {LVN[lv]} — {html.escape(w['awarenessTypeName'])}</b>\n"
                   f"{html.escape(AREAS[w['idAreaAviso']][0])} · {fdt(w['startTime'])} → {fdt(w['endTime'])}\n"
                   + (f"{html.escape(w.get('text') or '')}\n" if w.get("text") else "") + "<i>Fonte: IPMA</i>")
            ev.append((k, txt, LVL[lv]))
    except Exception as e: falhas.append(f"IPMA avisos: {e}")
    # 2) Ocorrências Proteção Civil
    try:
        q = "https://services-eu1.arcgis.com/VlrHb7fn5ewYhX6y/arcgis/rest/services/OcorrenciasSite/FeatureServer/0/query?where=1%3D1&outFields=*&returnGeometry=false&resultRecordCount=2000&f=json"
        lim = CFG.get("ocorrencias_ignorar_mais_de_dias", 3) * 1440
        for f in get(q).get("features", []):
            a = f["attributes"]; lat, lon = a.get("Latitude"), a.get("Longitude")
            if lat is None or (a.get("DuracaoMinutos") or 0) > lim: continue
            loc, d = mais_perto(lat, lon)
            if d > CFG.get("ocorrencias_raio_km", 15): continue
            nat = str(a.get("Natureza") or "Ocorrência").split(" - ", 1)[-1]
            ic = "🔥" if "inc" in (a.get("RASI") or "").lower() or "incênd" in nat.lower() or "mato" in nat.lower() or "povoamento" in nat.lower() else "🚨"
            k = f"a|{a.get('Numero')}"
            txt = (f"{ic} <b>{html.escape(nat)}</b> — {d:.1f} km de {html.escape(loc['nome'])}\n"
                   f"{html.escape(', '.join(x for x in [a.get('Localidade'), a.get('Freguesia'), a.get('Concelho')] if x))}\n"
                   f"{html.escape(a.get('EstadoOcorrencia') or '')} · {a.get('Operacionais') or 0} operacionais · {a.get('MeiosTerrestres') or 0} terrestres · {a.get('MeiosAereos') or 0} aéreos\n"
                   f"<a href=\"https://maps.google.com/?q={lat},{lon}\">Ver no mapa</a> · <i>Fonte: Proteção Civil</i>")
            ev.append((k, txt, 2 if (a.get("MeiosAereos") or 0) > 0 else 1))
    except Exception as e: falhas.append(f"Proteção Civil: {e}")
    # 3) Sismos IPMA
    try:
        for area in (7, 3):
            for s in get(f"https://api.ipma.pt/open-data/observation/seismic/{area}.json").get("data", []):
                try: mag = float(s.get("magnitud")); lat = float(s["lat"]); lon = float(s["lon"]); t = datetime.fromisoformat(s["time"])
                except Exception: continue
                if mag < CFG.get("sismo_magnitude_minima", 3) or agora - t > timedelta(hours=24): continue
                loc, d = mais_perto(lat, lon)
                if d > CFG.get("sismo_raio_km", 150): continue
                k = f"q|{s['time']}|{s['lat']}"
                txt = (f"🌍 <b>Sismo de magnitude {mag:.1f}</b> — {html.escape(s.get('obsRegion') or '')}\n"
                       f"{fdt(s['time'])} · {d:.0f} km de {html.escape(loc['nome'])} · {s.get('depth')} km de profundidade"
                       + (f"\nSentido: {html.escape(str(s['degree']))}" if s.get("degree") else "") + "\n<i>Fonte: IPMA</i>")
                ev.append((k, txt, 2 if mag >= 4.5 else 1))
    except Exception as e: falhas.append(f"IPMA sismos: {e}")
    # 4) Focos de calor por satélite (NASA FIRMS)
    try:
        R = CFG.get("fogos_satelite_raio_km", 20)
        for url in ["https://firms.modaps.eosdis.nasa.gov/data/active_fire/noaa-20-viirs-c2/csv/J1_VIIRS_C2_Europe_24h.csv",
                    "https://firms.modaps.eosdis.nasa.gov/data/active_fire/suomi-npp-viirs-c2/csv/SUOMI_VIIRS_C2_Europe_24h.csv"]:
            for r in csv.DictReader(io.StringIO(get(url, raw=True))):
                try: lat = float(r["latitude"]); lon = float(r["longitude"])
                except Exception: continue
                if not (36.8 < lat < 42.3 and -9.7 < lon < -6.1) or r.get("confidence") == "low": continue
                loc, d = mais_perto(lat, lon)
                if d > R: continue
                k = f"f|{round(lat,2)}|{round(lon,2)}|{r['acq_date']}"
                txt = (f"🛰️ <b>Foco de calor detetado por satélite</b> — {d:.1f} km de {html.escape(loc['nome'])}\n"
                       f"{r['acq_date']} {r['acq_time'][:2]}:{r['acq_time'][2:]} UTC · pode ser um incêndio ou uma fonte de calor industrial\n"
                       f"<a href=\"https://maps.google.com/?q={lat},{lon}\">Ver no mapa</a> · <i>Fonte: NASA FIRMS</i>")
                ev.append((k, txt, 1))
    except Exception as e: falhas.append(f"NASA FIRMS: {e}")
    return ev, falhas

def main():
    if not TOKEN:
        print("Falta o segredo TELEGRAM_TOKEN — alertas desligados."); return
    est = {"seen": {}, "init": False}
    if os.path.exists(ESTADO):
        try: est.update(json.load(open(ESTADO)))
        except Exception: pass
    chat = os.environ.get("TELEGRAM_CHAT_ID", "").strip() or est.get("chat_id")
    if not chat:
        ups = tg("getUpdates").get("result", [])
        msgs = [u.get("message") or u.get("channel_post") for u in ups if (u.get("message") or u.get("channel_post"))]
        if not msgs:
            print("Ainda ninguém escreveu ao bot. Envie uma mensagem ao bot no Telegram."); json.dump(est, open(ESTADO, "w")); return
        chat = str(msgs[-1]["chat"]["id"]); est["chat_id"] = chat
        nomes = ", ".join(p["nome"] for p in CFG["locais"])
        tg("sendMessage", chat_id=chat, parse_mode="HTML", text=f"✅ <b>Sentinela ligada</b>\nVou avisar aqui sobre avisos do IPMA, ocorrências da Proteção Civil, sismos e focos de calor perto de: {html.escape(nomes)}.")
    ev, falhas = eventos()
    for f in falhas: print("Falha:", f)
    agora = int(time.time())
    novos = [e for e in ev if e[0] not in est["seen"]]
    if not est.get("init"):
        # primeira vez: só um resumo dos avisos em vigor, sem inundar o chat
        avisos = [e for e in novos if e[0].startswith("w|")]
        if avisos:
            tg("sendMessage", chat_id=chat, parse_mode="HTML", disable_web_page_preview="true", text="📋 <b>Avisos em vigor ou previstos</b>\n\n" + "\n\n".join(e[1] for e in avisos)[:3900])
        for e in ev: est["seen"][e[0]] = agora
        est["init"] = True
    else:
        for k, txt, _ in sorted(novos, key=lambda e: -e[2])[:15]:
            try:
                tg("sendMessage", chat_id=chat, parse_mode="HTML", disable_web_page_preview="true", text=txt)
                est["seen"][k] = agora
            except Exception as e: print("Envio falhou:", e)
    est["seen"] = {k: t for k, t in est["seen"].items() if agora - t < 15 * 86400}
    est["ultima"] = datetime.now(timezone.utc).isoformat()
    json.dump(est, open(ESTADO, "w"), ensure_ascii=False)
    print(f"{len(ev)} eventos, {len(novos)} novos, {len(falhas)} falhas")

if __name__ == "__main__":
    main()

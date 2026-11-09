#!/usr/bin/env python3
"""Sentinela — recolhe as ocorrências publicadas pela Proteção Civil (geoPORTAL ANEPC,
o mapa da página prociv.gov.pt/pt/ocorrencias) e grava-as num JSON normalizado."""
import json, re, sys, os, time, urllib.request, urllib.parse
from datetime import datetime, timezone

PORTAL = "https://prociv-portal.geomai.mai.gov.pt/arcgis"
SERVER = "https://prociv-agserver.geomai.mai.gov.pt/arcgis/rest/services"
APP = "29e83f11f7a34339b35364e483e3846f"
UA = {"User-Agent": "Sentinela/1.0 (+https://ricktau86-ai.github.io/sentinela/)", "Referer": "https://prociv.gov.pt/"}
DEBUG = os.environ.get("DEBUG") == "1"

def get(url, params=None):
    if params: url += ("&" if "?" in url else "?") + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=40) as r:
        return json.loads(r.read().decode("utf-8", "replace"))

def log(*a):
    print(*a, file=sys.stderr, flush=True)

def norm_layer(u):
    u = u.rstrip("/")
    return u if re.search(r"/\d+$", u) else u + "/0"

def discover():
    layers, items = set(), set()
    def scan(o):
        s = json.dumps(o)
        for m in re.findall(r'https?://[^"\\ ]+?/(?:FeatureServer|MapServer)(?:/\d+)?', s): layers.add(m)
        for m in re.findall(r'"itemId"\s*:\s*"([0-9a-f]{32})"', s): items.add(m)
    try:
        d = get(f"{PORTAL}/sharing/rest/content/items/{APP}/data", {"f": "json"}); scan(d); log("app ok")
    except Exception as e: log("app falhou:", e)
    seen = set()
    while items - seen:
        i = (items - seen).pop(); seen.add(i)
        try:
            info = get(f"{PORTAL}/sharing/rest/content/items/{i}", {"f": "json"})
            log("item", i, info.get("type"), info.get("title"), info.get("url"))
            if info.get("url") and re.search("FeatureServer|MapServer", info["url"]): layers.add(info["url"])
            d = get(f"{PORTAL}/sharing/rest/content/items/{i}/data", {"f": "json"}); scan(d)
        except Exception as e: log("item falhou", i, e)
    expanded = set()
    for u in layers:
        if re.search(r"/\d+$", u): expanded.add(u); continue
        try:
            svc = get(u, {"f": "json"})
            for l in svc.get("layers", []): expanded.add(f"{u.rstrip('/')}/{l['id']}")
        except Exception as e: log("serviço falhou", u, e); expanded.add(norm_layer(u))
    return sorted(expanded)

def pick(a, pattern):
    for k, v in a.items():
        if re.search(pattern, k, re.I) and v not in (None, "", " "): return v
    return None

def to_iso(v):
    if v is None: return None
    if isinstance(v, (int, float)):
        return datetime.fromtimestamp(v / 1000 if v > 1e11 else v, tz=timezone.utc).isoformat()
    s = str(v)
    m = re.match(r"(\d{2})[-/](\d{2})[-/](\d{4})[ T]?(\d{2}:\d{2})?", s)
    if m: return f"{m[3]}-{m[2]}-{m[1]}T{m[4] or '00:00'}:00"
    return s

def fetch_layer(u):
    meta = get(u, {"f": "json"})
    name = meta.get("name", "")
    fields = [f.get("name") for f in meta.get("fields", [])]
    log("camada", u, "|", name, "|", meta.get("geometryType"), "|", fields)
    out, offset = [], 0
    while True:
        d = get(u + "/query", {"where": "1=1", "outFields": "*", "outSR": 4326, "returnGeometry": "true",
                              "resultOffset": offset, "resultRecordCount": 1000, "f": "json"})
        feats = d.get("features", [])
        for f in feats:
            g, a = f.get("geometry") or {}, f.get("attributes") or {}
            lat, lon = g.get("y"), g.get("x")
            if lat is None and "rings" in g:
                pts = [p for r in g["rings"] for p in r]; lon = sum(p[0] for p in pts)/len(pts); lat = sum(p[1] for p in pts)/len(pts)
            if lat is None: lat, lon = pick(a, r"^lat"), pick(a, r"^(lon|lng)")
            try: lat, lon = float(lat), float(lon)
            except Exception: continue
            out.append({"layer": name, "lat": lat, "lon": lon, "attrs": a})
        if DEBUG and feats: log("exemplo", json.dumps(feats[0].get("attributes"), ensure_ascii=False)[:1500])
        if not d.get("exceededTransferLimit") or not feats: break
        offset += len(feats)
    return out, name, fields

def main():
    layers = discover()
    log("camadas encontradas:", len(layers)); [log(" -", l) for l in layers]
    items, used = [], []
    for u in layers:
        try:
            rows, name, fields = fetch_layer(u)
            # só camadas de ocorrências (ignora limites administrativos, meios fixos, etc.)
            looks = re.search(r"ocorr|incid|eventos?", name + " " + u, re.I) or any(re.search(r"natureza", f or "", re.I) for f in fields)
            if not looks: log("  ignorada (não parece ocorrências)"); continue
            used.append({"url": u, "name": name, "count": len(rows), "fields": fields})
            for r in rows:
                a = r["attrs"]
                items.append({
                    "id": str(pick(a, r"^(numero|n_?ocorr|num_?ocorr|objectid|fid|globalid)") or f"{r['lat']:.5f},{r['lon']:.5f}"),
                    "lat": r["lat"], "lon": r["lon"],
                    "natureza": pick(a, r"natureza|descri|tipo"), "estado": pick(a, r"estado|status|fase|situa"),
                    "distrito": pick(a, r"distrito"), "concelho": pick(a, r"concelho|munic"), "freguesia": pick(a, r"freguesia"),
                    "localidade": pick(a, r"localidade|^local$|lugar"),
                    "operacionais": pick(a, r"operac|humanos"), "terrestres": pick(a, r"terrest|veic"), "aereos": pick(a, r"aere|aére"),
                    "inicio": to_iso(pick(a, r"data|hora|inicio|date")), "camada": r["layer"], "attrs": a})
        except Exception as e: log("camada falhou", u, e)
    res = {"updated": datetime.now(timezone.utc).isoformat(), "source": "geoPORTAL ANEPC (prociv.gov.pt/pt/ocorrencias)",
           "layers": used, "count": len(items), "items": items}
    path = sys.argv[1] if len(sys.argv) > 1 else "ocorrencias.json"
    with open(path, "w", encoding="utf-8") as fh: json.dump(res, fh, ensure_ascii=False, default=str)
    log("gravado", path, len(items), "ocorrências")
    if not used: sys.exit(2)

if __name__ == "__main__": main()

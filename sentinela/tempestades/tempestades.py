#!/usr/bin/env python3
"""Sentinela — Tempestades.
Corre no GitHub Actions. Usa os dados abertos do ECMWF (licença CC-BY-4.0):
  * corrida principal (HRES, "oper"): pressão, vento, rajadas e chuva -> mapas animados (PNG) para a app;
  * ensemble (ENS, "enfo", 50 cenários): pressão ao nível do mar -> seguimento do centro das depressões
    em cada cenário, trajetória média, cone com 80% dos cenários e entrada em terra.
Nada é inventado: se não houver uma depressão bem definida, o ficheiro diz isso mesmo.
"""
import json, math, os, sys, time, datetime as dt, urllib.request
import numpy as np
import xarray as xr
from scipy import ndimage
from ecmwf.opendata import Client

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
PREV = sys.argv[2] if len(sys.argv) > 2 else None
os.makedirs(os.path.join(OUT, "frames"), exist_ok=True)
TMP = "/tmp/sent_grib"; os.makedirs(TMP, exist_ok=True)
C = Client(source="ecmwf")

# Domínio dos mapas animados (Atlântico + Península) e do seguimento
MAP_S, MAP_N, MAP_W, MAP_E = 28.0, 52.0, -40.0, 5.0
TRK_S, TRK_N, TRK_W, TRK_E = 22.0, 64.0, -60.0, 15.0
LEIRIA = (39.7436, -8.8071)
PT_CENTER = (39.6, -8.0)

def log(*a): print(*a, flush=True)
def km(a, b, c, d):
    r = math.pi / 180; x = (c - a) * r; y = (d - b) * r
    h = math.sin(x/2)**2 + math.cos(a*r)*math.cos(c*r)*math.sin(y/2)**2
    return 2 * 6371 * math.asin(min(1, math.sqrt(h)))

def fetch(path, **req):
    for t in range(3):
        try:
            r = C.retrieve(target=path, **req); return r.datetime
        except Exception as e:
            log("  tentativa", t + 1, "falhou:", str(e)[:200]); time.sleep(8)
    raise RuntimeError("download falhou: %s" % req)

def open_crop(path, var, s, n, w, e):
    ds = xr.open_dataset(path, engine="cfgrib", backend_kwargs={"indexpath": ""})
    v = ds[var].sel(latitude=slice(n, s), longitude=slice(w, e)).load()
    ds.close()
    return v

# ---------------------------------------------------------------- HRES: mapas animados
def merc(lat): return np.log(np.tan(np.pi/4 + np.radians(lat)/2))

def to_merc_grid(field, lats, H):
    """Reamostra linhas (latitude regular, decrescente) para espaçamento Mercator (para a app sobrepor sem distorção)."""
    lats = np.asarray(lats); yt = np.linspace(merc(MAP_N), merc(MAP_S), H)
    lat_t = np.degrees(2*np.arctan(np.exp(yt)) - np.pi/2)
    idx = (lats[0] - lat_t) / (lats[0] - lats[1])
    i0 = np.clip(np.floor(idx).astype(int), 0, len(lats)-2); f = (idx - i0)[:, None]
    return field[i0] * (1-f) + field[i0+1] * f

def render_frames(run_dt, steps):
    import matplotlib; matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.colors import ListedColormap, BoundaryNorm
    from PIL import Image
    files = {}
    def var(param, stp):
        pth = os.path.join(TMP, f"hres_{param}.grib2"); files[param] = pth
        fetch(pth, type="fc", stream="oper", param=param, step=stp, date=run_dt.strftime("%Y%m%d"), time=run_dt.hour)
        ds = xr.open_dataset(pth, engine="cfgrib", backend_kwargs={"indexpath": ""})
        v = ds[list(ds.data_vars)[0]].sel(latitude=slice(MAP_N, MAP_S), longitude=slice(MAP_W, MAP_E)).load(); ds.close(); return v
    msl, tp = var("msl", steps), var("tp", steps)
    u10, v10 = var("10u", steps), var("10v", steps)
    gust = var("10fg", [s for s in steps if s > 0])
    lats, lons = msl.latitude.values, msl.longitude.values
    W = 1000; H = int(round(W * (merc(MAP_N) - merc(MAP_S)) / math.radians(MAP_E - MAP_W)))
    gust_lv = [0, 40, 55, 70, 85, 100, 115, 130, 300]
    gust_cm = ListedColormap([(0,0,0,0), (0.42,0.75,0.95,.55), (0.98,0.88,0.35,.6), (1,0.62,0.15,.65), (0.95,0.3,0.2,.7), (0.85,0.1,0.45,.72), (0.62,0.2,0.75,.75), (0.95,0.95,0.95,.8)])
    rain_lv = [0, 0.5, 2, 5, 10, 20, 40, 500]
    rain_cm = ListedColormap([(0,0,0,0), (0.55,0.8,1,.5), (0.25,0.6,1,.62), (0.1,0.35,0.95,.7), (0.55,0.25,0.95,.75), (0.9,0.2,0.75,.8), (1,0.4,0.4,.85)])
    x = np.linspace(MAP_W, MAP_E, len(lons)); Ym = merc(lats)
    frames = {"vento": [], "chuva": [], "isobaras": []}; times = []
    steps_avail = [int(s / np.timedelta64(1, "h")) for s in msl.step.values]
    def save(fig, name):
        path = os.path.join(OUT, "frames", name)
        fig.savefig(path, dpi=100, transparent=True); plt.close(fig)
        im = Image.open(path).convert("RGBA"); im.quantize(colors=48, method=Image.Quantize.FASTOCTREE).save(path, optimize=True)
        return name
    def blank():
        fig = plt.figure(figsize=(W/100, H/100), dpi=100); ax = fig.add_axes([0, 0, 1, 1]); ax.set_axis_off()
        return fig, ax
    prev_tp = None
    for i, st in enumerate(steps_avail):
        vt = (run_dt + dt.timedelta(hours=st)).replace(tzinfo=dt.timezone.utc)
        times.append(vt.isoformat())
        # rajadas (km/h) — no passo 0 não há rajada; usa o vento médio
        if "step" in gust.dims and st in [int(s / np.timedelta64(1, "h")) for s in gust.step.values]:
            g = gust.sel(step=np.timedelta64(st, "h")).values * 3.6
        else:
            g = np.hypot(u10.isel(step=i).values, v10.isel(step=i).values) * 3.6
        fig, ax = blank()
        ax.imshow(to_merc_grid(g, lats, H), cmap=gust_cm, norm=BoundaryNorm(gust_lv, gust_cm.N), interpolation="bilinear", extent=[0, W, H, 0], aspect="auto")
        ax.set_xlim(0, W); ax.set_ylim(H, 0); frames["vento"].append(save(fig, f"vento_{st:03d}.png"))
        # chuva nas últimas 3 h (mm)
        cur = tp.isel(step=i).values * 1000
        r = np.zeros_like(cur) if prev_tp is None else np.maximum(cur - prev_tp, 0)
        prev_tp = cur
        fig, ax = blank()
        ax.imshow(to_merc_grid(r, lats, H), cmap=rain_cm, norm=BoundaryNorm(rain_lv, rain_cm.N), interpolation="bilinear", extent=[0, W, H, 0], aspect="auto")
        ax.set_xlim(0, W); ax.set_ylim(H, 0); frames["chuva"].append(save(fig, f"chuva_{st:03d}.png"))
        # isóbaras de 4 em 4 hPa, com centros de Baixa (B) e Alta (A)
        m = ndimage.gaussian_filter(msl.isel(step=i).values / 100, 1.2)
        fig, ax = blank(); ax.set_xlim(MAP_W, MAP_E); ax.set_ylim(merc(MAP_S), merc(MAP_N))
        lv = np.arange(940, 1060, 4)
        cs = ax.contour(x, Ym, m, levels=lv, colors="white", linewidths=0.9, alpha=0.85)
        ax.clabel(cs, fmt="%d", fontsize=7, colors="white", inline=True)
        mn = (m == ndimage.minimum_filter(m, size=25)) & (m < 1012)
        mx = (m == ndimage.maximum_filter(m, size=31)) & (m > 1020)
        for (yy, xx) in zip(*np.where(mn)):
            if 3 < yy < len(lats)-3 and 3 < xx < len(lons)-3:
                ax.text(x[xx], Ym[yy], f"B\n{m[yy,xx]:.0f}", color="#ff5449", fontsize=11, fontweight="bold", ha="center", va="center", linespacing=.9)
        for (yy, xx) in zip(*np.where(mx)):
            if 3 < yy < len(lats)-3 and 3 < xx < len(lons)-3:
                ax.text(x[xx], Ym[yy], f"A\n{m[yy,xx]:.0f}", color="#4fc3f7", fontsize=11, fontweight="bold", ha="center", va="center", linespacing=.9)
        frames["isobaras"].append(save(fig, f"isobaras_{st:03d}.png"))
    # série para Leiria (corrida principal)
    li = np.abs(lats - LEIRIA[0]).argmin(); lj = np.abs(lons - LEIRIA[1]).argmin()
    tpl = tp.isel(latitude=li, longitude=lj).values * 1000
    serie = []
    for i, st in enumerate(steps_avail):
        gg = float(gust.sel(step=np.timedelta64(st, "h")).isel(latitude=li, longitude=lj)) * 3.6 if st in [int(s / np.timedelta64(1, "h")) for s in gust.step.values] else None
        serie.append({"t": times[i], "p": round(float(msl.isel(step=i, latitude=li, longitude=lj)) / 100, 1),
                      "rajada": None if gg is None else round(gg), "chuva3h": round(float(tpl[i] - (tpl[i-1] if i else 0)), 1),
                      "vento": round(float(np.hypot(u10.isel(step=i, latitude=li, longitude=lj), v10.isel(step=i, latitude=li, longitude=lj))) * 3.6)})
    for f in files.values():
        try: os.remove(f)
        except OSError: pass
    return {"bounds": [[MAP_S, MAP_W], [MAP_N, MAP_E]], "times": times, "layers": frames}, serie

# ---------------------------------------------------------------- ENS: seguimento das depressões
def ens_fields(run_dt, steps):
    data = {}; lats = lons = None
    for st in steps:
        p = os.path.join(TMP, f"ens_{st}.grib2")
        fetch(p, type="pf", stream="enfo", param="msl", step=st, date=run_dt.strftime("%Y%m%d"), time=run_dt.hour)
        v = open_crop(p, "msl", TRK_S, TRK_N, TRK_W, TRK_E)
        arr = v.transpose("number", "latitude", "longitude").values / 100
        data[st] = np.stack([ndimage.gaussian_filter(a, 1.5) for a in arr]).astype(np.float32)
        lats, lons = v.latitude.values, v.longitude.values
        os.remove(p); log("  ENS passo", st, "ok", arr.shape)
    return data, lats, lons

def local_min_near(f, lats, lons, lat0, lon0, radius_km):
    """Mínimo de pressão fechado mais profundo dentro do raio. Devolve (lat, lon, p) ou None."""
    dlat = radius_km / 111; dlon = radius_km / (111 * max(0.2, math.cos(math.radians(lat0))))
    i0, i1 = np.searchsorted(-lats, [-(lat0 + dlat), -(lat0 - dlat)])
    j0, j1 = np.searchsorted(lons, [lon0 - dlon, lon0 + dlon])
    i0, j0 = max(i0, 1), max(j0, 1); i1, j1 = min(i1, len(lats)-1), min(j1, len(lons)-1)
    if i1 - i0 < 3 or j1 - j0 < 3: return None
    sub = f[i0:i1, j0:j1]
    mn = (sub == ndimage.minimum_filter(sub, size=9, mode="nearest"))
    best = None
    for yy, xx in zip(*np.where(mn)):
        la, lo, pv = float(lats[i0+yy]), float(lons[j0+xx]), float(sub[yy, xx])
        if km(lat0, lon0, la, lo) > radius_km: continue
        # tem de ser um mínimo "fechado": anel a ~300 km pelo menos 1,5 hPa mais alto
        ring = []
        for ang in range(0, 360, 30):
            rl = la + 2.7 * math.cos(math.radians(ang)); rL = lo + 2.7 * math.sin(math.radians(ang)) / max(0.2, math.cos(math.radians(la)))
            ii = np.abs(lats - rl).argmin(); jj = np.abs(lons - rL).argmin(); ring.append(f[ii, jj])
        if np.mean(ring) - pv < 1.5: continue
        score = pv + km(lat0, lon0, la, lo) / 150  # prefere mais profundo e mais perto do previsto
        if best is None or score < best[3]: best = (la, lo, pv, score)
    return None if best is None else best[:3]

def detect_starts(f0, lats, lons):
    mn = (f0 == ndimage.minimum_filter(f0, size=25)) & (f0 < 1002)
    out = []
    for yy, xx in zip(*np.where(mn)):
        la, lo = float(lats[yy]), float(lons[xx])
        if not (25 <= la <= 60 and -50 <= lo <= 8): continue
        out.append((la, lo, float(f0[yy, xx])))
    return sorted(out, key=lambda c: c[2])[:6]

def track(fields, steps, lats, lons, member, start, max_move_km=750):
    pts = []; cur = start; vel = (0.0, 0.0)
    for k, st in enumerate(steps):
        f = fields[st][member]
        guess = (cur[0] + vel[0], cur[1] + vel[1]) if k else cur
        dh = st - steps[k-1] if k else 0
        r = 350 if k == 0 else max_move_km * dh / 6
        found = local_min_near(f, lats, lons, guess[0], guess[1], r)
        if found is None: break
        if k: vel = (found[0] - cur[0], found[1] - cur[1])
        if found[2] > 1012: break  # depressão desfeita
        cur = found; pts.append({"step": st, "lat": round(found[0], 2), "lon": round(found[1], 2), "p": round(found[2], 1)})
    return pts

def cone_polygon(mean_pts, radii):
    from shapely.geometry import Point, MultiPolygon
    from shapely.ops import unary_union
    lat0 = sum(p[0] for p in mean_pts) / len(mean_pts); kx = 111 * math.cos(math.radians(lat0)); ky = 111
    circles = [Point(p[1]*kx, p[0]*ky).buffer(max(r, 15), 32) for p, r in zip(mean_pts, radii)]
    parts = [circles[0]] + [unary_union([circles[i], circles[i+1]]).convex_hull for i in range(len(circles)-1)]
    poly = unary_union(parts).simplify(3)
    geoms = poly.geoms if isinstance(poly, MultiPolygon) else [poly]
    return [[[round(y/ky, 3), round(x/kx, 3)] for x, y in g.exterior.coords] for g in geoms]

# Costa (ordenada de norte para sul e depois para leste) para dizer onde entra em terra
COSTA = [("A Coruña",43.37,-8.40),("Finisterra",42.91,-9.26),("Vigo",42.24,-8.72),("Caminha",41.87,-8.84),("Viana do Castelo",41.69,-8.85),
 ("Póvoa de Varzim",41.38,-8.76),("Porto",41.15,-8.67),("Espinho",41.01,-8.64),("Aveiro",40.64,-8.75),("Mira",40.43,-8.80),("Figueira da Foz",40.15,-8.86),
 ("Pedrógão (Leiria)",39.92,-8.95),("São Pedro de Moel",39.76,-9.03),("Nazaré",39.60,-9.07),("Peniche",39.36,-9.38),("Ericeira",38.96,-9.42),
 ("Cascais",38.70,-9.42),("Costa da Caparica",38.64,-9.24),("Sesimbra",38.44,-9.10),("Comporta",38.38,-8.80),("Sines",37.95,-8.87),
 ("Vila Nova de Milfontes",37.72,-8.79),("Zambujeira do Mar",37.52,-8.79),("Aljezur",37.32,-8.86),("Sagres",37.01,-8.94),("Lagos",37.10,-8.67),
 ("Portimão",37.12,-8.54),("Albufeira",37.08,-8.25),("Faro",37.00,-7.93),("Tavira",37.12,-7.65),("Vila Real de Santo António",37.19,-7.42),("Cádis",36.53,-6.30)]

def load_land():
    url = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_land.geojson"
    from shapely.geometry import shape, box
    from shapely.ops import unary_union
    g = json.loads(urllib.request.urlopen(url, timeout=60).read())
    clip = box(-20, 30, 10, 50)
    return unary_union([shape(f["geometry"]).intersection(clip) for f in g["features"] if shape(f["geometry"]).intersects(clip)])

def landfall(tracks, run_dt, land):
    from shapely.geometry import Point
    hits = []
    for tr in tracks:
        for a, b in zip(tr, tr[1:]):
            if Point(a["lon"], a["lat"]).within(land): break
            if Point(b["lon"], b["lat"]).within(land):
                for h in range(1, 7):  # interpolar de hora a hora
                    f = h / (b["step"] - a["step"]) if b["step"] > a["step"] else 1
                    la = a["lat"] + (b["lat"] - a["lat"]) * f; lo = a["lon"] + (b["lon"] - a["lon"]) * f
                    if Point(lo, la).within(land) or f >= 1:
                        hits.append((la, lo, a["step"] + h)); break
                break
    if len(hits) < max(5, 0.3 * len(tracks)): return None
    idx = sorted(min(range(len(COSTA)), key=lambda i: km(h[0], h[1], COSTA[i][1], COSTA[i][2])) for h in hits)
    times = sorted(h[2] for h in hits)
    q = lambda arr, p: arr[min(len(arr)-1, int(round(p * (len(arr)-1))))]
    T = lambda s: (run_dt + dt.timedelta(hours=s)).replace(tzinfo=dt.timezone.utc).isoformat()
    return {"cenarios": len(hits), "de": len(tracks), "troco": [COSTA[q(idx, .1)][0], COSTA[q(idx, .9)][0]],
            "mais_provavel": COSTA[q(idx, .5)][0], "janela": [T(q(times, .25)), T(q(times, .75))], "margem": [T(q(times, .1)), T(q(times, .9))],
            "pontos": [[round(h[0], 2), round(h[1], 2)] for h in hits]}

def storms_from_ens(run_dt):
    steps = list(range(0, 73, 3)) + list(range(78, 97, 6))
    fields, lats, lons = ens_fields(run_dt, steps)
    nmem = fields[0].shape[0]
    mean0 = fields[0].mean(axis=0)
    starts = detect_starts(mean0, lats, lons)
    log("candidatos:", starts)
    try: land = load_land()
    except Exception as e: log("costa indisponível:", e); land = None
    storms = []
    for (la, lo, p) in starts:
        tracks = [track(fields, steps, lats, lons, m, (la, lo, p)) for m in range(nmem)]
        tracks = [t for t in tracks if len(t) >= 2]
        if len(tracks) < 0.5 * nmem: continue
        mean_pts, radii, mean_out = [], [], []
        for st in steps:
            at = [next((q for q in t if q["step"] == st), None) for t in tracks]; at = [q for q in at if q]
            if len(at) < max(10, 0.6 * len(tracks)): break
            mla = float(np.mean([q["lat"] for q in at])); mlo = float(np.mean([q["lon"] for q in at]))
            d = sorted(km(mla, mlo, q["lat"], q["lon"]) for q in at)
            r80 = d[min(len(d)-1, int(round(0.8 * (len(d)-1))))]
            if r80 > 650 and len(mean_out) >= 3: break  # cenários demasiado espalhados: a trajetória deixa de ser fiável
            mean_pts.append((mla, mlo)); radii.append(r80)
            mean_out.append({"t": (run_dt + dt.timedelta(hours=st)).replace(tzinfo=dt.timezone.utc).isoformat(), "step": st, "lat": round(mla, 2), "lon": round(mlo, 2),
                             "p": round(float(np.median([q["p"] for q in at])), 1), "r80": round(r80), "n": len(at)})
        if len(mean_out) < 3: continue
        dmin = min(km(PT_CENTER[0], PT_CENTER[1], q["lat"], q["lon"]) for q in mean_out)
        if dmin > 900: continue  # só depressões que se aproximam de Portugal
        st = {"id": f"{round(la)}_{round(lo)}", "inicio": {"lat": round(la, 2), "lon": round(lo, 2), "p": round(p, 1)},
              "media": mean_out, "cone": cone_polygon(mean_pts, radii), "cenarios": len(tracks),
              "trajetorias": [[[q["lat"], q["lon"]] for q in t if q["step"] <= mean_out[-1]["step"]] for t in tracks],
              "pmin": min(q["p"] for q in mean_out), "dist_pt_min_km": round(dmin)}
        st["entrada_em_terra"] = landfall(tracks, run_dt, land) if land is not None else None
        storms.append(st)
    # remover duplicados (mesmo sistema detetado duas vezes)
    uniq = []
    for s in sorted(storms, key=lambda s: s["pmin"]):
        if all(km(s["media"][0]["lat"], s["media"][0]["lon"], u["media"][0]["lat"], u["media"][0]["lon"]) > 400 for u in uniq): uniq.append(s)
    # pressão em Leiria nos 50 cenários (percentis 10/50/90)
    li = np.abs(lats - LEIRIA[0]).argmin(); lj = np.abs(lons - LEIRIA[1]).argmin()
    pl = [{"t": (run_dt + dt.timedelta(hours=s)).replace(tzinfo=dt.timezone.utc).isoformat(), "p10": round(float(np.percentile(fields[s][:, li, lj], 10)), 1),
           "p50": round(float(np.percentile(fields[s][:, li, lj], 50)), 1), "p90": round(float(np.percentile(fields[s][:, li, lj], 90)), 1)} for s in steps]
    try:
        nomes = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "nomes.json"), encoding="utf-8"))
    except Exception:
        nomes = []
    hoje = run_dt.strftime("%Y-%m-%d")
    for s in uniq:
        for n in [x for x in nomes if "nome" in x]:
            if n.get("ate", "9999") >= hoje and km(s["inicio"]["lat"], s["inicio"]["lon"], n["lat"], n["lon"]) <= n.get("raio_km", 900):
                s["nome"] = n["nome"]; break
    return uniq[:3], nmem, pl

def main():
    now = dt.datetime.utcnow()
    hres_run = C.latest(type="fc", stream="oper", param="msl", step=72)
    ens_run = C.latest(type="pf", stream="enfo", param="msl", step=96)
    log("HRES", hres_run, "ENS", ens_run)
    prev = None
    if PREV and os.path.exists(PREV):
        try: prev = json.load(open(PREV))
        except Exception: prev = None
    if prev and prev.get("hres_run") == hres_run.isoformat() and prev.get("ens_run") == ens_run.isoformat() and os.environ.get("FORCE") != "1":
        log("sem corridas novas"); sys.exit(3)
    frames, serie = render_frames(hres_run, list(range(0, 73, 3)))
    storms, nmem, pleiria = storms_from_ens(ens_run)
    anterior = None
    if prev:
        anterior = {"ens_run": prev.get("ens_run"), "tempestades": [{"id": s["id"], "media": [{"t": q["t"], "lat": q["lat"], "lon": q["lon"]} for q in s["media"]]} for s in prev.get("tempestades", [])]}
        if prev.get("ens_run") == ens_run.isoformat(): anterior = prev.get("anterior")
    out = {"gerado": now.replace(tzinfo=dt.timezone.utc).isoformat(), "hres_run": hres_run.isoformat(), "ens_run": ens_run.isoformat(),
           "fonte": "ECMWF (dados abertos, CC-BY-4.0): corrida principal HRES e ensemble ENS com %d cenários" % nmem,
           "cenarios": nmem, "tempestades": storms, "frames": frames, "leiria": {"hres": serie, "ens_pressao": pleiria}, "anterior": anterior}
    json.dump(out, open(os.path.join(OUT, "data.json"), "w"), ensure_ascii=False, separators=(",", ":"))
    log("tempestades:", [(s["id"], s["pmin"], s["dist_pt_min_km"], (s["entrada_em_terra"] or {}).get("mais_provavel")) for s in storms])

if __name__ == "__main__":
    main()

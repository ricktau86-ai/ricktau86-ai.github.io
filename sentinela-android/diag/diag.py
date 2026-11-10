import urllib.request, json, time
H={"User-Agent":"Sentinela/1.0"}
def g(u,n=300):
    t=time.time()
    try:
        with urllib.request.urlopen(urllib.request.Request(u,headers=H),timeout=40) as r: b=r.read(); print("OK",r.status,u,len(b),"bytes",round(time.time()-t,1),"s","CORS",r.headers.get("Access-Control-Allow-Origin"),"|",b[:n].decode("utf-8","replace").replace("\n"," | "))
    except Exception as e: print("FALHA",u,e)
g("https://api.ipma.pt/open-data/forecast/warnings/warnings_www.json")
g("https://api.ipma.pt/open-data/observation/seismic/7.json")
g("https://services-eu1.arcgis.com/VlrHb7fn5ewYhX6y/arcgis/rest/services/OcorrenciasSite/FeatureServer/0/query?where=1%3D1&returnCountOnly=true&f=json")
for src in ["SUOMI_VIIRS_C2","J1_VIIRS_C2","MODIS_C6_1"]:
    folder={"SUOMI_VIIRS_C2":"suomi-npp-viirs-c2","J1_VIIRS_C2":"noaa-20-viirs-c2","MODIS_C6_1":"modis-c6.1"}[src]
    g(f"https://firms.modaps.eosdis.nasa.gov/data/active_fire/{folder}/csv/{src}_Europe_24h.csv",400)
g("https://api.telegram.org/",120)
g("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=39.74&longitude=-8.8&current=alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen&timezone=Europe%2FLisbon",500)

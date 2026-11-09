import json, re, time, urllib.request, urllib.parse
PORTAL="https://prociv-portal.geomai.mai.gov.pt/arcgis"; UA={"User-Agent":"Sentinela/1.0","Referer":"https://prociv.gov.pt/"}
def get(u, tries=4):
    for t in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(u,headers=UA),timeout=40) as r: return r.read().decode("utf-8","replace")
        except Exception as e: print("  tentativa",t+1,"falhou:",e); time.sleep(4)
    return ""
dash=get(f"{PORTAL}/sharing/rest/content/items/47b772e5c8b447399eb5dfc1cc0dcf00/data?f=json")
print("DASH len",len(dash))
ds=set(re.findall(r'"itemId"\s*:\s*"([0-9a-f]{32})"',dash)); print("itemIds:",ds)
print("layerIds:",sorted(set(re.findall(r'"layerId"\s*:\s*([0-9]+)',dash))))
print("urls:",sorted(set(re.findall(r'https?://[^"\\ ]+',dash)))[:40])
for m in re.finditer(r'"dataSource"\s*:\s*\{[^{}]*\}',dash): print("DS:",m.group(0)[:300])
for i in ds:
    print("\n== ITEM",i); s=get(f"{PORTAL}/sharing/rest/content/items/{i}?f=json"); print(s[:700])
    d=get(f"{PORTAL}/sharing/rest/content/items/{i}/data?f=json"); print("DATA:",d[:2500])
    for u in sorted(set(re.findall(r'https?://[^"\\ ]+?/(?:FeatureServer|MapServer)(?:/\d+)?',s+d))): print("  SERVICE URL",u)
for svc in ["https://prociv-agserver.geomai.mai.gov.pt/arcgis/rest/services/Ocorrencias_Base/MapServer"]:
    print("\n== SVC",svc); s=get(svc+"?f=json"); print(s[:2500])
    try:
        for l in json.loads(s).get("layers",[]):
            lu=f"{svc}/{l['id']}"; m=get(lu+"?f=json")
            try: mj=json.loads(m); print("LAYER",l["id"],l["name"],mj.get("geometryType"),[f["name"] for f in mj.get("fields",[])])
            except Exception: print("LAYER",l, m[:200])
            q=get(lu+"/query?where=1%3D1&outFields=*&outSR=4326&resultRecordCount=2&f=json"); print("  SAMPLE",q[:1500])
            c=get(lu+"/query?where=1%3D1&returnCountOnly=true&f=json"); print("  COUNT",c[:200])
    except Exception as e: print("svc parse",e)

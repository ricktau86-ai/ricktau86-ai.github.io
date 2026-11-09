import urllib.request, json, re
H={"User-Agent":"Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36"}
def g(u):
    try:
        with urllib.request.urlopen(urllib.request.Request(u,headers=H),timeout=25) as r: return r.read().decode("utf-8","replace"), dict(r.headers)
    except Exception as e: return "ERRO "+str(e), {}
app,_=g("https://prociv-portal.geomai.mai.gov.pt/arcgis/sharing/rest/content/items/29e83f11f7a34339b35364e483e3846f/data?f=json")
try:
    j=json.loads(app)
    for wid in ["widget_16","widget_17","widget_18","widget_19","widget_20","widget_1","widget_2"]:
        w=j.get("widgets",{}).get(wid); print("WIDGET",wid,json.dumps(w,ensure_ascii=False)[:700] if w else None)
except Exception as e: print("app err",e,app[:200])
for i in ["4862420f68204df1b7cad1c22b2a7b27","ba4aad5cd9014408a7ae30b567fedc75"]:
    info,_=g(f"https://www.arcgis.com/sharing/rest/content/items/{i}?f=json"); print("\nAGOL ITEM",i,info[:500])
    d,_=g(f"https://www.arcgis.com/sharing/rest/content/items/{i}/data?f=json")
    ids=sorted(set(re.findall(r'"itemId"\s*:\s*"([0-9a-f]{32})"',d))); urls=sorted(set(re.findall(r'https?://[^"\\ ]+?/(?:FeatureServer|MapServer)(?:/\d+)?',d)))
    print("  itemIds",ids,"urls",urls)
    for k in ids[:6]:
        ii,_=g(f"https://www.arcgis.com/sharing/rest/content/items/{k}?f=json")
        try: jj=json.loads(ii); print("   -",k,jj.get("type"),jj.get("title"),jj.get("access"),jj.get("url"))
        except: print("   -",k,ii[:150])
        dd,_=g(f"https://www.arcgis.com/sharing/rest/content/items/{k}/data?f=json")
        for u in sorted(set(re.findall(r'https?://[^"\\ ]+?/(?:FeatureServer|MapServer)(?:/\d+)?',dd))): print("      layer",u)
        for m in re.finditer(r'"title"\s*:\s*"([^"]+)"\s*,\s*"url"\s*:\s*"([^"]+)"',dd): print("      opl",m.group(1),m.group(2))

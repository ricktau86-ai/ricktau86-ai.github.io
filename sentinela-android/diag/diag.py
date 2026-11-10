import urllib.request, json, re
H={"User-Agent":"Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36","Accept-Language":"pt-PT,pt;q=0.9"}
def g(u):
    try:
        with urllib.request.urlopen(urllib.request.Request(u,headers=H),timeout=30) as r: return r.status, r.read().decode("utf-8","replace")
    except urllib.error.HTTPError as e: return e.code, e.read().decode("utf-8","replace")[:400]
    except Exception as e: return None, str(e)
B="https://e-redes.opendatasoft.com/api/explore/v2.1"
st,c=g(B+"/catalog/datasets?limit=100&select=dataset_id,metas.default.title,metas.default.modified,metas.default.records_count")
try:
    for d in json.loads(c)["results"]:
        t=json.dumps(d,ensure_ascii=False)
        if re.search(r"outage|interrup|avaria|falha|corte|incid",t,re.I): print("DS",t)
except Exception as e: print("catalog err",st,c[:300])
for ds in ["outages-per-geography","active-energy-interruptions","scheduled-energy-interruptions","network-scheduling-work"]:
    st,c=g(B+f"/catalog/datasets/{ds}/records?limit=3"); print("\nREC",ds,st,c[:900])
for u in ["https://downdetector.pt/problemas/meo/","https://downdetector.pt/"]:
    st,c=g(u); print("\nDD",u,st,len(c),re.sub(r"\s+"," ",c[:300]), "| cloudflare:",bool(re.search("just a moment|cf-chl|challenge",c,re.I)))
for u in ["https://www.google.com/appsstatus/dashboard/incidents.json","https://metastatus.com/","https://status.cloud.google.com/incidents.json"]:
    st,c=g(u); print("\nSTATUS",u,st,len(c),c[:300].replace("\n"," "))

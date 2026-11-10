import urllib.request, json
H={"User-Agent":"Mozilla/5.0"}
def g(u):
    with urllib.request.urlopen(urllib.request.Request(u,headers=H),timeout=30) as r: return json.loads(r.read())
B="https://e-redes.opendatasoft.com/api/explore/v2.1"
off=0
while True:
    d=g(B+f"/catalog/datasets?limit=100&offset={off}")
    for x in d["results"]:
        m=x.get("metas",{}).get("default",{}); print("DS",x["dataset_id"],"|",m.get("title"),"|",m.get("modified"),"|",m.get("records_count"))
    off+=100
    if off>=d["total_count"]: break
d=g(B+"/catalog/datasets/network-scheduling-work/records?limit=0&group_by=data&order_by=data")
print("NSW por dia",json.dumps(d)[:800])
d=g(B+"/catalog/datasets/network-scheduling-work/records?where=municipality%3D%22Leiria%22&limit=5&order_by=startdatetime")
print("LEIRIA",json.dumps(d,ensure_ascii=False)[:1200])

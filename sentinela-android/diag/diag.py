import urllib.request, urllib.parse, json, datetime
H={"User-Agent":"Mozilla/5.0"}
base="https://e-redes.opendatasoft.com/api/explore/v2.1/catalog/datasets/network-scheduling-work/records"
days=[(datetime.datetime.utcnow()+datetime.timedelta(days=i)).strftime("%Y-%m-%d") for i in range(8)]
def go(q,extra=""):
    try: return json.loads(urllib.request.urlopen(urllib.request.Request(f"{base}?where={urllib.parse.quote(q)}{extra}",headers=H),timeout=30).read())
    except urllib.error.HTTPError as e: return {"err":e.code,"b":e.read()[:300].decode()}
for conc in ["Leiria","Pombal","Viseu","Bragança","Sintra"]:
    d=go(f'search(municipality,"{conc}")',"&order_by=startdatetime&limit=100"); print("MUN",conc,d.get("total_count"),d.get("err"),d.get("b",""),json.dumps(d.get("results",[])[:1],ensure_ascii=False)[:300])
d=go("data in ("+",".join(f'"{x}"' for x in days)+")","&limit=0"); print("NAT",d)

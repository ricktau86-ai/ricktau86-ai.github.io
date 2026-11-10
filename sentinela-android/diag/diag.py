import urllib.request, urllib.parse, json, datetime
H={"User-Agent":"Mozilla/5.0"}
base="https://e-redes.opendatasoft.com/api/explore/v2.1/catalog/datasets/network-scheduling-work/records"
now=datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S"); wk=(datetime.datetime.utcnow()+datetime.timedelta(days=7)).strftime("%Y-%m-%dT%H:%M:%S")
for conc in ["Leiria","Pombal","Viseu","Bragança"]:
    q=urllib.parse.quote(f'search(municipality,"{conc}") and enddatetime>="{now}"')
    try: d=json.loads(urllib.request.urlopen(urllib.request.Request(f"{base}?where={q}&order_by=startdatetime&limit=3",headers=H),timeout=30).read()); print("MUN",conc,d["total_count"],json.dumps(d["results"][:1],ensure_ascii=False))
    except urllib.error.HTTPError as e: print("ERR",conc,e.code,e.read()[:300])
q=urllib.parse.quote(f'enddatetime>="{now}" and startdatetime<="{wk}"')
try: d=json.loads(urllib.request.urlopen(urllib.request.Request(f"{base}?where={q}&limit=0",headers=H),timeout=30).read()); print("NAT",d["total_count"])
except urllib.error.HTTPError as e: print("ERR nat",e.code,e.read()[:300])
d=json.loads(urllib.request.urlopen(urllib.request.Request("https://www.google.com/appsstatus/dashboard/incidents.json",headers=H),timeout=30).read())
act=[i for i in d if not i.get("end")]; print("GWS ativos",len(act), json.dumps(act[:1],ensure_ascii=False)[:600]); print("GWS keys",list(d[0].keys()))

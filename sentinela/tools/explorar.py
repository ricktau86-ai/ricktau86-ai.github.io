import json, re, sys, urllib.request, urllib.parse
PORTAL="https://prociv-portal.geomai.mai.gov.pt/arcgis"; SERVER="https://prociv-agserver.geomai.mai.gov.pt/arcgis/rest/services"
APP="29e83f11f7a34339b35364e483e3846f"; UA={"User-Agent":"Sentinela/1.0","Referer":"https://prociv.gov.pt/"}
def get(u):
    with urllib.request.urlopen(urllib.request.Request(u,headers=UA),timeout=40) as r: return r.read().decode("utf-8","replace")
def show(t,u,n=4000):
    print("\n=====",t,u)
    try: s=get(u); print(s[:n]); return s
    except Exception as e: print("ERRO",e); return ""
s=show("APP DATA",f"{PORTAL}/sharing/rest/content/items/{APP}/data?f=json",9000)
ids=sorted(set(re.findall(r"[0-9a-f]{32}",s))-{APP}); print("\nIDS:",ids)
print("URLS:",sorted(set(re.findall(r'https?://[^"\\ ]+',s)))[:80])
show("APP INFO",f"{PORTAL}/sharing/rest/content/items/{APP}?f=json",2500)
for i in ids[:12]:
    info=show("ITEM",f"{PORTAL}/sharing/rest/content/items/{i}?f=json",1200)
    show("ITEM DATA",f"{PORTAL}/sharing/rest/content/items/{i}/data?f=json",3500)
show("SEARCH",f"{PORTAL}/sharing/rest/search?"+urllib.parse.urlencode({"q":"type:\"Feature Service\" OR type:\"Map Service\" OR type:\"Web Map\"","num":100,"f":"json"}),6000)
root=show("SERVER ROOT",SERVER+"?f=json",3000)
try:
    j=json.loads(root)
    for f in j.get("folders",[])[:30]: show("FOLDER",f"{SERVER}/{f}?f=json",1500)
except Exception as e: print(e)

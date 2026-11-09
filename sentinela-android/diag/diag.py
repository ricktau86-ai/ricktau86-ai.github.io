import urllib.request, json
L="https://services-eu1.arcgis.com/VlrHb7fn5ewYhX6y/arcgis/rest/services/OcorrenciasSite/FeatureServer/0"
H={"User-Agent":"Mozilla/5.0","Origin":"https://example.org"}
def g(u):
    with urllib.request.urlopen(urllib.request.Request(u,headers=H),timeout=30) as r: return r.read().decode("utf-8","replace"), r.headers
m,h=g(L+"?f=json"); j=json.loads(m)
print("NAME",j.get("name"),"| maxRecordCount",j.get("maxRecordCount"),"| CORS",h.get("Access-Control-Allow-Origin"),"| geom",j.get("geometryType"))
print("FIELDS",[f["name"] for f in j.get("fields",[])])
c,_=g(L+"/query?where=1%3D1&returnCountOnly=true&f=json"); print("COUNT",c)
s,_=g(L+"/query?where=1%3D1&outFields=*&outSR=4326&resultRecordCount=3&f=json")
for f in json.loads(s).get("features",[]): print("SAMPLE",json.dumps(f,ensure_ascii=False)[:2000])

import json, re, time, urllib.request
PORTAL="https://prociv-portal.geomai.mai.gov.pt/arcgis"
L="https://prociv-agserver.geomai.mai.gov.pt/arcgis/rest/services/Ocorrencias_Base/FeatureServer/0"
BR={"User-Agent":"Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36",
    "Accept":"application/json, text/plain, */*","Accept-Language":"pt-PT,pt;q=0.9","Origin":"https://prociv-portal.geomai.mai.gov.pt","Referer":"https://prociv-portal.geomai.mai.gov.pt/"}
def get(u,h):
    try:
        with urllib.request.urlopen(urllib.request.Request(u,headers=h),timeout=30) as r: return r.status, dict(r.headers), r.read().decode("utf-8","replace")
    except Exception as e: return None, {}, str(e)
d=get(f"{PORTAL}/sharing/rest/content/items/995fda1ccd2a4a05a8e3cdac47d91e67/data?f=json",BR)[2]
try:
    j=json.loads(d)
    for l in j["operationalLayers"]:
        if "Ocorr" in l.get("title",""):
            pi=l.get("popupInfo",{}); print("POPUP TITLE:",pi.get("title"))
            print("FIELDS:",[(f.get("fieldName"),f.get("label")) for f in pi.get("fieldInfos",[])])
            print("POPUP ELEMS:",json.dumps(pi.get("popupElements"),ensure_ascii=False)[:1500])
            print("EXPR:",json.dumps(pi.get("expressionInfos"),ensure_ascii=False)[:1500])
            print("RENDERER:",json.dumps(l.get("layerDefinition",{}).get("drawingInfo",{}),ensure_ascii=False)[:1200])
except Exception as e: print("webmap err",e,d[:300])
for name,h in [("browser",BR),("minimo",{"User-Agent":"Mozilla/5.0"})]:
    s,hd,b=get(L+"?f=json",h); print("\n==",name,"status",s,"cors",hd.get("Access-Control-Allow-Origin"),"server",hd.get("Server")); print(b[:1500])
    s,hd,b=get(L+"/query?where=1%3D1&outFields=*&outSR=4326&resultRecordCount=2&f=json",h); print("QUERY",s,b[:1800])

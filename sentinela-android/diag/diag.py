import urllib.request, json
H={"User-Agent":"Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36","Origin":"https://prociv-portal.geomai.mai.gov.pt","Referer":"https://prociv-portal.geomai.mai.gov.pt/arcgis/apps/dashboards/47b772e5c8b447399eb5dfc1cc0dcf00"}
def t(u):
    try:
        with urllib.request.urlopen(urllib.request.Request(u,headers=H),timeout=20) as r:
            b=r.read().decode("utf-8","replace"); print("OK",r.status,u,"| CORS:",r.headers.get("Access-Control-Allow-Origin"),"|",b[:600].replace("\n"," "))
    except Exception as e: print("FALHA",u,"|",e)
P="https://prociv-portal.geomai.mai.gov.pt"
t(P+"/arcgis/sharing/rest/content/items/16a8b70d57d7443884a2e34a02e6e954?f=json")
for base in [P+"/server/rest/services",P+"/arcgis/rest/services",P+"/hosting/rest/services",P+"/agserver/rest/services",
             "https://prociv-agserver.geomai.mai.gov.pt/arcgis/rest/services","http://prociv-agserver.geomai.mai.gov.pt/arcgis/rest/services",
             "https://prociv-agserver.geomai.mai.gov.pt/server/rest/services"]:
    t(base+"/Ocorrencias_Base/FeatureServer/0/query?where=1%3D1&outFields=Natureza,EstadoOcorrencia,Concelho&returnGeometry=false&resultRecordCount=2&f=json")
t(P+"/arcgis/sharing/rest/portals/self/servers?f=json")
t(P+"/arcgis/sharing/proxy?https://prociv-agserver.geomai.mai.gov.pt/arcgis/rest/services/Ocorrencias_Base/FeatureServer/0?f=json")

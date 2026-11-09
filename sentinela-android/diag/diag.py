import urllib.request, re
H={"User-Agent":"Mozilla/5.0"}
js=urllib.request.urlopen(urllib.request.Request("https://emergencias.pt/assets/index-cxpUDRPg.js",headers=H),timeout=30).read().decode("utf-8","replace")
for k in ["/api/v2/incidents/active","baseURL","baseUrl","fogos","VITE_","import.meta.env","fetch(","axios","tile","natureza","Fonte","fonte","ANEPC","Proteção Civil"]:
    for i,m in enumerate(re.finditer(re.escape(k),js)):
        if i>=2: break
        print("CTX",k,"::",js[max(0,m.start()-300):m.start()+300].replace("\n"," "))

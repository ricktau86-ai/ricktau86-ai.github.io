import urllib.request, re, json
H={"User-Agent":"Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36"}
def g(u):
    try:
        with urllib.request.urlopen(urllib.request.Request(u,headers=H),timeout=30) as r: return r.read().decode("utf-8","replace")
    except Exception as e: return "ERRO "+str(e)
base="https://emergencias.pt"
html=g(base+"/"); print("HTML", html[:1500].replace("\n"," "))
scripts=re.findall(r'(?:src|href)="([^"]+\.(?:js|json|webmanifest))"',html); print("SCRIPTS",scripts)
for s in scripts[:12]:
    u=s if s.startswith("http") else base+("" if s.startswith("/") else "/")+s
    js=g(u); print("\n== JS",u,len(js))
    urls=sorted(set(re.findall(r'https?://[A-Za-z0-9._~:/?#\[\]@!$&\'()*+,;=%-]+',js)))
    print("URLS",[x for x in urls if not re.search(r'w3\.org|reactjs|github\.com/|mozilla|fb\.me|googleapis\.com/css|schema\.org',x)][:80])
    for m in set(re.findall(r'["\'`](/api/[^"\'`]+|/data/[^"\'`]+|[^"\'`]*\.json)["\'`]',js)): print("PATH",m)
    for k in ["FeatureServer","prociv","anepc","fogos","ipma","arcgis","supabase","firebase","socket","wss://"]:
        for m in re.finditer(k,js,re.I):
            print("CTX",k,js[max(0,m.start()-150):m.start()+200].replace("\n"," ")); break
for p in ["/api/ocorrencias","/api/occurrences","/api/incidents","/api/v1/ocorrencias","/ocorrencias.json","/data.json","/robots.txt","/about","/sobre"]:
    r=g(base+p); print("\nTRY",p,r[:400].replace("\n"," "))

import urllib.request, urllib.parse, json, datetime
H={"User-Agent":"Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36","Origin":"https://ricktau86-ai.github.io"}
def get(u):
    try:
        with urllib.request.urlopen(urllib.request.Request(u,headers=H),timeout=30) as r: return r.status, dict(r.headers), r.read().decode("utf-8","replace")
    except urllib.error.HTTPError as e: return e.code, dict(e.headers), e.read()[:300].decode("utf-8","replace")
    except Exception as e: return None,{},str(e)
base="https://e-redes.opendatasoft.com/api/explore/v2.1/catalog/datasets/network-scheduling-work/records"
days=[(datetime.datetime.utcnow()+datetime.timedelta(days=i)).strftime("%Y-%m-%d") for i in range(8)]
for conc in ["Leiria","Pombal","Viseu","Sintra"]:
    st,h,b=get(f"{base}?where={urllib.parse.quote(f'search(municipality,\"{conc}\")')}&order_by=startdatetime&limit=100"); print("MUN",conc,st,b[:250])
st,h,b=get(f"{base}?where={urllib.parse.quote('data in ('+','.join(chr(34)+x+chr(34) for x in days)+')')}&limit=0"); print("NAT",st,b[:200])
for u in ["https://lusometeo.com/wp-json/wp/v2/posts?per_page=3&_fields=id,date,link,title,excerpt,categories,featured_media,_links","https://lusometeo.com/feed/","https://lusometeo.com/wp-json/wp/v2/categories?per_page=50&_fields=id,name,slug,count"]:
    st,h,b=get(u); print("\nLUSO",u,st,"CORS:",h.get("Access-Control-Allow-Origin"),"| server:",h.get("Server"),"|",b[:1500].replace("\n"," "))

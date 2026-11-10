import urllib.request, re
H={"User-Agent":"Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36"}
F={"GN Leiria":"https://news.google.com/rss/search?q=Leiria+when:7d&hl=pt-PT&gl=PT&ceid=PT:pt-150",
 "GN PT top":"https://news.google.com/rss?hl=pt-PT&gl=PT&ceid=PT:pt-150",
 "GN Mundo":"https://news.google.com/rss/headlines/section/topic/WORLD?hl=pt-PT&gl=PT&ceid=PT:pt-150",
 "Regiao Leiria":"https://www.regiaodeleiria.pt/feed/","Jornal Leiria":"https://www.jornaldeleiria.pt/rss","Jornal Leiria2":"https://www.jornaldeleiria.pt/feed",
 "Diario Leiria":"https://www.diarioleiria.pt/feed/","Leiria Radio":"https://www.radiolis.pt/feed/",
 "RTP":"https://www.rtp.pt/noticias/rss","RTP pais":"https://www.rtp.pt/noticias/rss/pais","RTP mundo":"https://www.rtp.pt/noticias/rss/mundo",
 "Observador":"https://observador.pt/feed/","Publico":"https://feeds.feedburner.com/PublicoRSS","Publico mundo":"https://www.publico.pt/api/list/mundo",
 "NaM":"https://www.noticiasaominuto.com/rss/ultima-hora","SIC":"https://sicnoticias.pt/rss","Expresso":"https://expresso.pt/rss/ultimas","JN":"https://www.jn.pt/rss/","CM":"https://www.cmjornal.pt/rss"}
for n,u in F.items():
    try:
        with urllib.request.urlopen(urllib.request.Request(u,headers=H),timeout=20) as r:
            b=r.read().decode("utf-8","replace")
            items=re.findall(r"<item[\s>].*?</item>",b,re.S); t=[re.sub(r"<!\[CDATA\[|\]\]>","",re.search(r"<title>(.*?)</title>",i,re.S).group(1)).strip() for i in items[:2] if re.search(r"<title>",i)]
            d=re.search(r"<pubDate>(.*?)</pubDate>",b)
            print("OK",n,r.status,len(items),"itens | último:",d.group(1) if d else "-","|",t)
    except Exception as e: print("FALHA",n,e)

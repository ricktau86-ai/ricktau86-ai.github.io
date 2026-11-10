import sys; sys.path.insert(0,"sentinela/alertas")
import alertas
ev,f=alertas.eventos()
print("EVENTOS",len(ev),"FALHAS",f)
for k,t,p in ev[:6]: print("EV",k,"|",t.replace("\n"," / ")[:400])

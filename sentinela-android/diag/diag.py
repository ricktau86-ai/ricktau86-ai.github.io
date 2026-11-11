import time, os, sys
from ecmwf.opendata import Client
c=Client(source="ecmwf")
t=time.time()
for typ,stream in [("fc","oper"),("pf","enfo")]:
    try: print("LATEST",typ,stream,c.latest(type=typ,stream=stream,param="msl",step=0))
    except Exception as e: print("LATEST ERR",typ,e)
def tryget(**k):
    t=time.time()
    try:
        r=c.retrieve(target="/tmp/x.grib2",**k); print("OK",k,round(os.path.getsize("/tmp/x.grib2")/1e6,1),"MB",round(time.time()-t,1),"s",r.datetime)
    except Exception as e: print("ERR",k,str(e)[:300])
tryget(type="fc",stream="oper",param=["msl","10u","10v"],step=[0,3])
tryget(type="fc",stream="oper",param=["10fg"],step=[3])
tryget(type="fc",stream="oper",param=["fg10"],step=[3])
tryget(type="fc",stream="oper",param=["tp"],step=[3,6])
tryget(type="cf",stream="enfo",param="msl",step=[0,6])
t=time.time(); tryget(type="pf",stream="enfo",param="msl",step=[0,6])
import xarray as xr
ds=xr.open_dataset("/tmp/x.grib2",engine="cfgrib")
print("DS",ds); sub=ds.msl.sel(latitude=slice(55,25),longitude=slice(-40,5)); print("MIN hPa",float(sub.min())/100)

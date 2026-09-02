#!/usr/bin/env python3
"""Dependency-free concurrent HTTP smoke/load probe. Usage: python scripts/load-smoke.py URL [requests] [concurrency]."""
from __future__ import annotations
import concurrent.futures, sys, time
from urllib.request import Request, urlopen

def one(url):
    started=time.perf_counter()
    try:
        with urlopen(Request(url, headers={'User-Agent':'uep-qualification/0.15'}), timeout=10) as r:
            r.read(4096); return r.status, (time.perf_counter()-started)*1000, None
    except Exception as e: return 0, (time.perf_counter()-started)*1000, str(e)

url=sys.argv[1] if len(sys.argv)>1 else 'http://localhost:8080/actuator/health'
count=int(sys.argv[2]) if len(sys.argv)>2 else 50
workers=int(sys.argv[3]) if len(sys.argv)>3 else min(10,count)
t0=time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as ex: rows=list(ex.map(one,[url]*count))
ms=sorted(r[1] for r in rows); ok=sum(1 for r in rows if 200<=r[0]<400)
p95=ms[min(len(ms)-1,max(0,int(len(ms)*.95)-1))]
print(f'requests={count} concurrency={workers} ok={ok} elapsed_ms={(time.perf_counter()-t0)*1000:.1f} p95_ms={p95:.1f}')
if ok != count: sys.exit(2)

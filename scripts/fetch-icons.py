"""Fetch the pinned Lucide subset and its redistribution notices from upstream."""
from pathlib import Path
import hashlib
import json
import urllib.request

root=Path(__file__).resolve().parents[1]
base='https://raw.githubusercontent.com/lucide-icons/lucide/'
request=urllib.request.Request('https://api.github.com/repos/lucide-icons/lucide/commits/main',headers={'User-Agent':'VibeFinancier-build'})
commit=json.load(urllib.request.urlopen(request,timeout=30))['sha']
names=['coins','wallet','credit-card','landmark','badge-check','piggy-bank','gem','utensils','shopping-cart','house','handshake','gamepad-2','user-round','hand-coins','percent','car','bus','plane','coffee','gift','heart-pulse','book-open','smartphone','paw-print','briefcase-business','menu','list-filter','arrow-left','plus','chart-pie','chart-no-axes-column','list','settings','tag','cloud','calendar-days','download','check']
folder=root/'app/src/main/assets/lucide'
folder.mkdir(parents=True,exist_ok=True)
manifest={'source':'https://github.com/lucide-icons/lucide','commit':commit,'license':'ISC and retained MIT notices for Feather-derived icons','files':{}}
for name in names:
 url=base+commit+'/icons/'+name+'.svg'
 data=urllib.request.urlopen(url,timeout=30).read()
 (folder/(name+'.svg')).write_bytes(data)
 manifest['files'][name+'.svg']={'url':url,'sha256':hashlib.sha256(data).hexdigest()}
license_data=urllib.request.urlopen(base+commit+'/LICENSE',timeout=30).read()
(folder/'LICENSE').write_bytes(license_data)
(folder/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print('Downloaded',len(names),'Lucide icons at',commit)

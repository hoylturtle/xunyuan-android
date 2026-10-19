#!/usr/bin/env python3
"""Sign the public workflows using the service used by the Cherri compiler.

No account credentials or user search data are included in these workflows.
"""
import json,pathlib,plistlib,urllib.request,hashlib
root=pathlib.Path(__file__).resolve().parent
out=root/'prebuilt';out.mkdir(exist_ok=True)
for stem,name in [('Xunyuan','寻源'),('Xunyuan-Plus','寻源 Plus')]:
    workflow=plistlib.loads((root/'dist'/(stem+'.unsigned.shortcut')).read_bytes())
    assert workflow['WFWorkflowName']==name
    xml=plistlib.dumps(workflow,fmt=plistlib.FMT_XML).decode()
    payload=json.dumps({'shortcutName':name,'shortcut':xml}).encode()
    request=urllib.request.Request('https://hubsign.routinehub.services/sign',data=payload,headers={'Content-Type':'application/json','User-Agent':'cherri/0.3','Origin':'https://routinehub.co','Referer':'https://routinehub.co/'})
    try:
        with urllib.request.urlopen(request,timeout=45) as response:data=response.read(4*1024*1024)
    except Exception as error:
        print('HubSign unavailable:',type(error).__name__,'; trying documented Shortcuty signing API',flush=True)
        raw=(root/'dist'/(stem+'.unsigned.shortcut')).read_bytes()
        fallback=urllib.request.Request('https://sign.shortcuty.app/api/v1/sign',data=raw,headers={'Content-Type':'application/octet-stream','X-Filename':stem+'.shortcut','User-Agent':'Xunyuan/0.2.0'})
        with urllib.request.urlopen(fallback,timeout=60) as response:
            data=response.read(4*1024*1024)
            expected=response.headers.get('X-Shortcut-Input-SHA256')
            if expected and expected!=hashlib.sha256(raw).hexdigest():raise SystemExit('Signing service input checksum mismatch')
    if not data.startswith(b'AEA1'):raise SystemExit('Signing service did not return a signed Shortcut')
    (out/(name+'.shortcut')).write_bytes(data)
    print('Signed',name,len(data),'bytes; verify before publishing')

#!/usr/bin/env python3
"""Exercise the real native Android screens and save screenshots + UI trees."""
import pathlib,subprocess,time,xml.etree.ElementTree as ET,re,os,urllib.request,zipfile,io
root=pathlib.Path(__file__).resolve().parent.parent
dest=root/'ui-captures';dest.mkdir(exist_ok=True)
def adb(*args,**kwargs):return subprocess.check_output(['adb',*args],**kwargs)
def dump():
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    return adb('shell','cat','/sdcard/window.xml').decode()
def tap(label):
    for _ in range(8):
        xml=dump()
        for n in ET.fromstring(xml).iter('node'):
            if n.get('text')==label or n.get('content-desc')==label:
                x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
                adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.7);return
        time.sleep(.6)
    raise RuntimeError('Missing UI control: '+label)
def capture(prefix,name):
    time.sleep(.8);(dest/(prefix+'-'+name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    (dest/(prefix+'-'+name+'.xml')).write_text(dump())
def screens(apk,prefix):
    subprocess.run(['adb','uninstall','com.btsou.search'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    adb('install',str(apk));adb('shell','am','start','-n','com.btsou.search/.MainActivity');time.sleep(3)
    capture(prefix,'home')
    for page in ['收藏','来源','设置']:
        tap(page);capture(prefix,{'收藏':'favorites','来源':'sources','设置':'settings'}[page])
    # Confirm the default is off without turning the setting on.
    xml=dump();toggles=[n for n in ET.fromstring(xml).iter('node') if n.get('class')=='android.widget.Switch' and '成人' in n.get('text','')]
    assert toggles and toggles[0].get('checked')=='false','Adult switch should default off'
    tap('搜索');tap('搜索关键词');adb('shell','input','text','ubuntu');tap('搜索')
    time.sleep(20);capture(prefix,'search')
    adb('shell','input','keyevent','4')

try:
    # Reuse the real baseline already captured from 0.2.0, so its startup updater
    # cannot interrupt a later comparison after a newer version is released.
    with urllib.request.urlopen('https://github.com/hoylturtle/xunyuan-android/releases/download/ui-preview/ui-captures.zip',timeout=30) as response:
        archive=zipfile.ZipFile(io.BytesIO(response.read()))
    for name in archive.namelist():
        if pathlib.Path(name).name==name and name.startswith('before-') and name.endswith(('.png','.xml')):
            (dest/name).write_bytes(archive.read(name))
except Exception:
    pass
if not (dest/'before-home.png').exists():screens(root/'release-bootstrap/Xunyuan-0.2.0.apk','before')
new=sorted((root/'android/build').glob('Xunyuan-*.apk'))
assert new
screens(new[-1],'after')
print('Native UI smoke checks completed')

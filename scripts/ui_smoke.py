#!/usr/bin/env python3
"""Exercise the real native Android screens and save screenshots + UI trees."""
import pathlib,subprocess,time,xml.etree.ElementTree as ET,re,os,urllib.request,zipfile,io
root=pathlib.Path(__file__).resolve().parent.parent
dest=root/'ui-captures';dest.mkdir(exist_ok=True)
def adb(*args,**kwargs):
    kwargs.setdefault('timeout',40)
    return subprocess.check_output(['adb',*args],**kwargs)
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
def scroll_has(text):
    # A source below the fold is still present; inspect every visible segment.
    width,height=map(int,re.findall(r'\d+',adb('shell','wm','size').decode())[-2:])
    last=None
    for _ in range(12):
        xml=dump()
        if text in xml:return True
        if xml==last:return False
        last=xml
        adb('shell','input','swipe',str(width//2),str(height*4//5),str(width//2),str(height//3),'250');time.sleep(.3)
    return False
def capture(prefix,name):
    time.sleep(.8);(dest/(prefix+'-'+name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    (dest/(prefix+'-'+name+'.xml')).write_text(dump())
def screens(apk,prefix):
    subprocess.run(['adb','uninstall','com.btsou.search'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    adb('install',str(apk));adb('shell','am','start','-n','com.btsou.search/.MainActivity');time.sleep(3)
    capture(prefix,'home')
    for page in ['收藏','来源','设置']:
        tap(page);capture(prefix,{'收藏':'favorites','来源':'sources','设置':'settings'}[page])
    # New versions hide the control. Exercise the real title gesture and reset.
    xml=dump();toggles=[n for n in ET.fromstring(xml).iter('node') if n.get('class')=='android.widget.Switch' and '成人' in n.get('text','')]
    if prefix=='before':
        assert toggles and toggles[0].get('checked')=='false','Adult switch should default off'
    else:
        assert not toggles,'Adult switch must not appear in Settings'
        tap('搜索')
        assert '直连搜索 · 普通模式' in dump(),'Default mode must be ordinary'
        def title_taps():
            title=next(n for n in ET.fromstring(dump()).iter('node') if n.get('content-desc')=='寻源名称')
            x1,y1,x2,y2=map(int,re.findall(r'\d+',title.get('bounds')))
            x,y=(x1+x2)//2,(y1+y2)//2
            adb('shell','; '.join(['input tap '+str(x)+' '+str(y)]*6));time.sleep(.5)
        title_taps();tap('取消');assert '直连搜索 · 普通模式' in dump(),'Cancel must keep mode off'
        title_taps();tap('我已满18岁，开启');assert '扩展模式' in dump(),'Six taps must unlock'
        capture(prefix,'hidden-enabled')
        tap('来源');assert scroll_has('Sukebei'),'Unlocked source must appear'
        tap('搜索');tap('搜索关键词');adb('shell','input','text','testkeyword')
        title_taps();xml=dump();assert '直连搜索 · 普通模式' in xml and 'testkeyword' not in xml,'Lock must clear query'
        tap('来源');assert 'Sukebei' not in dump(),'Locked source must be hidden (extended sources sort first when present)'
        tap('搜索');title_taps();tap('我已满18岁，开启')
        adb('shell','am','force-stop','com.btsou.search');adb('shell','am','start','-n','com.btsou.search/.MainActivity');time.sleep(2)
        assert '直连搜索 · 普通模式' in dump(),'Process restart must reset mode'
    if prefix=='after':
        for category in ['电影','电视剧','电子书','软件','不限制']:
            current=next(n.get('text') for n in ET.fromstring(dump()).iter('node') if n.get('text','').startswith('分类：'))
            tap(current);tap(category)
            assert '分类：'+category in dump(),'Category selection must remain visible'
        tap('筛选与排序')
        tap('结果内包含词，多个词以空格分隔');adb('shell','input','text','Ubuntu')
        tap('应用');tap('筛选与排序');assert 'Ubuntu' in dump(),'Refine fields must survive reopening'
        tap('重置');tap('筛选与排序');assert 'Ubuntu' not in dump(),'Reset must clear include terms'
        tap('取消');capture(prefix,'filters')
        tap('收藏');tap('搜索收藏');tap('显示全部');capture(prefix,'favorites-tools');tap('搜索')
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

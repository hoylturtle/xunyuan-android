#!/usr/bin/env python3
"""Check native action wiring and run exact parsing expressions on real fixtures.

This is not a replacement for executing Shortcuts on an iPhone.
"""
import collections, html, json, pathlib, plistlib, re
from build_shortcut import build,ADULT,HOSTS,JSON_PATTERN,ROW_PATTERN,TITLE_PATTERN,MAGNET_PATTERN

root=pathlib.Path(__file__).resolve().parent
workflow=build();actions=workflow['WFWorkflowActions'];checks=0
def check(ok,message):
    global checks
    checks+=1
    if not ok:raise AssertionError(message)

seen=set();groups=[];counts=collections.Counter()
def walk(value):
    if isinstance(value,dict):
        if value.get('WFSerializationType')=='WFTextTokenString':
            v=value['Value'];encoded=v['string'].encode('utf-16-le');ranges=v['attachmentsByRange'];check(v['string'].count('\ufffc')==len(ranges),'placeholder count')
            for key,ref in ranges.items():
                pos,length=map(int,re.findall(r'\d+',key));check(length==1 and encoded[pos*2:pos*2+2]==b'\xfc\xff','UTF16 attachment offset')
        if value.get('Type')=='ActionOutput':check(value.get('OutputUUID') in seen,'forward or missing output reference')
        if value.get('Type')=='Variable' and value.get('VariableName','').startswith('Repeat Item'):
            depth=sum(1 for name,_ in groups if name.endswith('repeat.each'))
            expected='Repeat Item'+(' '+str(depth) if depth>1 else '')
            check(value['VariableName']==expected,'correct nested Repeat Item level')
        for v in value.values():walk(v)
    elif isinstance(value,list):
        for v in value:walk(v)

for a in actions:
    ident=a['WFWorkflowActionIdentifier'];p=a['WFWorkflowActionParameters'];uid=p['UUID'];check(uid not in seen,'unique UUID');walk(p)
    if 'WFControlFlowMode' in p:
        mode=p['WFControlFlowMode'];g=p['GroupingIdentifier']
        if mode==0:groups.append((ident,g))
        else:
            check(bool(groups) and groups[-1]==(ident,g),'properly nested control blocks')
            if mode==2:groups.pop()
    if ident.endswith('text.match'):check('text' in p and 'WFInput' not in p,'Match Text source key')
    if ident.endswith('text.match.getgroup'):check('matches' in p,'Get Group source key')
    if ident.endswith('conditional')and p['WFControlFlowMode']==0:check(p['WFInput'].get('Type')=='Variable','If input wrapper')
    if ident.endswith('choosefromlist'):check(isinstance(p['WFChooseFromListActionSelectMultiple'],bool),'boolean selection')
    seen.add(uid)
check(not groups,'all groups closed')
check(plistlib.loads((root/'dist/Xunyuan.unsigned.shortcut').read_bytes())==workflow,'binary plist round trip')

fixtures=root.parent/'research/probes'
page=(fixtures/'pansearch.txt').read_text();data=json.loads(re.search(JSON_PATTERN,page).group(1))['props']['pageProps']['data']['data']
for name,host in HOSTS.items():
    links=[]
    for record in data:
        for line in re.sub(r'(?i)<br\s*/?>','\n',record['content']).splitlines():
            if not re.search(ADULT,line):links.extend(re.findall(r'(?<=href=")https?://'+host+r'/s/[^"<>\s]+',line))
    check(bool(links),name+' finds actual cloud links');counts[name]=len(links)
nyaa=(fixtures/'nyaa.txt').read_text();rows=re.findall(ROW_PATTERN,nyaa)
check(len(rows)==1,'Nyaa fixture result count')
for row in rows:
    title=html.unescape(re.search(TITLE_PATTERN,row).group(1));magnet=html.unescape(re.search(MAGNET_PATTERN,row).group())
    check('Koha' in title,'title parser');check(magnet.startswith('magnet:?xt=urn:btih:'),'magnet parser');check('&amp;'not in magnet,'HTML ampersands decoded')
check(re.search(ADULT,'SSIS-123 sample') is not None,'adult catalogue flagged')
check(re.search(ADULT,'Python basics and Java development') is None,'normal search allowed')
bad='https://pan.baidu.com.evil.example/s/fake';check(re.search(r'https?://'+HOSTS['全部网盘']+r'/s/',bad)is None,'lookalike host rejected')
report={'checks':checks,'actionCount':len(actions),'fixtureCloudLinkCounts':dict(counts),'fixtureMagnetResults':len(rows),'iphoneRuntimeTested':False}
(root/'dist/test-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False))

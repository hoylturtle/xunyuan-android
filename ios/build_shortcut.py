#!/usr/bin/env python3
"""Build a native Shortcuts workflow; Apple signing is performed on macOS."""
import pathlib, plistlib, uuid, json

ROOT=pathlib.Path(__file__).resolve().parent
ADULT=r'(?i)(成人|色情|无码|無碼|有码|有碼|性爱|性愛|性交|裸体|裸體|巨乳|爆乳|中出|内射|內射|口交|肛交|麻豆|18禁|18\+|hentai|porn|xxx|nsfw|\bsex\b|\b(?:SSIS|SSNI|STARS|IPX|IPZZ|MIDE|MIDV|FSDSS|JUFE|JUQ|MEYD|DASS|ABW|HEYZO)[-_ ]?\d{2,})'
HOSTS={'全部网盘':r'(?:pan\.baidu\.com|pan\.quark\.cn|(?:www\.)?(?:alipan|aliyundrive)\.com)', '百度网盘':r'pan\.baidu\.com','夸克网盘':r'pan\.quark\.cn','阿里云盘':r'(?:www\.)?(?:alipan|aliyundrive)\.com'}
JSON_PATTERN=r'(?s)<script\b[^>]*\bid="__NEXT_DATA__"[^>]*>(.*?)</script>'
ROW_PATTERN=r'(?s)<tr\b[^>]*>(?:(?!</tr>).)*magnet:(?:(?!</tr>).)*</tr>'
TITLE_PATTERN=r'<a\b[^>]*href="/view/[^\"]+"[^>]*title="([^\"]+)"'
MAGNET_PATTERN=r'magnet:\?xt=urn:btih:(?:[a-fA-F0-9]{40}|[A-Z2-7]{32})(?:&(?:amp;)?dn=[^"<>]*)?'

def var(name):return {'Type':'Variable','VariableName':name}
def output(uid):return {'Type':'ActionOutput','OutputUUID':uid,'OutputName':'Result'}
def attach(ref):return {'Value':ref,'WFSerializationType':'WFTextTokenAttachment'}
def token(*parts):
    value='';refs={}
    for p in parts:
        if isinstance(p,dict):refs['{%s, 1}'%(len(value.encode('utf-16-le'))//2)]=p;value+='\ufffc'
        else:value+=str(p)
    return {'Value':{'string':value,'attachmentsByRange':refs},'WFSerializationType':'WFTextTokenString'}

class Builder:
    def __init__(self,allow_adult=False):self.actions=[];self.allow_adult=allow_adult
    def uid(self):return str(uuid.uuid5(uuid.NAMESPACE_URL,'https://github.com/hoylturtle/xunyuan-android/ios/'+str(len(self.actions)))).upper()
    def add(self,name,**params):
        uid=self.uid();params['UUID']=uid;self.actions.append({'WFWorkflowActionIdentifier':'is.workflow.actions.'+name,'WFWorkflowActionParameters':params});return output(uid)
    def text(self,*parts):return self.add('gettext',WFTextActionText=token(*parts))
    def set(self,name,value):return self.add('setvariable',WFVariableName=name,WFInput=attach(value))
    def literal(self,name,value):return self.set(name,self.text(value))
    def match(self,pattern,value):return self.add('text.match',WFMatchTextPattern=token(pattern) if isinstance(pattern,dict) else pattern,text=token(value),WFMatchTextCaseSensitive=True)
    def group(self,matched,index=1):return self.add('text.match.getgroup',WFGetGroupType='Group At Index',WFGroupIndex=index,matches=attach(matched))
    def count(self,value):return self.add('count',WFCountType='Items',WFInput=attach(value))
    def replace(self,pattern,value,replacement=''):
        return self.add('text.replace',WFInput=token(value),WFReplaceTextFind=pattern,WFReplaceTextReplace=replacement,WFReplaceTextRegularExpression=True,WFReplaceTextCaseSensitive=True)
    def first(self,value):return self.add('getitemfromlist',WFInput=attach(value),WFItemSpecifier='First Item')
    def begin_if(self,value,code=100,literal=None):
        g=self.uid();params={'GroupingIdentifier':g,'WFControlFlowMode':0,'WFInput':{'Type':'Variable','Variable':attach(value)},'WFCondition':code}
        if code in [0,1,2,3]:params['WFNumberValue']=str(literal)
        elif code in [4,5,8,9,99,999]:params['WFConditionalActionString']=str(literal)
        self.add('conditional',**params);return g
    def otherwise(self,g):self.add('conditional',GroupingIdentifier=g,WFControlFlowMode=1)
    def end_if(self,g):self.add('conditional',GroupingIdentifier=g,WFControlFlowMode=2)
    def menu(self,prompt,options):g=self.uid();self.add('choosefrommenu',GroupingIdentifier=g,WFControlFlowMode=0,WFMenuPrompt=prompt,WFMenuItems=options);return g
    def case(self,g,title):self.add('choosefrommenu',GroupingIdentifier=g,WFControlFlowMode=1,WFMenuItemTitle=title)
    def end_menu(self,g):self.add('choosefrommenu',GroupingIdentifier=g,WFControlFlowMode=2)
    def repeat(self,items):g=self.uid();self.add('repeat.each',GroupingIdentifier=g,WFControlFlowMode=0,WFInput=attach(items));return g
    def end_repeat(self,g):self.add('repeat.each',GroupingIdentifier=g,WFControlFlowMode=2)
    def stop(self,message):self.add('showresult',Text=message);self.add('exit')
    def add_safe_result(self,label,link,source):
        candidate=self.text(label,'\n',link);self.set('候选结果',candidate)
        if self.allow_adult:
            self.add('appendvariable',WFVariableName='搜索结果',WFInput=attach(var('候选结果')))
        else:
            clean=self.begin_if(self.count(self.match(ADULT,source)),0,1)
            self.add('appendvariable',WFVariableName='搜索结果',WFInput=attach(var('候选结果')))
            self.end_if(clean)

def show_result_page(b):
    # The navigation item is never treated as a resource URL.
    b.add('appendvariable',WFVariableName='显示结果',WFInput=attach(b.text('下一页 ▶')))
    chosen=b.add('choosefromlist',WFInput=attach(var('显示结果')),WFChooseFromListActionPrompt=token('第 ',var('页码'),' 批 · 最多15条，选择资源或下一页'),WFChooseFromListActionSelectMultiple=False);b.set('已选结果',chosen)
    resource=b.begin_if(chosen,5,'下一页 ▶')
    # Selected text contains exactly the resource link; match only expected protocols.
    selected=b.add('getitemfromlist',WFInput=attach(b.match(r'(?:https?://'+HOSTS['全部网盘']+r'/s/[^\s<>]+|magnet:\?xt=urn:btih:[^\s<>]+)',chosen)),WFItemSpecifier='Last Item');b.set('已选链接',selected)
    g=b.menu('如何处理这个结果？',['打开链接（尝试唤起应用）','复制链接与提取码','复制磁链／网盘地址'])
    b.case(g,'打开链接（尝试唤起应用）')
    code=b.match(r'(?:提取码|提取碼|密码)[：:\s]*([a-zA-Z0-9]{4,8})',var('已选结果'));has=b.begin_if(b.count(code),2,0);b.add('setclipboard',WFInput=attach(b.first(b.group(code))),WFLocalOnly=True);b.end_if(has)
    b.add('openurl',WFInput=token(var('已选链接')))
    b.case(g,'复制链接与提取码');b.add('setclipboard',WFInput=attach(var('已选结果')),WFLocalOnly=True);b.add('showresult',Text='已复制结果和提取码。')
    b.case(g,'复制磁链／网盘地址');b.add('setclipboard',WFInput=attach(var('已选链接')),WFLocalOnly=True);b.add('showresult',Text='已复制链接。');b.end_menu(g)
    b.add('exit')
    b.end_if(resource)

def build(variant='standard'):
    allow_adult=variant=='adult';shortcut_name='寻源 Plus' if allow_adult else '寻源'
    b=Builder(allow_adult);b.add('comment',WFCommentActionText='寻源 iOS 0.3.0：每屏最多15条，选择下一页继续。手机直连，无服务器。普通版过滤已识别成人关键词且不含成人来源；Plus 版单独发布，仅供成年人使用。分类可能误判，来源可能失效。选择结果后可复制或尝试打开；网盘 Universal Link 是否唤起应用由 iOS 与网盘应用决定。')
    if allow_adult:
        b.add('alert',WFAlertActionTitle='寻源 Plus · 确认已满18岁',WFAlertActionMessage='此版本允许成人关键词与相关来源。仅供年满18岁的用户使用，取消则停止本次运行。',WFAlertActionCancelButtonShown=True)
    q=b.add('ask',WFAskActionPrompt='寻源：输入资源关键词',WFInputType='Text',WFAskActionDefaultAnswer='');q=b.replace(r'^\s+|\s+$',q);b.set('关键词',q)
    empty=b.begin_if(q,101);b.stop('关键词不能为空。');b.end_if(empty)
    if not allow_adult:
        blocked=b.begin_if(b.count(b.match(ADULT,q)),2,0);b.stop('寻源普通版不支持成人资源搜索。');b.end_if(blocked)
    encoded=b.add('urlencode',WFInput=attach(q),WFEncodeMode='Encode');b.set('编码关键词',encoded)
    sources=list(HOSTS)+(['磁力（成人来源）'] if allow_adult else ['磁力（Nyaa）']);g=b.menu('选择搜索来源',sources)
    for name,host in HOSTS.items():
        b.case(g,name);b.literal('模式','pan');b.literal('网盘匹配',r'(?<=href=")https?://'+host+r'/s/[^"<>\s]+')
    b.case(g,sources[-1]);b.literal('模式','bt');b.end_menu(g)
    # Source pages are requested only after the user asks for the next page.
    offsets=b.add('detect.dictionary',WFInput=attach(b.text(json.dumps({str(i):str((i-1)*10) for i in range(1,101)}))))
    pages=b.add('text.split',text=token(b.text('\n'.join(map(str,range(1,101))))),WFTextSeparator='New Lines')
    navigation=b.repeat(pages);b.set('页码',var('Repeat Item'))
    b.set('偏移',b.add('getvalueforkey',WFDictionaryKey=token(var('页码')),WFGetDictionaryValueType='Value',WFInput=attach(offsets)))
    b.set('搜索结果',b.add('list',WFItems=[]))
    mode=b.begin_if(var('模式'),4,'pan')
    url=b.text('https://www.pansearch.me/search?keyword=',var('编码关键词'),'&offset=',var('偏移'));b.set('搜索地址',url)
    page=b.add('downloadurl',WFURL=token(url),WFHTTPMethod='GET');html=b.add('detect.text',WFInput=attach(page));matches=b.match(JSON_PATTERN,html)
    missing=b.begin_if(b.count(matches),0,1);b.stop('网盘来源未返回可解析的数据，可能需要验证或站点已改版。可稍后重试。');b.end_if(missing)
    raw=b.first(b.group(matches));dictionary=b.add('detect.dictionary',WFInput=attach(raw));records=b.add('getvalueforkey',WFDictionaryKey='props.pageProps.data.data',WFGetDictionaryValueType='Value',WFInput=attach(dictionary))
    exhausted=b.begin_if(b.count(records),0,1);b.stop('已经没有更多结果。');b.end_if(exhausted)
    repeat=b.repeat(records)
    content=b.add('getvalueforkey',WFDictionaryKey='content',WFGetDictionaryValueType='Value',WFInput=attach(var('Repeat Item 2')))
    # Each resource line generally carries its own title, link and extraction code.
    content=b.replace(r'(?i)<br\s*/?>',content,'\n');lines=b.add('text.split',text=token(content),WFTextSeparator='New Lines')
    inner=b.repeat(lines);b.set('资源行',b.text(var('Repeat Item 3')))
    links=b.match(var('网盘匹配'),var('资源行'));has=b.begin_if(b.count(links),2,0)
    label=b.replace(r'(?s)<a\b.*$',var('资源行'));label=b.replace(r'<[^>]*>',label);b.set('资源标题',label)
    perlink=b.repeat(links);b.set('资源链接',b.text(var('Repeat Item 4')))
    # Only retain an extraction code from this line, never another resource's code.
    passes=b.match(r'(?:提取码|提取碼|密码)[：:\s]*([a-zA-Z0-9]{4,8})',var('资源行'));haspass=b.begin_if(b.count(passes),2,0)
    passcode=b.first(b.group(passes));b.set('结果标题',b.text(var('资源标题'),'  提取码：',passcode))
    b.otherwise(haspass);b.set('结果标题',var('资源标题'));b.end_if(haspass)
    b.add_safe_result(var('结果标题'),var('资源链接'),var('资源行'))
    b.end_repeat(perlink);b.end_if(has);b.end_repeat(inner);b.end_repeat(repeat)
    b.otherwise(mode)
    b.literal('磁力源','https://sukebei.nyaa.si/?f=0&c=0_0&q=' if allow_adult else 'https://nyaa.si/?f=0&c=0_0&q=')
    url=b.text(var('磁力源'),var('编码关键词'),'&p=',var('页码'));b.set('搜索地址',url)
    page=b.add('downloadurl',WFURL=token(url),WFHTTPMethod='GET');html=b.add('detect.text',WFInput=attach(page));rows=b.match(ROW_PATTERN,html)
    exhausted=b.begin_if(b.count(rows),0,1);b.stop('已经没有更多结果。');b.end_if(exhausted)
    repeat=b.repeat(rows);b.set('磁力行',b.text(var('Repeat Item 2')))
    title=b.first(b.group(b.match(TITLE_PATTERN,var('磁力行'))));title=b.replace(r'&amp;',title,'&');b.set('资源标题',title)
    magnet=b.first(b.match(MAGNET_PATTERN,var('磁力行')));magnet=b.replace(r'&amp;',magnet,'&');b.set('资源链接',magnet)
    b.add_safe_result(var('资源标题'),var('资源链接'),var('资源标题'));b.end_repeat(repeat);b.end_if(mode)
    # Keep every result in source order: do not discard items after the first 15.
    b.set('显示结果',b.add('list',WFItems=[]))
    results=b.repeat(var('搜索结果'))
    b.add('appendvariable',WFVariableName='显示结果',WFInput=attach(var('Repeat Item 2')))
    full=b.begin_if(b.count(var('显示结果')),2,14)
    show_result_page(b);b.set('显示结果',b.add('list',WFItems=[]));b.end_if(full)
    b.end_repeat(results)
    remainder=b.begin_if(b.count(var('显示结果')),2,0);show_result_page(b);b.end_if(remainder)
    empty_page=b.begin_if(b.count(var('搜索结果')),0,1)
    g=b.menu('当前来源页没有符合筛选条件的结果',['下一页','结束搜索'])
    b.case(g,'下一页');b.case(g,'结束搜索');b.add('exit');b.end_menu(g);b.end_if(empty_page)
    b.end_repeat(navigation)
    b.stop('已浏览100个来源页，请更换关键词继续搜索。')
    workflow={'WFWorkflowName':shortcut_name,'WFWorkflowActions':b.actions,'WFWorkflowClientVersion':'2302.0.4','WFWorkflowClientRelease':'18.0','WFWorkflowMinimumClientVersion':900,'WFWorkflowMinimumClientVersionString':'900','WFWorkflowIcon':{'WFWorkflowIconStartColor':431817727,'WFWorkflowIconGlyphNumber':61440},'WFWorkflowTypes':[],'WFWorkflowInputContentItemClasses':[],'WFWorkflowOutputContentItemClasses':[],'WFWorkflowImportQuestions':[],'WFWorkflowHasOutputFallback':False,'WFQuickActionSurfaces':[]}
    dist=ROOT/'dist';dist.mkdir(exist_ok=True)
    stem='Xunyuan-Plus' if allow_adult else 'Xunyuan'
    (dist/(stem+'.unsigned.shortcut')).write_bytes(plistlib.dumps(workflow,fmt=plistlib.FMT_BINARY,sort_keys=False))
    (dist/(stem+'.workflow.json')).write_text(json.dumps(workflow,ensure_ascii=False,indent=2)+'\n')
    print('Built',shortcut_name,'native workflow:',len(b.actions),'actions')
    return workflow

if __name__=='__main__':build();build('adult')

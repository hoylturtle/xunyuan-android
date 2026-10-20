package com.btsou.search;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final int GREEN=Color.rgb(23,105,80),INK=Color.rgb(27,47,37),MUTED=Color.rgb(101,115,106),BG=Color.rgb(246,248,243),LINE=Color.rgb(226,232,222);
    private SharedPreferences prefs;
    private AppUpdates updates;
    private String bundledCatalogRaw="";
    private SourceCatalog officialCatalog;
    private final ExecutorService catalogPool=Executors.newSingleThreadExecutor();
    private boolean checkingSources=false;
    private List<Source> sources=new ArrayList<>();
    private final SearchEngine engine=new SearchEngine();
    private final ExecutorService detailPool=Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,Result> results=new LinkedHashMap<>(),favorites=new LinkedHashMap<>();
    private final Map<String,String> statuses=new LinkedHashMap<>();
    private final ArrayList<String> history=new ArrayList<>();
    private LinearLayout root,body,resultsBox,nav,progressArea;
    private TextView summary,notice;
    private EditText query;
    private Button searchButton,moreButton;
    private ProgressBar progress;
    private ScrollView resultsScroll;
    private boolean showBackupSources=false;
    private boolean adult=false,busy=false,destroyed=false,refreshQueued=false;
    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final TapSequence brandTaps=new TapSequence();
    private String screen="搜索",filter="全部",term="",draft="";
    private String contentCategory="不限制",sortOrder="默认顺序",includeWords="",excludeWords="",favoriteSearch="";
    private boolean onlyCode=false;
    private int resultPage=0,favoriteCheckToken=0;
    private final Set<String> favoriteSelection=new HashSet<>();
    private java.util.concurrent.Future<?> favoriteCheck;
    private int generation=0,page=1,pending=0,total=0,success=0,invalidLinks=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);prefs=getSharedPreferences("btsou",MODE_PRIVATE);adult=false;prefs.edit().remove("adult").apply();
        try{
            bundledCatalogRaw=read(getAssets().open("sources.json"),512*1024);officialCatalog=SourceCatalog.choose(bundledCatalogRaw,prefs.getString("officialSources",""));String bundled=officialCatalog.raw;String custom=prefs.getString("customSources","");
            try{sources=Source.read(custom.isEmpty()?bundled:custom);}catch(Exception e){sources=Source.read(bundled);toast("自定义来源配置无效，已使用内置来源");}
            if(custom.isEmpty())applySourceDefaults(officialCatalog);
            JSONArray a=new JSONArray(prefs.getString("favorites","[]"));for(int i=0;i<a.length();i++){Result r=Result.from(a.getJSONObject(i));if(Result.safeUrl(r.url))favorites.put(r.key(),r);}
            a=new JSONArray(prefs.getString("history","[]"));for(int i=0;i<a.length();i++)history.add(a.getString(i));
        }catch(Exception e){toast("配置加载失败："+SearchEngine.message(e));}
        if(b!=null){draft=b.getString("draft","");screen=b.getString("screen","搜索");if(Result.adultText(draft))draft="";}
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(Color.WHITE);
        render();
        updates=new AppUpdates(this);
        if(prefs.getBoolean("autoSourceUpdates",true)&&System.currentTimeMillis()-prefs.getLong("lastSourceAttempt",0)>SourceCatalog.INTERVAL)checkSources(false);
        if(prefs.getBoolean("autoUpdates",true)&&System.currentTimeMillis()-prefs.getLong("lastUpdateCheck",0)>86400000L){prefs.edit().putLong("lastUpdateCheck",System.currentTimeMillis()).apply();updates.check(false);}
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("draft",query==null?draft:query.getText().toString());out.putString("screen",screen);}
    @Override protected void onResume(){super.onResume();if(updates!=null)updates.resumeInstall();}
    @Override protected void onDestroy(){destroyed=true;uiHandler.removeCallbacksAndMessages(null);generation++;favoriteCheckToken++;if(favoriteCheck!=null)favoriteCheck.cancel(true);engine.close();detailPool.shutdownNow();catalogPool.shutdownNow();if(updates!=null)updates.close();super.onDestroy();}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setIncludeFontPadding(false);v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setLineSpacing(dp(2),1.06f);return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void gap(LinearLayout l,int height){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private void padded(LinearLayout v,int n){v.setPadding(dp(n),dp(n),dp(n),dp(n));}
    private GradientDrawable surface(int fill,int radius,boolean border){GradientDrawable d=shape(fill,radius);if(border)d.setStroke(dp(1),LINE);return d;}
    private void interactive(View v,int fill,int radius){v.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(207,225,211)),surface(fill,radius,false),shape(Color.WHITE,radius)));v.setFocusable(true);}
    private Button button(String label,boolean filled,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(filled?Color.WHITE:GREEN);interactive(b,filled?GREEN:Color.rgb(234,241,231),13);b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(7),dp(12),dp(7));b.setOnClickListener(v->action.run());return b;}
    private View iconButton(String icon,String label,boolean selected,Runnable action){FrameLayout box=new FrameLayout(this);interactive(box,selected?Color.rgb(224,237,219):Color.rgb(244,247,241),12);UiIcon image=new UiIcon(this,icon,GREEN);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER);box.addView(image,ip);box.setContentDescription(label);box.setTooltipText(label);box.setOnClickListener(v->action.run());box.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(v,info);info.setClassName(Button.class.getName());}});return box;}
    private TextView badge(String label,int foreground,int background){TextView v=text(label,11,foreground,true);v.setPadding(dp(9),dp(5),dp(9),dp(5));v.setBackground(shape(background,7));return v;}
    private LinearLayout card(){LinearLayout v=column();v.setBackground(surface(Color.WHITE,20,true));return v;}
    private void pageTitle(LinearLayout list,String title,String subtitle){list.addView(text(title,26,INK,true));gap(list,8);list.addView(text(subtitle,12,MUTED,false));gap(list,22);}
    private void section(LinearLayout list,String title){gap(list,18);TextView v=text(title,12,MUTED,true);v.setPadding(dp(3),0,0,dp(9));list.addView(v);}
    private void divider(LinearLayout list){View line=new View(this);line.setBackgroundColor(LINE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(dp(16),0,dp(16),0);list.addView(line,lp);}
    private void tintSwitch(Switch sw){sw.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{GREEN,Color.WHITE}));sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Color.rgb(177,211,184),Color.rgb(207,216,205)}));}
    private void settingRow(LinearLayout list,String icon,String title,String subtitle,Runnable action){LinearLayout line=row();line.setPadding(dp(15),dp(15),dp(14),dp(15));line.setMinimumHeight(dp(68));UiIcon image=new UiIcon(this,icon,GREEN);line.addView(image,new LinearLayout.LayoutParams(dp(23),dp(23)));LinearLayout labels=column();LinearLayout.LayoutParams labelp=new LinearLayout.LayoutParams(0,-2,1);labelp.setMargins(dp(13),0,dp(10),0);line.addView(labels,labelp);labels.addView(text(title,15,INK,false));if(!subtitle.isEmpty()){gap(labels,5);labels.addView(text(subtitle,11,MUTED,false));}line.addView(new UiIcon(this,"chevron",MUTED),new LinearLayout.LayoutParams(dp(16),dp(16)));interactive(line,Color.TRANSPARENT,16);line.setOnClickListener(v->action.run());list.addView(line);}
    private void render(){
        query=null;root=column();root.setBackgroundColor(BG);setContentView(root);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);getWindow().getDecorView().getWindowInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return in;});}
        LinearLayout header=row();header.setPadding(dp(22),dp(14),dp(22),dp(15));FrameLayout brand=new FrameLayout(this);brand.setBackground(shape(GREEN,13));UiIcon symbol=new UiIcon(this,"compass",Color.WHITE);brand.addView(symbol,new FrameLayout.LayoutParams(dp(26),dp(26),Gravity.CENTER));header.addView(brand,new LinearLayout.LayoutParams(dp(43),dp(43)));LinearLayout labels=column();LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(11);header.addView(labels,hp);labels.addView(brandTitle(23));gap(labels,3);labels.addView(text("磁力与网盘，轻松找到",10,MUTED,false));header.addView(badge("●  直连搜索",GREEN,Color.rgb(231,239,223)));root.addView(header);
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        View topLine=new View(this);topLine.setBackgroundColor(LINE);root.addView(topLine,new LinearLayout.LayoutParams(-1,dp(1)));
        nav=row();nav.setBackgroundColor(Color.WHITE);nav.setPadding(dp(12),dp(8),dp(12),dp(8));String[] tabs={"搜索","收藏","来源","设置"},icons={"search","bookmark","sources","settings"};
        for(int i=0;i<tabs.length;i++){final String tab=tabs[i];boolean selected=tab.equals(screen);LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(0,dp(6),0,dp(4));UiIcon glyph=new UiIcon(this,icons[i],selected?GREEN:MUTED);item.addView(glyph,new LinearLayout.LayoutParams(dp(23),dp(23)));gap(item,5);TextView caption=text(tab,11,selected?GREEN:MUTED,selected);caption.setGravity(Gravity.CENTER);item.addView(caption);if(selected)item.setBackground(shape(Color.rgb(233,241,226),15));item.setContentDescription(tab);item.setFocusable(true);item.setOnClickListener(v->{if(query!=null)draft=query.getText().toString();screen=tab;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(57),1);lp.setMargins(dp(4),0,dp(4),0);nav.addView(item,lp);}
        root.addView(nav);
        if("搜索".equals(screen))renderSearch();else if("收藏".equals(screen))renderFavorites();else if("来源".equals(screen))renderSources();else renderSettings();
    }
    private void renderSearch(){
        LinearLayout controls=column();controls.setPadding(dp(20),dp(4),dp(20),0);
        LinearLayout input=row();input.setPadding(dp(13),dp(5),dp(5),dp(5));input.setBackground(surface(Color.WHITE,17,true));input.addView(new UiIcon(this,"search",MUTED),new LinearLayout.LayoutParams(dp(21),dp(21)));
        query=new EditText(this);query.setSingleLine(true);query.setHint("搜索你想找的资源");query.setTextSize(15);query.setTextColor(INK);query.setHintTextColor(MUTED);query.setPadding(dp(10),0,dp(5),0);query.setBackgroundColor(Color.TRANSPARENT);query.setText(draft);query.setSelectAllOnFocus(false);query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);query.setContentDescription("搜索关键词");input.addView(query,new LinearLayout.LayoutParams(0,dp(46),1));
        View clear=iconButton("close","清空关键词",false,()->query.setText(""));LinearLayout.LayoutParams clearp=new LinearLayout.LayoutParams(dp(36),dp(44));clearp.rightMargin=dp(4);input.addView(clear,clearp);clear.setVisibility(draft.isEmpty()?View.GONE:View.VISIBLE);query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){clear.setVisibility(s.length()==0?View.GONE:View.VISIBLE);}public void afterTextChanged(android.text.Editable e){}});
        searchButton=button(busy?"停止":"搜索",true,()->{if(busy)stop();else start(false);});input.addView(searchButton,new LinearLayout.LayoutParams(dp(65),dp(44)));controls.addView(input);gap(controls,10);
        query.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){start(false);return true;}return false;});
        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);LinearLayout chips=row();for(String f:new String[]{"全部","网盘","磁力","百度","夸克","迅雷","115","阿里","其他网盘"}){Button b=button(f,f.equals(filter),()->{draft=query.getText().toString();filter=f;resultPage=0;render();});b.setTextSize(12);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));lp.setMargins(0,0,dp(7),0);chips.addView(b,lp);}hsv.addView(chips);controls.addView(hsv);gap(controls,10);
        LinearLayout tools=row();Button categoryButton=button("分类："+contentCategory,false,()->new AlertDialog.Builder(this).setTitle("资源分类（按标题识别）").setItems(ResultTools.CATEGORIES,(d,index)->{draft=query.getText().toString();contentCategory=ResultTools.CATEGORIES[index];resultPage=0;render();}).show());tools.addView(categoryButton,new LinearLayout.LayoutParams(0,dp(38),1));Button refine=button("筛选与排序",false,this::showRefine);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(38),1);rp.leftMargin=dp(8);tools.addView(refine,rp);controls.addView(tools);gap(controls,6);
        LinearLayout state=row();UiIcon shield=new UiIcon(this,"shield",adult?Color.rgb(155,103,47):GREEN);state.addView(shield,new LinearLayout.LayoutParams(dp(14),dp(14)));TextView protection=text(adult?"扩展模式":"直连搜索 · 普通模式",10,adult?Color.rgb(155,103,47):MUTED,false);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-2,1);pp.leftMargin=dp(5);state.addView(protection,pp);TextView sourcesLink=text(activeSources().size()+" 个来源  ›",11,GREEN,true);sourcesLink.setPadding(dp(10),dp(7),0,dp(7));sourcesLink.setOnClickListener(v->{draft=query.getText().toString();screen="来源";render();});state.addView(sourcesLink);controls.addView(state);gap(controls,9);
        summary=text("",14,INK,true);controls.addView(summary);gap(controls,6);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));controls.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));notice=text("",11,MUTED,false);notice.setPadding(0,dp(3),0,dp(10));controls.addView(notice);body.addView(controls);
        resultsScroll=new ScrollView(this);resultsScroll.setClipToPadding(false);resultsBox=column();resultsBox.setPadding(dp(20),dp(4),dp(20),dp(24));resultsScroll.addView(resultsBox);body.addView(resultsScroll,new LinearLayout.LayoutParams(-1,0,1));updateResults();
    }

    private List<Source> activeSources(){List<Source> list=new ArrayList<>();for(Source s:sources)if(prefs.getBoolean("source:"+s.id,s.defaultEnabled)&&(adult||!s.adult))list.add(s);return list;}
    private void start(boolean next){
        String q=query==null?draft:query.getText().toString().trim();if(next)q=term;
        if(q.isEmpty()){toast("请输入搜索关键词");return;}if(q.length()>150){toast("关键词最多 150 字");return;}
        if(!adult&&Result.adultText(q)){toast("当前搜索范围不支持该关键词");return;}
        if(busy){generation++;engine.cancel();}
        List<Source> list=activeSources();if(next)list.removeIf(s->s.singlePage);if(list.isEmpty()){toast("没有启用可搜索的来源，请到来源页选择");return;}
        if(!next){invalidLinks=0;page=1;resultPage=0;results.clear();term=q;draft=q;history.remove(q);history.add(0,q);while(history.size()>12)history.remove(history.size()-1);prefs.edit().putString("history",new JSONArray(history).toString()).apply();}else page++;
        statuses.clear();for(Source s:list)statuses.put(s.id,"搜索中");busy=true;pending=list.size();total=pending;success=0;int token=++generation;
        InputMethodManager im=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(query!=null)im.hideSoftInputFromWindow(query.getWindowToken(),0);
        final boolean adultSnapshot=adult;final int searchPage=page;
        engine.search(list,term,searchPage,adultSnapshot,new SearchEngine.Listener(){
            public void removed(Source s,Result r){runOnUiThread(()->{if(destroyed||generation!=token)return;results.remove(r.key());invalidLinks++;scheduleResults();});}
            public void found(Source s,Result r){runOnUiThread(()->{if(destroyed||generation!=token)return;results.merge(r.key(),r,Result::mergeKnown);scheduleResults();});}
            public void done(Source s,List<Result> found,String error){runOnUiThread(()->{
            if(destroyed||generation!=token)return;
            pending--;if(error.isEmpty())success++;statuses.put(s.id,error.isEmpty()?"第 "+searchPage+" 页 · "+found.size()+" 条":error);
            prefs.edit().putString("last:"+s.id,statuses.get(s.id)).apply();
            if(pending==0)busy=false;
            if("搜索".equals(screen))updateResults();else if("来源".equals(screen))render();
        });}});
        if("搜索".equals(screen))updateResults();
    }
    private void stop(){generation++;engine.cancel();busy=false;pending=0;for(String id:new ArrayList<>(statuses.keySet()))if("搜索中".equals(statuses.get(id)))statuses.put(id,"已停止");if("搜索".equals(screen))updateResults();}
    private boolean matches(Result r){return ResultTools.accepts(r,contentCategory,includeWords,excludeWords)&&(!onlyCode||(!r.kind.equals("bt")&&!r.password.isEmpty()))&&("全部".equals(filter)||("磁力".equals(filter)&&r.kind.equals("bt"))||("网盘".equals(filter)&&!r.kind.equals("bt"))||filter.equals(r.kind)||("其他网盘".equals(filter)&&!Arrays.asList("bt","百度","夸克","迅雷","115","阿里").contains(r.kind)));}
    private void scheduleResults(){if(refreshQueued||destroyed)return;refreshQueued=true;uiHandler.postDelayed(()->{refreshQueued=false;if(!destroyed&&"搜索".equals(screen))updateResults();},150);}
    private void updateResults(){
        if(resultsBox==null||!"搜索".equals(screen))return;final int previousY=resultsScroll==null?0:resultsScroll.getScrollY();
        searchButton.setText(busy?"停止":"搜索");progress.setVisibility(busy?View.VISIBLE:View.GONE);progress.setMax(Math.max(1,total));progress.setProgress(total-pending);
        List<Result> filtered=new ArrayList<>();for(Result r:results.values())if(r.visible(adult)&&matches(r))filtered.add(r);ResultTools.sort(filtered,sortOrder,term);if(!filtered.isEmpty())resultPage=Math.min(resultPage,(filtered.size()-1)/15);else resultPage=0;
        long count=filtered.size();summary.setVisibility(term.isEmpty()?View.GONE:View.VISIBLE);notice.setVisibility(term.isEmpty()?View.GONE:View.VISIBLE);
        summary.setText("找到 "+count+" 条结果"+(invalidLinks>0?" · 已隐藏 "+invalidLinks+" 条失效链接":""));notice.setText(busy?"正在查询 · "+(total-pending)+" / "+total+" 个来源完成":"第 "+page+" 页 · "+success+" 个来源已响应 · 查看状态 ›");notice.setOnClickListener(v->showStatus());resultsBox.removeAllViews();
        if(term.isEmpty()){
            LinearLayout hero=column();hero.setPadding(dp(23),dp(24),dp(23),dp(24));hero.setBackground(shape(Color.rgb(232,239,218),23));LinearLayout h=row();LinearLayout words=column();words.addView(text("多源搜索 · 简单一点",11,GREEN,true));gap(words,12);TextView big=text("好资源，\n从这里开始。",29,INK,true);big.setLineSpacing(dp(4),1.08f);words.addView(big);h.addView(words,new LinearLayout.LayoutParams(0,-2,1));FrameLayout emblem=new FrameLayout(this);emblem.setBackground(shape(Color.rgb(218,230,200),100));UiIcon icon=new UiIcon(this,"compass",GREEN);emblem.addView(icon,new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.CENTER));h.addView(emblem,new LinearLayout.LayoutParams(dp(94),dp(94)));hero.addView(h);gap(hero,18);hero.addView(text("磁力链接 / 百度 / 夸克 / 阿里云盘",11,MUTED,false));resultsBox.addView(hero);gap(resultsBox,22);
            resultsBox.addView(text("试试这些关键词",12,MUTED,true));gap(resultsBox,10);LinearLayout suggestions=row();for(String key:new String[]{"Ubuntu","Python"}){Button b=button(key,false,()->{query.setText(key);start(false);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.setMargins(0,0,dp(8),0);suggestions.addView(b,lp);}resultsBox.addView(suggestions);
            List<String> visible=new ArrayList<>();for(String entry:history)if(adult||!Result.adultText(entry))visible.add(entry);
            if(!visible.isEmpty()){gap(resultsBox,24);resultsBox.addView(text("最近搜索",12,MUTED,true));gap(resultsBox,10);for(int i=0;i<Math.min(visible.size(),6);i+=2){LinearLayout rr=row();for(int k=i;k<Math.min(i+2,visible.size());k++){final String entry=visible.get(k);Button b=button(entry,false,()->{query.setText(entry);start(false);});b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setSingleLine(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1);lp.setMargins(0,0,dp(8),dp(7));rr.addView(b,lp);}resultsBox.addView(rr);}}
            gap(resultsBox,22);LinearLayout info=row();info.addView(new UiIcon(this,"shield",MUTED),new LinearLayout.LayoutParams(dp(17),dp(17)));TextView note=text("搜索直连来源，收藏只保存在本机",10,MUTED,false);note.setPadding(dp(7),0,0,0);info.addView(note);resultsBox.addView(info);
        }else{
            for(int i=resultPage*15;i<Math.min((resultPage+1)*15,filtered.size());i++)addCard(resultsBox,filtered.get(i));
            if(count>0){LinearLayout pages=row();Button previous=button("上一页",false,()->{resultPage--;resultsScroll.scrollTo(0,0);updateResults();});previous.setEnabled(resultPage>0);pages.addView(previous,new LinearLayout.LayoutParams(0,dp(42),1));TextView label=text((resultPage+1)+" / "+((count+14)/15),12,MUTED,false);label.setGravity(Gravity.CENTER);pages.addView(label,new LinearLayout.LayoutParams(0,-2,1));Button next=button("下一页",false,()->{resultPage++;resultsScroll.scrollTo(0,0);updateResults();});next.setEnabled((resultPage+1)*15<count);pages.addView(next,new LinearLayout.LayoutParams(0,dp(42),1));resultsBox.addView(pages);}
            if(count==0){LinearLayout empty=card();empty.setGravity(Gravity.CENTER);padded(empty,26);empty.addView(new UiIcon(this,busy?"sources":"search",GREEN),new LinearLayout.LayoutParams(dp(44),dp(44)));gap(empty,17);empty.addView(text(busy?"正在为你寻找…":"这次还没有找到",19,INK,true));gap(empty,10);TextView message=text(busy?"结果会陆续出现，请稍等片刻":"试试其他关键词、分类或搜索来源",12,MUTED,false);message.setGravity(Gravity.CENTER);empty.addView(message);resultsBox.addView(empty);}
            if(!busy){gap(resultsBox,12);moreButton=button("继续加载来源下一批",true,()->start(true));resultsBox.addView(moreButton,new LinearLayout.LayoutParams(-1,dp(48)));gap(resultsBox,9);resultsBox.addView(button("查看来源连接状态",false,this::showStatus),new LinearLayout.LayoutParams(-1,dp(44)));}
        }
        if(previousY>0&&resultsScroll!=null)resultsScroll.post(()->resultsScroll.scrollTo(0,previousY));
    }
    private void addCard(LinearLayout list,Result r){
        LinearLayout card=card();padded(card,16);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(12);list.addView(card,cp);
        LinearLayout top=row();int accent=r.kind.equals("百度")?Color.rgb(62,105,173):r.kind.equals("阿里")?Color.rgb(113,91,163):GREEN;top.addView(badge(r.kind.equals("bt")?"磁力链接":r.kind+"网盘",accent,Color.rgb(238,243,234)));TextView source=text(r.source,10,MUTED,false);source.setGravity(Gravity.END);top.addView(source,new LinearLayout.LayoutParams(0,-2,1));card.addView(top);gap(card,12);
        if("收藏".equals(screen)){CheckBox select=new CheckBox(this);select.setText("选择收藏");select.setChecked(favoriteSelection.contains(r.key()));select.setOnCheckedChangeListener((v,on)->{if(on)favoriteSelection.add(r.key());else favoriteSelection.remove(r.key());});card.addView(select);}
        TextView title=text(r.title,16,INK,true);title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setTextIsSelectable(true);title.setLineSpacing(dp(3),1.1f);card.addView(title);gap(card,10);
        List<String> details=new ArrayList<>();details.add(ResultTools.category(r));if(!r.collectionGroup.isEmpty())details.add("分组 "+r.collectionGroup);if(!r.note.isEmpty())details.add("备注 "+r.note);if(!r.size.isEmpty())details.add(r.size);if(!r.date.isEmpty())details.add(r.date.substring(0,Math.min(10,r.date.length())));if(!r.password.isEmpty())details.add("提取码 "+r.password);if(!r.kind.equals("bt"))details.add(r.linkMessage);if("收藏".equals(screen)&&r.linkCheckedAt>0)details.add("检查于 "+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new java.util.Date(r.linkCheckedAt)));if(r.adult)details.add("成人");String meta=String.join("  ·  ",details);if(!meta.trim().isEmpty()){card.addView(text(meta,11,MUTED,false));gap(card,13);}
        LinearLayout actions=row();Button open=button(r.kind.equals("bt")?"打开磁链":"打开网盘",true,()->action(r,false));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,dp(46),1.35f);op.rightMargin=dp(8);actions.addView(open,op);Button copy=button("复制链接",false,()->action(r,true));LinearLayout.LayoutParams cpy=new LinearLayout.LayoutParams(0,dp(46),1);cpy.rightMargin=dp(8);actions.addView(copy,cpy);
        boolean saved=favorites.containsKey(r.key());View fav=iconButton("bookmark",saved?"取消收藏":"收藏资源",saved,()->{if(favorites.containsKey(r.key()))favorites.remove(r.key());else favorites.put(r.key(),r);saveFavorites();if("收藏".equals(screen))render();else updateResults();});actions.addView(fav,new LinearLayout.LayoutParams(dp(46),dp(46)));card.addView(actions);
        if(!r.pageUrl.isEmpty()){gap(card,10);LinearLayout detail=row();TextView link=text("查看来源详情",10,MUTED,false);detail.addView(link,new LinearLayout.LayoutParams(0,-2,1));detail.addView(new UiIcon(this,"arrow",MUTED),new LinearLayout.LayoutParams(dp(15),dp(15)));detail.setPadding(0,dp(7),0,dp(4));detail.setMinimumHeight(dp(32));detail.setContentDescription("查看来源详情");detail.setOnClickListener(v->open(r.pageUrl));card.addView(detail);}
    }

    private void action(Result r,boolean copy){
        if(!r.visible(adult))return;
        if(!r.kind.equals("bt")||r.url.startsWith("magnet:")){if(copy)copy(r.url+(r.password.isEmpty()?"":"\n提取码："+r.password));else{if(!r.password.isEmpty()){copy(r.password);toast("提取码已复制");}if(r.kind.equals("bt"))open(r.url);else openCloud(r.url);}return;}
        final int token=generation;toast("正在读取磁链…");detailPool.submit(()->{
            try{String magnet=SearchEngine.resolveMagnet(r);runOnUiThread(()->{if(destroyed||generation!=token||!r.visible(adult))return;String oldKey=r.key();boolean saved=favorites.remove(oldKey)!=null;boolean shown=results.remove(oldKey)!=null;r.url=magnet;if(saved){favorites.put(r.key(),r);saveFavorites();}if(shown)results.putIfAbsent(r.key(),r);if(copy)copy(magnet);else open(magnet);if("搜索".equals(screen))updateResults();});}
            catch(Exception e){runOnUiThread(()->{if(destroyed||generation!=token)return;new AlertDialog.Builder(this).setTitle("未取得磁链").setMessage(SearchEngine.message(e)).setPositiveButton("打开来源页",(d,w)->open(r.pageUrl)).setNegativeButton("关闭",null).show();});}
        });
    }
    private void showStatus(){StringBuilder b=new StringBuilder();for(Source s:sources)if(statuses.containsKey(s.id))b.append(s.name).append("：").append(statuses.get(s.id)).append("\n\n");new AlertDialog.Builder(this).setTitle("本轮来源状态").setMessage(b.length()==0?"还没有开始搜索":b.toString()).setPositiveButton("关闭",null).show();}
    private void renderFavorites(){
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();padded(list,20);long visible=favorites.values().stream().filter(r->r.visible(adult)&&ResultTools.favoriteMatches(r,favoriteSearch)).count();pageTitle(list,"我的收藏",visible+" 条收藏 · 喜欢的资源，留在这里");
        HorizontalScrollView bar=new HorizontalScrollView(this);LinearLayout actions=row();
        actions.addView(button("搜索收藏",false,()->askFavoriteSearch()));actions.addView(button("寻找替代",false,()->{List<Result> rows=selectedFavorites();if(rows.size()!=1){toast("请选择一条收藏");return;}draft=rows.get(0).title;screen="搜索";render();start(false);}));actions.addView(button("分组备注",false,()->editFavoriteSelection()));actions.addView(button("移除选中",false,()->removeFavoriteSelection()));actions.addView(button(favoriteCheck!=null?"停止复检":"复检收藏",false,()->recheckFavorites()));bar.addView(actions);list.addView(bar);gap(list,12);
        if(!favoriteSearch.isEmpty()){list.addView(text("筛选："+favoriteSearch,12,MUTED,false));gap(list,10);}
        for(Result r:favorites.values())if(r.visible(adult)&&ResultTools.favoriteMatches(r,favoriteSearch))addCard(list,r);
        if(visible==0){LinearLayout empty=card();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(25),dp(45),dp(25),dp(45));empty.addView(new UiIcon(this,"bookmark",GREEN),new LinearLayout.LayoutParams(dp(50),dp(50)));gap(empty,21);empty.addView(text("把好资源留给以后",19,INK,true));gap(empty,11);TextView hint=text("点击搜索结果上的收藏图标，\n下次就能在这里找到它。",12,MUTED,false);hint.setGravity(Gravity.CENTER);empty.addView(hint);gap(empty,22);empty.addView(button("去搜索资源",true,()->{screen="搜索";render();}),new LinearLayout.LayoutParams(dp(150),dp(46)));list.addView(empty);}
        gap(list,16);list.addView(text("收藏仅保存在本机，可在设置中备份与恢复。",11,MUTED,false));scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
    }
    private void renderSources(){
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();padded(list,20);pageTitle(list,"搜索来源","选择你信任的来源，让每一次搜索更合适");LinearLayout stats=row();stats.setPadding(dp(16),dp(15),dp(16),dp(15));stats.setBackground(shape(Color.rgb(232,239,218),16));stats.addView(new UiIcon(this,"sources",GREEN),new LinearLayout.LayoutParams(dp(26),dp(26)));TextView connected=text("已启用 "+activeSources().size()+" 个来源",15,GREEN,true);connected.setPadding(dp(12),0,0,0);stats.addView(connected);list.addView(stats);
        gap(list,12);LinearLayout updatesCard=card();Switch autoSources=new Switch(this);autoSources.setText("自动更新官方来源（每天检查）");autoSources.setSingleLine(false);autoSources.setMinHeight(dp(48));autoSources.setChecked(prefs.getBoolean("autoSourceUpdates",true));tintSwitch(autoSources);autoSources.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("autoSourceUpdates",on).apply());padded(updatesCard,14);updatesCard.addView(autoSources,new LinearLayout.LayoutParams(-1,-2));gap(updatesCard,8);
        String sourceInfo=prefs.getString("customSources","").isEmpty()?"官方来源 v"+officialCatalog.version+" · "+officialCatalog.updated:"自定义来源模式，自动更新暂停";updatesCard.addView(text(sourceInfo+"\n"+prefs.getString("sourceUpdateStatus","内置来源可离线使用"),11,MUTED,false));
        gap(updatesCard,12);updatesCard.addView(button(checkingSources?"正在检查来源…":"检查来源更新",false,()->checkSources(true)),new LinearLayout.LayoutParams(-1,dp(48)));gap(updatesCard,12);updatesCard.addView(button("回退上次来源",false,this::rollbackSources),new LinearLayout.LayoutParams(-1,dp(48)));if(!prefs.getString("customSources","").isEmpty()){gap(updatesCard,12);updatesCard.addView(button("使用官方来源",false,()->{prefs.edit().remove("customSources").apply();sources=new ArrayList<>(officialCatalog.sources);applySourceDefaults(officialCatalog);stop();render();checkSources(true);}),new LinearLayout.LayoutParams(-1,dp(48)));}list.addView(updatesCard);
        section(list,"正在使用");List<Source> ordered=new ArrayList<>(sources);ordered.sort(Comparator.comparing(source->!source.adult));boolean has=false;for(Source source:ordered)if((adult||!source.adult)&&prefs.getBoolean("source:"+source.id,source.defaultEnabled)){sourceCard(list,source);has=true;}if(!has)list.addView(text("暂无启用来源，请从备用来源中选择。",12,MUTED,false));
        int backups=0;for(Source source:sources)if((adult||!source.adult)&&!prefs.getBoolean("source:"+source.id,source.defaultEnabled))backups++;
        final int unused=backups;gap(list,12);list.addView(button((showBackupSources?"收起":"查看")+"备用来源（"+unused+"）",false,()->{showBackupSources=!showBackupSources;render();}),new LinearLayout.LayoutParams(-1,dp(46)));
        if(showBackupSources){section(list,"备用来源 · 连接状态可能变化");for(Source source:sources)if((adult||!source.adult)&&!prefs.getBoolean("source:"+source.id,source.defaultEnabled))sourceCard(list,source);}
        section(list,"管理来源");LinearLayout management=card();settingRow(management,"upload","导入来源配置","从文件添加自己的搜索源",()->chooseFile(42));divider(management);settingRow(management,"history","恢复默认来源","重置来源配置与开关",()->new AlertDialog.Builder(this).setTitle("恢复默认来源？").setMessage("将替换自定义配置并重置来源开关，收藏不受影响。").setPositiveButton("恢复",(d,w)->{stop();SharedPreferences.Editor e=prefs.edit().remove("customSources");for(String key:prefs.getAll().keySet())if(key.startsWith("source:")||key.startsWith("last:"))e.remove(key);e.apply();try{sources=new ArrayList<>(officialCatalog.sources);}catch(Exception ex){toast(SearchEngine.message(ex));}render();}).setNegativeButton("取消",null).show());list.addView(management);gap(list,16);list.addView(text("已实测来源默认启用，历史问题来源默认停用；明确失效的网盘链接已隐藏，验证码或超时会标注暂未确认。",11,MUTED,false));scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
    }
    private void sourceCard(LinearLayout list,Source source){
        LinearLayout card=card();padded(card,15);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(9);list.addView(card,cp);Switch toggle=new Switch(this);toggle.setText(source.name);toggle.setTextColor(INK);toggle.setTextSize(15);toggle.setPadding(0,dp(5),0,dp(5));toggle.setChecked(prefs.getBoolean("source:"+source.id,source.defaultEnabled));tintSwitch(toggle);toggle.setContentDescription(source.name+"搜索源开关");toggle.setOnCheckedChangeListener((b,on)->{prefs.edit().putBoolean("source:"+source.id,on).apply();if(busy)stop();render();});card.addView(toggle);gap(card,7);
        LinearLayout description=row();description.addView(badge(source.kind.equals("bt")?"磁力":"网盘",GREEN,Color.rgb(237,243,232)));TextView host=text(Uri.parse(source.prefix).getHost(),11,MUTED,false);host.setPadding(dp(8),0,0,0);host.setSingleLine(true);host.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);description.addView(host,new LinearLayout.LayoutParams(0,-2,1));card.addView(description);
        String last=prefs.getString("last:"+source.id,"");gap(card,10);TextView status=text(last.isEmpty()?"连接状态将在搜索后更新":last,10,last.contains("条")?GREEN:MUTED,false);status.setPadding(0,dp(2),0,dp(2));status.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(source.name).setMessage(source.prefix+"\n\n"+source.note+(last.isEmpty()?"":"\n\n上次查询："+last)).setPositiveButton("关闭",null).show());card.addView(status);
    }
    private void renderSettings(){
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();padded(list,20);pageTitle(list,"设置","按照自己的习惯，调整寻源");
        LinearLayout version=card();padded(version,18);LinearLayout brand=row();FrameLayout brandBox=new FrameLayout(this);brandBox.setBackground(shape(GREEN,14));brandBox.addView(new UiIcon(this,"compass",Color.WHITE),new FrameLayout.LayoutParams(dp(29),dp(29),Gravity.CENTER));brand.addView(brandBox,new LinearLayout.LayoutParams(dp(49),dp(49)));LinearLayout names=column();LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=dp(13);brand.addView(names,np);names.addView(brandTitle(20));gap(names,5);names.addView(text("版本 "+appVersion()+" · 轻松找到好资源",11,MUTED,false));version.addView(brand);gap(version,16);version.addView(button("检查应用更新",true,()->updates.check(true)),new LinearLayout.LayoutParams(-1,dp(46)));list.addView(version);
        section(list,"数据与备份");LinearLayout data=card();settingRow(data,"history","清除搜索历史","清理最近搜索的关键词",()->{history.clear();prefs.edit().remove("history").apply();toast("历史已清除");});divider(data);settingRow(data,"upload","导出收藏备份","保存一份本地收藏文件",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"寻源-收藏.json");startActivityForResult(i,43);});divider(data);settingRow(data,"download","导入收藏备份","恢复已保存的收藏",()->chooseFile(44));list.addView(data);
        section(list,"应用与更新");LinearLayout app=card();LinearLayout autoBox=column();padded(autoBox,16);Switch auto=new Switch(this);auto.setText("自动检查更新");auto.setTextSize(15);auto.setTextColor(INK);auto.setPadding(0,dp(5),0,dp(5));tintSwitch(auto);auto.setChecked(prefs.getBoolean("autoUpdates",true));auto.setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean("autoUpdates",on).apply());autoBox.addView(auto);gap(autoBox,5);autoBox.addView(text("启动时每天检查一次，安装前会请你确认。",11,MUTED,false));app.addView(autoBox);divider(app);settingRow(app,"link","GitHub 项目","查看源码、说明与发布版本",()->open(AppUpdates.REPO_URL));divider(app);settingRow(app,"sources","开源参考与许可","依赖和搜索来源说明",()->new AlertDialog.Builder(this).setTitle("开源参考与许可").setMessage("寻源为独立 Java 实现。\n\nHTML 解析：jsoup 1.18.3（MIT）。\n技术调研：TorrentSearch、PanSou、qBittorrent 搜索插件。\n\n完整依赖许可与来源说明见 GitHub 仓库。").setPositiveButton("关闭",null).show());list.addView(app);gap(list,20);TextView privacy=text("搜索直连第三方来源，数据与收藏仅保存在本机。\n没有账号、广告 SDK 或统计 SDK。",11,MUTED,false);privacy.setGravity(Gravity.CENTER);list.addView(privacy);gap(list,8);scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
    }

    private void applySourceDefaults(SourceCatalog catalog){
        if(prefs.getLong("sourceDefaultsVersion",0)>=catalog.version)return;
        SharedPreferences.Editor edit=prefs.edit().putLong("sourceDefaultsVersion",catalog.version);
        for(Source source:catalog.sources)if(!source.defaultEnabled&&!source.adult)edit.putBoolean("source:"+source.id,false);edit.apply();
    }
    private void checkSources(boolean manual){
        if(checkingSources){if(manual)toast("正在检查来源更新");return;}if(!prefs.getString("customSources","").isEmpty()){if(manual)toast("自定义来源不会被覆盖；请先切换到官方来源");return;}
        if(officialCatalog==null)return;checkingSources=true;prefs.edit().putLong("lastSourceAttempt",System.currentTimeMillis()).apply();final SourceCatalog current=officialCatalog;
        if(manual)toast("正在检查官方来源…");catalogPool.submit(()->{try{SourceCatalog.Update update=SourceCatalog.download(current);runOnUiThread(()->{if(destroyed)return;checkingSources=false;
            if(!prefs.getString("customSources","").isEmpty())return;
            SharedPreferences.Editor edit=prefs.edit();String stamp=new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new java.util.Date());String message="已检查 "+stamp+" · 来源 v"+update.catalog.version;
            if(update.changed){edit.putString("previousOfficialSources",current.raw).putString("officialSources",update.catalog.raw);if(busy&&!manual)message+=" 已下载，下次启动生效";else{stop();officialCatalog=update.catalog;sources=new ArrayList<>(officialCatalog.sources);applySourceDefaults(officialCatalog);results.clear();statuses.clear();message+=" 已更新";}}else edit.putString("officialSources",update.catalog.raw);
            edit.putString("sourceUpdateStatus",message).apply();if(manual)toast(message);if("来源".equals(screen))render();
        });}catch(Exception e){runOnUiThread(()->{if(destroyed)return;checkingSources=false;String message=SearchEngine.message(e);prefs.edit().putString("sourceUpdateStatus",message).apply();if(manual)toast(message);if("来源".equals(screen))render();});}});
    }
    private void rollbackSources(){
        if(checkingSources){toast("请等来源检查结束");return;}String old=prefs.getString("previousOfficialSources","");if(old.isEmpty()){toast("暂无上一个在线来源版本");return;}
        try{SourceCatalog previous=new SourceCatalog(old);if(previous.version<new SourceCatalog(bundledCatalogRaw).version){toast("不能回退到当前应用内置来源之前");return;}new AlertDialog.Builder(this).setTitle("回退到来源 v"+previous.version+"？").setMessage("会暂停自动来源更新，收藏不受影响。").setPositiveButton("回退",(d,w)->{stop();prefs.edit().putString("officialSources",previous.raw).remove("customSources").putBoolean("autoSourceUpdates",false).putLong("sourceDefaultsVersion",0).putString("sourceUpdateStatus","已回退，自动更新已暂停").apply();officialCatalog=previous;sources=new ArrayList<>(previous.sources);applySourceDefaults(previous);results.clear();render();}).setNegativeButton("取消",null).show();}catch(Exception e){toast("上次配置不可用，当前配置已保留");}
    }

    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "";}}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setContentDescription(hint);e.setText(value);e.setTextSize(14);return e;}
    private void showRefine(){
        LinearLayout p=column();padded(p,20);EditText include=field("结果内包含词，多个词以空格分隔",includeWords),exclude=field("排除词，多个词以空格分隔",excludeWords);p.addView(include);p.addView(exclude);Spinner order=new Spinner(this);order.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,ResultTools.ORDERS));order.setSelection(Arrays.asList(ResultTools.ORDERS).indexOf(sortOrder));p.addView(order);CheckBox code=new CheckBox(this);code.setText("只看有提取码的网盘");code.setChecked(onlyCode);p.addView(code);p.addView(text("分类依据标题线索；无法识别的资源仅在不限制中展示。",11,MUTED,false));
        new AlertDialog.Builder(this).setTitle("筛选与排序").setView(p).setPositiveButton("应用",(d,w)->{draft=query.getText().toString();includeWords=include.getText().toString();excludeWords=exclude.getText().toString();sortOrder=order.getSelectedItem().toString();onlyCode=code.isChecked();resultPage=0;render();}).setNeutralButton("重置",(d,w)->{draft=query.getText().toString();includeWords="";excludeWords="";sortOrder="默认顺序";contentCategory="不限制";filter="全部";onlyCode=false;resultPage=0;render();}).setNegativeButton("取消",null).show();
    }
    private void askFavoriteSearch(){EditText e=field("搜索标题、分组或备注",favoriteSearch);new AlertDialog.Builder(this).setTitle("搜索收藏").setView(e).setPositiveButton("搜索",(d,w)->{favoriteSearch=e.getText().toString().trim();favoriteSelection.clear();render();}).setNeutralButton("显示全部",(d,w)->{favoriteSearch="";favoriteSelection.clear();render();}).setNegativeButton("取消",null).show();}
    private List<Result> selectedFavorites(){List<Result> rows=new ArrayList<>();for(Result r:favorites.values())if(favoriteSelection.contains(r.key())&&r.visible(adult)&&ResultTools.favoriteMatches(r,favoriteSearch))rows.add(r);return rows;}
    private void editFavoriteSelection(){List<Result> rows=selectedFavorites();if(rows.isEmpty()){toast("请先选择收藏");return;}LinearLayout p=column();padded(p,16);EditText group=field("分组",rows.get(0).collectionGroup),note=field("备注",rows.size()==1?rows.get(0).note:"");p.addView(group);p.addView(note);CheckBox changeNote=new CheckBox(this);changeNote.setText("同时替换备注（多选时）");changeNote.setChecked(rows.size()==1);p.addView(changeNote);new AlertDialog.Builder(this).setTitle("编辑 "+rows.size()+" 条收藏").setView(p).setPositiveButton("保存",(d,w)->{for(Result r:rows){r.collectionGroup=group.getText().toString().trim();if(changeNote.isChecked())r.note=note.getText().toString().trim();}saveFavorites();render();}).setNegativeButton("取消",null).show();}
    private void removeFavoriteSelection(){List<Result> rows=selectedFavorites();if(rows.isEmpty()){toast("请先选择收藏");return;}new AlertDialog.Builder(this).setTitle("移除 "+rows.size()+" 条收藏？").setPositiveButton("移除",(d,w)->{for(Result r:rows){favorites.remove(r.key());favoriteSelection.remove(r.key());}saveFavorites();render();}).setNegativeButton("取消",null).show();}
    private void recheckFavorites(){
        if(favoriteCheck!=null){favoriteCheckToken++;favoriteCheck.cancel(true);favoriteCheck=null;toast("已停止复检");render();return;}
        List<Result> rows=selectedFavorites();if(rows.isEmpty())for(Result r:favorites.values())if(r.visible(adult)&&ResultTools.favoriteMatches(r,favoriteSearch))rows.add(r);rows.removeIf(r->!LinkHealth.cloud(r.url));if(rows.isEmpty()){toast("当前没有可复检的网盘收藏");return;}
        final int token=++favoriteCheckToken;favoriteCheck=detailPool.submit(()->{LinkHealth health=new LinkHealth();int count=0;for(Result r:rows){if(Thread.currentThread().isInterrupted())break;LinkHealth.Check check=health.check(r.url);if(Thread.currentThread().isInterrupted())break;final int done=++count;runOnUiThread(()->{if(destroyed||token!=favoriteCheckToken||!favorites.containsKey(r.key()))return;r.linkState=check.state;r.linkMessage=check.message;r.linkCheckedAt=check.checkedAt;saveFavorites();if(done==rows.size()){favoriteCheck=null;toast("收藏复检完成，失效记录已保留");}if("收藏".equals(screen))render();});}});toast("正在复检 "+rows.size()+" 条网盘收藏");render();
    }

    private TextView brandTitle(int size){TextView name=text(adult?"寻源 ·":"寻源",size,INK,true);name.setContentDescription("寻源名称");name.setFocusable(true);name.setOnClickListener(v->onBrandTap());return name;}
    private void onBrandTap(){
        if(!brandTaps.tap(android.os.SystemClock.elapsedRealtimeNanos()))return;
        if(adult){setAdult(false);toast("已恢复普通模式");return;}
        new AlertDialog.Builder(this).setTitle("启用扩展模式").setMessage("扩展搜索可能包含成人内容，仅供年满18周岁的用户使用。").setPositiveButton("我已满18岁，开启",(d,w)->{setAdult(true);toast("扩展模式已开启");}).setNegativeButton("取消",null).show();
    }
    private void setAdult(boolean on){adult=on;prefs.edit().remove("adult").apply();stop();results.clear();term="";draft="";includeWords="";excludeWords="";favoriteSearch="";favoriteSelection.clear();if(favoriteCheck!=null){favoriteCheckToken++;favoriteCheck.cancel(true);favoriteCheck=null;}statuses.clear();render();}
    private void saveFavorites(){JSONArray a=new JSONArray();for(Result r:favorites.values())try{a.put(r.json());}catch(Exception ignored){}prefs.edit().putString("favorites",a.toString()).apply();}
    private void chooseFile(int code){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});startActivityForResult(i,code);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            if(req==42){String raw=read(getContentResolver().openInputStream(data.getData()),512*1024);List<Source> incoming=Source.read(raw);new AlertDialog.Builder(this).setTitle("导入 "+incoming.size()+" 个来源？").setMessage("将替换当前来源配置。请仅导入你信任的地址，搜索关键词会发送给启用的来源。").setPositiveButton("导入",(d,w)->{stop();sources=incoming;SharedPreferences.Editor edit=prefs.edit().putString("customSources",raw);for(String key:prefs.getAll().keySet())if(key.startsWith("source:")||key.startsWith("last:"))edit.remove(key);edit.apply();render();}).setNegativeButton("取消",null).show();}
            else if(req==43){JSONArray a=new JSONArray();for(Result r:favorites.values())a.put(r.json());try(OutputStream os=getContentResolver().openOutputStream(data.getData())){if(os==null)throw new IOException("无法写入文件");os.write(a.toString(2).getBytes("UTF-8"));}toast("收藏已导出");}
            else if(req==44){JSONArray a=new JSONArray(read(getContentResolver().openInputStream(data.getData()),2*1024*1024));if(a.length()>3000)throw new IOException("收藏数量超过 3000");LinkedHashMap<String,Result> imported=new LinkedHashMap<>();for(int n=0;n<a.length();n++){Result r=Result.from(a.getJSONObject(n));if(Result.safeUrl(r.url)&&!r.title.isEmpty())imported.put(r.key(),r);}for(Map.Entry<String,Result> e:imported.entrySet())favorites.putIfAbsent(e.getKey(),e.getValue());saveFavorites();toast("已导入 "+imported.size()+" 条收藏");render();}
        }catch(Exception e){toast("操作失败："+SearchEngine.message(e));}
    }
    private static String read(InputStream stream,int max)throws Exception{if(stream==null)throw new IOException("无法读取文件");try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("文件过大");out.write(b,0,n);}return out.toString("UTF-8");}}
    private void copy(String value){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("寻源",value));toast("已复制");}
    private void openCloud(String value){
        if(!Result.safeUrl(value)){toast("链接格式不受支持");return;}
        String app=CloudApps.packageFor(value);
        if(!app.isEmpty()){
            try{
                getPackageManager().getPackageInfo(app,0);
                // Keep the entire share URL, including password parameters and fragments.
                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app));
                return;
            }catch(android.content.pm.PackageManager.NameNotFoundException e){
                toast("未安装对应网盘 APP，使用网页打开");
            }catch(ActivityNotFoundException e){
                toast("当前网盘 APP 不支持此分享链接，使用网页打开");
            }catch(SecurityException e){
                toast("网盘 APP 未允许打开此链接，使用网页打开");
            }
        }
        open(value);
    }
    private void open(String value){if(!Result.safeUrl(value)){toast("链接格式不受支持");return;}try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException e){toast(value.startsWith("magnet:")?"请先安装支持磁力链接的下载器":"没有可打开此链接的应用");}catch(Exception e){toast("无法打开链接");}}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}

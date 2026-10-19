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
    private final int GREEN=Color.rgb(19,107,87),INK=Color.rgb(25,44,37),MUTED=Color.rgb(105,121,112),BG=Color.rgb(246,248,244),LINE=Color.rgb(224,232,222);
    private SharedPreferences prefs;
    private AppUpdates updates;
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
    private boolean adult=false,busy=false,destroyed=false;
    private String screen="搜索",filter="全部",term="",draft="";
    private int generation=0,page=1,pending=0,total=0,success=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);prefs=getSharedPreferences("btsou",MODE_PRIVATE);adult=prefs.getBoolean("adult",false);
        try{
            String bundled=read(getAssets().open("sources.json"),512*1024);String custom=prefs.getString("customSources","");
            try{sources=Source.read(custom.isEmpty()?bundled:custom);}catch(Exception e){sources=Source.read(bundled);toast("自定义来源配置无效，已使用内置来源");}
            JSONArray a=new JSONArray(prefs.getString("favorites","[]"));for(int i=0;i<a.length();i++){Result r=Result.from(a.getJSONObject(i));if(Result.safeUrl(r.url))favorites.put(r.key(),r);}
            a=new JSONArray(prefs.getString("history","[]"));for(int i=0;i<a.length();i++)history.add(a.getString(i));
        }catch(Exception e){toast("配置加载失败："+SearchEngine.message(e));}
        if(b!=null){draft=b.getString("draft","");screen=b.getString("screen","搜索");}
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(Color.WHITE);
        render();
        updates=new AppUpdates(this);
        if(prefs.getBoolean("autoUpdates",true)&&System.currentTimeMillis()-prefs.getLong("lastUpdateCheck",0)>86400000L){prefs.edit().putLong("lastUpdateCheck",System.currentTimeMillis()).apply();updates.check(false);}
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("draft",query==null?draft:query.getText().toString());out.putString("screen",screen);}
    @Override protected void onResume(){super.onResume();if(updates!=null)updates.resumeInstall();}
    @Override protected void onDestroy(){destroyed=true;generation++;engine.close();detailPool.shutdownNow();if(updates!=null)updates.close();super.onDestroy();}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void gap(LinearLayout l,int height){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private Button button(String label,boolean filled,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(filled?Color.WHITE:GREEN);b.setBackground(shape(filled?GREEN:Color.rgb(232,242,232),13));b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(13),dp(7),dp(13),dp(7));b.setOnClickListener(v->action.run());return b;}
    private void padded(LinearLayout v,int n){v.setPadding(dp(n),dp(n),dp(n),dp(n));}
    private void render(){
        query=null;
        root=column();root.setBackgroundColor(BG);setContentView(root);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);getWindow().getDecorView().getWindowInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return in;});}
        LinearLayout header=column();header.setPadding(dp(20),dp(18),dp(20),dp(12));
        LinearLayout mark=row();TextView logo=text("寻源",26,GREEN,true);mark.addView(logo);TextView badge=text("  DIRECT / 直连",11,MUTED,true);mark.addView(badge);header.addView(mark);
        gap(header,5);header.addView(text("搜磁力，也搜网盘。",13,MUTED,false));root.addView(header);
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        nav=row();nav.setBackgroundColor(Color.WHITE);nav.setPadding(dp(10),dp(8),dp(10),dp(8));
        for(String tab:new String[]{"搜索","收藏","来源","设置"}){
            Button b=button(tab,tab.equals(screen),()->{if(query!=null)draft=query.getText().toString();screen=tab;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(3),0,dp(3),0);nav.addView(b,lp);
        }
        root.addView(nav);
        if("搜索".equals(screen))renderSearch();else if("收藏".equals(screen))renderFavorites();else if("来源".equals(screen))renderSources();else renderSettings();
    }
    private void renderSearch(){
        LinearLayout controls=column();controls.setPadding(dp(20),dp(6),dp(20),0);
        LinearLayout input=row();input.setPadding(dp(12),dp(4),dp(4),dp(4));input.setBackground(shape(Color.WHITE,18));
        query=new EditText(this);query.setSingleLine(true);query.setHint("资源名称、关键词…");query.setTextSize(16);query.setTextColor(INK);query.setHintTextColor(MUTED);query.setBackgroundColor(Color.TRANSPARENT);query.setText(draft);query.setSelectAllOnFocus(false);query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);query.setContentDescription("搜索关键词");
        input.addView(query,new LinearLayout.LayoutParams(0,dp(48),1));searchButton=button(busy?"停止":"搜索",true,()->{if(busy)stop();else start(false);});input.addView(searchButton,new LinearLayout.LayoutParams(dp(70),dp(44)));controls.addView(input);gap(controls,12);
        query.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){start(false);return true;}return false;});
        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);LinearLayout chips=row();
        for(String f:new String[]{"全部","磁力","百度","夸克","阿里","其他网盘"}){Button b=button(f,f.equals(filter),()->{draft=query.getText().toString();filter=f;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));lp.setMargins(0,0,dp(6),0);chips.addView(b,lp);}hsv.addView(chips);controls.addView(hsv);gap(controls,10);
        LinearLayout state=row();TextView protection=text(adult?"成人搜索 · 已开启":"成人搜索 · 已关闭",12,adult?Color.rgb(165,94,37):GREEN,true);state.addView(protection,new LinearLayout.LayoutParams(0,-2,1));TextView mode=text("手机直连 · 无自建服务器",11,MUTED,false);state.addView(mode);controls.addView(state);gap(controls,12);
        summary=text("",13,INK,true);controls.addView(summary);gap(controls,5);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));controls.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));gap(controls,8);
        notice=text("",11,MUTED,false);controls.addView(notice);gap(controls,6);body.addView(controls);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);resultsBox=column();resultsBox.setPadding(dp(20),dp(4),dp(20),dp(20));scroll.addView(resultsBox);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));updateResults();
    }
    private List<Source> activeSources(){List<Source> list=new ArrayList<>();for(Source s:sources)if(prefs.getBoolean("source:"+s.id,s.defaultEnabled)&&(adult||!s.adult))list.add(s);return list;}
    private void start(boolean next){
        String q=query==null?draft:query.getText().toString().trim();if(next)q=term;
        if(q.isEmpty()){toast("请输入搜索关键词");return;}if(q.length()>150){toast("关键词最多 150 字");return;}
        if(!adult&&Result.adultText(q)){toast("请先在设置中开启成人资源搜索");return;}
        if(busy){generation++;engine.cancel();}
        List<Source> list=activeSources();if(next)list.removeIf(s->s.singlePage);if(list.isEmpty()){toast("没有启用可搜索的来源，请到来源页选择");return;}
        if(!next){page=1;results.clear();term=q;draft=q;history.remove(q);history.add(0,q);while(history.size()>12)history.remove(history.size()-1);prefs.edit().putString("history",new JSONArray(history).toString()).apply();}else page++;
        statuses.clear();for(Source s:list)statuses.put(s.id,"搜索中");busy=true;pending=list.size();total=pending;success=0;int token=++generation;
        InputMethodManager im=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(query!=null)im.hideSoftInputFromWindow(query.getWindowToken(),0);
        final boolean adultSnapshot=adult;final int searchPage=page;
        engine.search(list,term,searchPage,adultSnapshot,(s,found,error)->runOnUiThread(()->{
            if(destroyed||generation!=token)return;
            pending--;if(error.isEmpty())success++;statuses.put(s.id,error.isEmpty()?"第 "+searchPage+" 页 · "+found.size()+" 条":error);
            prefs.edit().putString("last:"+s.id,statuses.get(s.id)).apply();
            for(Result r:found)if(r.visible(adult))results.putIfAbsent(r.key(),r);
            if(pending==0)busy=false;
            if("搜索".equals(screen))updateResults();else if("来源".equals(screen))render();
        }));
        if("搜索".equals(screen))updateResults();
    }
    private void stop(){generation++;engine.cancel();busy=false;pending=0;for(String id:new ArrayList<>(statuses.keySet()))if("搜索中".equals(statuses.get(id)))statuses.put(id,"已停止");if("搜索".equals(screen))updateResults();}
    private boolean matches(Result r){return "全部".equals(filter)||("磁力".equals(filter)&&r.kind.equals("bt"))||filter.equals(r.kind)||("其他网盘".equals(filter)&&!Arrays.asList("bt","百度","夸克","阿里").contains(r.kind));}
    private void updateResults(){
        if(resultsBox==null||!"搜索".equals(screen))return;
        searchButton.setText(busy?"停止":"搜索");progress.setVisibility(busy?View.VISIBLE:View.GONE);progress.setMax(Math.max(1,total));progress.setProgress(total-pending);
        long count=results.values().stream().filter(r->r.visible(adult)&&matches(r)).count();
        summary.setText(term.isEmpty()?"一个入口，连接多个来源":("“"+term+"” · "+count+" 条结果"));
        notice.setText(term.isEmpty()?"结果按来源返回顺序展示，重复链接自动合并。":busy?("第 "+page+" 页 · "+(total-pending)+" / "+total+" 个来源完成"):"第 "+page+" 页 · "+success+" 个来源响应 · 点击查看来源状态");
        notice.setOnClickListener(v->showStatus());resultsBox.removeAllViews();
        if(term.isEmpty()){
            gap(resultsBox,24);TextView hero=text("找到你想找的。",27,INK,true);resultsBox.addView(hero);gap(resultsBox,10);resultsBox.addView(text("BT 磁力 / 百度网盘 / 夸克 / 阿里云盘",13,MUTED,false));gap(resultsBox,26);
            if(!history.isEmpty()){resultsBox.addView(text("最近搜索",13,MUTED,true));gap(resultsBox,10);for(String h:history)if(adult||!Result.adultText(h)){Button b=button(h,false,()->{query.setText(h);start(false);});resultsBox.addView(b,new LinearLayout.LayoutParams(-1,dp(44)));gap(resultsBox,7);}}
            else {resultsBox.addView(text("试试搜索 ubuntu、Python 或公开课程。",14,MUTED,false));}
        }else {
            for(Result r:results.values())if(r.visible(adult)&&matches(r))addCard(resultsBox,r);
            if(count==0){gap(resultsBox,32);resultsBox.addView(text(busy?"正在查询来源…":"暂时没有匹配结果",21,INK,true));gap(resultsBox,12);resultsBox.addView(text(busy?"结果会陆续出现在这里。":"换个关键词或分类，或查看来源的连接状态。",14,MUTED,false));}
            if(!busy){gap(resultsBox,12);moreButton=button("加载下一页",true,()->start(true));resultsBox.addView(moreButton,new LinearLayout.LayoutParams(-1,dp(46)));gap(resultsBox,10);Button status=button("查看本轮来源状态",false,this::showStatus);resultsBox.addView(status,new LinearLayout.LayoutParams(-1,dp(42)));}
        }
    }
    private void addCard(LinearLayout list,Result r){
        LinearLayout card=column();padded(card,15);card.setBackground(shape(Color.WHITE,16));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);list.addView(card,cp);
        LinearLayout top=row();TextView kind=text(r.kind.equals("bt")?"BT / 磁力":r.kind+"网盘",11,GREEN,true);top.addView(kind,new LinearLayout.LayoutParams(0,-2,1));TextView source=text(r.source,10,MUTED,false);top.addView(source);card.addView(top);gap(card,9);
        TextView title=text(r.title,16,INK,true);title.setMaxLines(4);title.setTextIsSelectable(true);card.addView(title);gap(card,8);
        String meta=r.size+(r.date.isEmpty()?"":"  ·  "+r.date.substring(0,Math.min(10,r.date.length())))+(r.password.isEmpty()?"":"  ·  提取码 "+r.password)+(r.adult?"  ·  成人":"");
        if(!meta.trim().isEmpty()){card.addView(text(meta,11,MUTED,false));gap(card,10);}
        LinearLayout actions=row();Button open=button(r.kind.equals("bt")?"打开磁链":"打开网盘",true,()->action(r,false));Button copy=button("复制",false,()->action(r,true));Button fav=button(favorites.containsKey(r.key())?"已收藏":"收藏",false,()->{if(favorites.containsKey(r.key()))favorites.remove(r.key());else favorites.put(r.key(),r);saveFavorites();if("收藏".equals(screen))render();else updateResults();});
        for(Button b:new Button[]{open,copy,fav}){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.setMargins(0,0,dp(6),0);actions.addView(b,lp);}card.addView(actions);
        if(!r.pageUrl.isEmpty()){TextView details=text("来源详情 ↗",11,MUTED,false);details.setPadding(0,dp(12),0,dp(4));details.setOnClickListener(v->open(r.pageUrl));card.addView(details);}
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
    private void renderFavorites(){ScrollView scroll=new ScrollView(this);LinearLayout list=column();padded(list,20);list.addView(text("我的收藏",24,INK,true));gap(list,8);list.addView(text("仅保存在本机。成人开关关闭时隐藏相关收藏。",12,MUTED,false));gap(list,20);int n=0;for(Result r:favorites.values())if(r.visible(adult)){addCard(list,r);n++;}if(n==0)list.addView(text("还没有可显示的收藏。",15,MUTED,false));scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));}
    private void renderSources(){
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();padded(list,20);list.addView(text("搜索来源",24,INK,true));gap(list,8);list.addView(text(activeSources().size()+" 个来源启用 · 结果不代表分享链接仍有效",12,MUTED,false));gap(list,16);
        for(Source s:sources){
            if(s.adult&&!adult)continue;
            LinearLayout card=column();padded(card,14);card.setBackground(shape(Color.WHITE,15));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);list.addView(card,lp);
            Switch toggle=new Switch(this);toggle.setText(s.name+"  ·  "+(s.kind.equals("bt")?"磁力":"网盘"));toggle.setTextColor(INK);toggle.setTextSize(15);toggle.setChecked(prefs.getBoolean("source:"+s.id,s.defaultEnabled));toggle.setPadding(0,dp(6),0,dp(6));toggle.setOnCheckedChangeListener((b,on)->{prefs.edit().putBoolean("source:"+s.id,on).apply();if(busy)stop();});card.addView(toggle);gap(card,7);
            card.addView(text(Uri.parse(s.prefix).getHost(),12,GREEN,false));gap(card,6);card.addView(text(s.note,11,MUTED,false));
            String last=prefs.getString("last:"+s.id,"");if(!last.isEmpty()){gap(card,7);card.addView(text("上次查询："+last,12,INK,false));}
        }
        gap(list,10);list.addView(button("导入来源 JSON",false,()->chooseFile(42)),new LinearLayout.LayoutParams(-1,dp(44)));gap(list,8);list.addView(button("恢复内置来源",false,()->new AlertDialog.Builder(this).setTitle("恢复内置来源？").setMessage("替换自定义配置并重置来源开关。收藏不受影响。").setPositiveButton("恢复",(d,w)->{stop();SharedPreferences.Editor e=prefs.edit().remove("customSources");for(String k:prefs.getAll().keySet())if(k.startsWith("source:")||k.startsWith("last:"))e.remove(k);e.apply();try{sources=Source.read(read(getAssets().open("sources.json"),512*1024));}catch(Exception ex){toast(SearchEngine.message(ex));}render();}).setNegativeButton("取消",null).show()),new LinearLayout.LayoutParams(-1,dp(44)));
        gap(list,16);list.addView(text("导入配置只包含地址和解析器参数，不执行远程脚本。HTTP 仅对安装包内允许的旧站点开放；新增来源请使用 HTTPS。",12,MUTED,false));scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
    }
    private void renderSettings(){
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();padded(list,20);list.addView(text("设置",24,INK,true));gap(list,20);
        list.addView(button("检查应用更新",true,()->updates.check(true)),new LinearLayout.LayoutParams(-1,dp(46)));gap(list,10);
        Switch auto=new Switch(this);auto.setText("启动时检查更新（每天一次）");auto.setTextSize(14);auto.setTextColor(INK);auto.setChecked(prefs.getBoolean("autoUpdates",true));auto.setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean("autoUpdates",on).apply());list.addView(auto);gap(list,10);
        list.addView(button("GitHub 项目与发布版本",false,()->open(AppUpdates.REPO_URL)),new LinearLayout.LayoutParams(-1,dp(44)));gap(list,20);
        LinearLayout box=column();padded(box,16);box.setBackground(shape(Color.WHITE,16));Switch sw=new Switch(this);sw.setText("成人资源搜索");sw.setTextSize(16);sw.setTextColor(INK);sw.setChecked(adult);sw.setPadding(0,dp(8),0,dp(8));box.addView(sw);gap(box,8);box.addView(text("默认关闭。开启后允许成人关键词和成人来源；关闭时隐藏识别出的成人结果、收藏和历史。关键词过滤可能漏判或误判。",12,MUTED,false));
        sw.setOnCheckedChangeListener((b,on)->{
            if(on&&!adult)new AlertDialog.Builder(this).setTitle("开启成人资源搜索").setMessage("此选项仅供年满 18 周岁的用户使用。确认后启用成人来源及相关搜索。是否开启？").setPositiveButton("我已满 18 岁，开启",(d,w)->setAdult(true)).setNegativeButton("取消",(d,w)->{sw.setOnCheckedChangeListener(null);sw.setChecked(false);render();}).setOnCancelListener(d->render()).show();
            else if(!on&&adult)setAdult(false);
        });list.addView(box);gap(list,20);
        list.addView(button("清除搜索历史",false,()->{history.clear();prefs.edit().remove("history").apply();toast("历史已清除");}),new LinearLayout.LayoutParams(-1,dp(46)));gap(list,10);
        list.addView(button("导出收藏备份",false,()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"寻源-收藏.json");startActivityForResult(i,43);}),new LinearLayout.LayoutParams(-1,dp(46)));gap(list,10);
        list.addView(button("导入收藏备份",false,()->chooseFile(44)),new LinearLayout.LayoutParams(-1,dp(46)));gap(list,25);
        list.addView(text("寻源  0.2.0",16,INK,true));gap(list,10);list.addView(text("Android 8.0+ · 无账号 · 无广告 SDK\n\n关键词直接发给已启用的来源。APP 不运行自建中转服务，不执行来源页面脚本。分享是否有效、是否需要提取码或登录，由对应网盘决定。\n\n联网用于搜索和检查更新，安装权限仅用于应用升级；收藏、历史与开关保存在本机。卸载前请导出收藏。",13,MUTED,false));gap(list,20);
        list.addView(button("开源参考与许可",false,()->new AlertDialog.Builder(this).setTitle("开源与参考").setMessage("HTML 解析：jsoup 1.18.3（MIT）\n\n技术调研：\nprajwalch/TorrentSearch\nfish2018/pansou\nqBittorrent/search-plugins\n\n本项目为独立 Java 实现，未打包这些项目的代码。完整参考链接与 jsoup 许可见源码包。\n\nBTSOU 地址取自 20260818 规则快照，未引入其政治屏蔽词或广告词表。").setPositiveButton("关闭",null).show()),new LinearLayout.LayoutParams(-1,dp(46)));
        scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
    }
    private void setAdult(boolean on){adult=on;prefs.edit().putBoolean("adult",on).apply();stop();results.clear();term="";draft="";statuses.clear();render();}
    private void saveFavorites(){JSONArray a=new JSONArray();for(Result r:favorites.values())try{a.put(r.json());}catch(Exception ignored){}prefs.edit().putString("favorites",a.toString()).apply();}
    private void chooseFile(int code){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});startActivityForResult(i,code);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            if(req==42){String raw=read(getContentResolver().openInputStream(data.getData()),512*1024);List<Source> incoming=Source.read(raw);new AlertDialog.Builder(this).setTitle("导入 "+incoming.size()+" 个来源？").setMessage("将替换当前来源配置。请仅导入你信任的地址，搜索关键词会发送给启用的来源。").setPositiveButton("导入",(d,w)->{stop();sources=incoming;SharedPreferences.Editor edit=prefs.edit().putString("customSources",raw);for(String key:prefs.getAll().keySet())if(key.startsWith("source:")||key.startsWith("last:"))edit.remove(key);edit.apply();render();}).setNegativeButton("取消",null).show();}
            else if(req==43){JSONArray a=new JSONArray();for(Result r:favorites.values())a.put(r.json());try(OutputStream os=getContentResolver().openOutputStream(data.getData())){if(os==null)throw new IOException("无法写入文件");os.write(a.toString(2).getBytes("UTF-8"));}toast("收藏已导出");}
            else if(req==44){JSONArray a=new JSONArray(read(getContentResolver().openInputStream(data.getData()),2*1024*1024));if(a.length()>3000)throw new IOException("收藏数量超过 3000");LinkedHashMap<String,Result> imported=new LinkedHashMap<>();for(int n=0;n<a.length();n++){Result r=Result.from(a.getJSONObject(n));if(Result.safeUrl(r.url)&&!r.title.isEmpty())imported.put(r.key(),r);}favorites.putAll(imported);saveFavorites();toast("已导入 "+imported.size()+" 条收藏");render();}
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

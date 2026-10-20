package com.btsou.search;
import java.util.*;
import org.json.*;
public final class ResultToolsTests {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Result row(String title){Result r=new Result();r.title=title;r.url="https://pan.quark.cn/s/example";return r;}
    public static void main(String[] args)throws Exception{run();}
    public static void run()throws Exception{
        String[][] types={{"蜘蛛侠 1080P BluRay","电影"},{"刺杀小说家 1080p.mkv","电影"},{"某剧 S01E02 1080p WEB-DL","电视剧"},{"权力的游戏S01E02.mkv","电视剧"},{"漫长的季节 全12集","电视剧"},{"三体.epub","电子书"},{"计算机教程.PDF","电子书"},{"Windows教程.epub","电子书"},{"三体 小说全集","电子书"},{"软件安装包.msi","软件"},{"Ubuntu 24.04 ISO","软件"},{"手机应用.APK","软件"},{"三体","未分类"},{"摄影资料.zip","未分类"}};
        for(String[] sample:types)check(ResultTools.category(row(sample[0])).equals(sample[1]),"category: "+sample[0]);
        Result movie=row("蜘蛛侠 1080p 国语 蓝光");check(ResultTools.accepts(movie,"电影","蜘蛛侠 国语","广告"),"combined include");check(!ResultTools.accepts(movie,"电视剧","",""),"exclude wrong category");check(!ResultTools.accepts(movie,"电影","英语",""),"all include terms required");check(!ResultTools.accepts(movie,"不限制","","国语,广告"),"any excluded term blocks");check(ResultTools.accepts(row("三体"),"不限制","",""),"unknown kept unrestricted");check(!ResultTools.accepts(row("三体"),"电子书","",""),"unknown not invented category");
        check(ResultTools.accepts(row("Ｆｉｌｍ 1080P"),"电影","film",""),"full width normalized");
        List<Result> all=new ArrayList<>();for(String[] t:types)all.add(row(t[0]));List<Result> before=new ArrayList<>(all);List<Result> selected=new ArrayList<>();for(Result r:all)if(ResultTools.accepts(r,"电影","",""))selected.add(r);check(all.equals(before)&&selected.size()==2,"reversible filter retains collected rows");
        Result newer=row("Python 新版"),older=row("Python 旧版"),unknown=row("Python 无日期"),invalid=row("invalid date");newer.date="2026-10-20";older.date="2025/2/3";invalid.date="2026-02-30";List<Result> dates=new ArrayList<>(Arrays.asList(unknown,older,newer,invalid));ResultTools.sort(dates,"最新日期优先","");check(dates.equals(Arrays.asList(newer,older,unknown,invalid)),"date chronological, invalid last and stable");
        older.password="1234";List<Result> codes=new ArrayList<>(Arrays.asList(newer,older,unknown));ResultTools.sort(codes,"有提取码优先","");check(codes.equals(Arrays.asList(older,newer,unknown)),"code stable priority");Result exact=row("Python");List<Result> ranked=new ArrayList<>(Arrays.asList(unknown,exact));ResultTools.sort(ranked,"相关度优先","python");check(ranked.get(0)==exact,"exact relevance first");
        movie.collectionGroup="待看";movie.note="国语收藏";Result restored=Result.from(movie.json());check(restored.collectionGroup.equals("待看")&&restored.note.equals("国语收藏"),"metadata backup roundtrip");check(ResultTools.favoriteMatches(restored,"待看 国语"),"favorite fields searchable");check(!ResultTools.favoriteMatches(restored,"英语"),"favorite unmatched");Result legacy=Result.from(new JSONObject().put("title","旧收藏"));check(legacy.note.isEmpty()&&legacy.collectionGroup.isEmpty(),"legacy metadata defaults");
        List<Result> imported=ResultTools.importFavorites(new JSONArray().put(movie.json()).put(new JSONObject().put("title","bad").put("url","javascript:alert(1)")).toString());check(imported.size()==1,"backup rejects unsafe links");Result adult=row("淫荡蜘蛛侠");check(!adult.visible(false),"category tools cannot relax adult gate");Result note=row("普通标题");note.note="色情资源";check(!note.visible(false)&&note.visible(true),"notes respect hidden mode");
        System.out.println("RESULT_TOOLS_TESTS_OK checks="+checks);
    }
}

package com.btsou.search;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Desktop shell; Source, Result and SearchEngine are shared with Android. */
public final class DesktopApp {
    public static final String VERSION="0.1.10";
    private static final Color BG=new Color(246,248,245),GREEN=new Color(35,105,80),INK=new Color(30,47,39),MUTED=new Color(103,119,109);
    private final JFrame frame=new JFrame("寻源 · Windows "+VERSION);
    private final SearchEngine engine=new SearchEngine();
    private final JTextField query=new JTextField();
    private final JComboBox<String> filter=new JComboBox<>(DesktopFilters.TYPES);
    private final JCheckBox onlyWithCode=new JCheckBox("只看有提取码的网盘");
    private boolean adultEnabled=false;
    private final TapSequence titleTaps=new TapSequence();
    private final JLabel title=new JLabel("寻源");
    private final Map<String,JPanel> sourceRows=new LinkedHashMap<>();
    private java.util.function.BooleanSupplier confirmAdult=()->JOptionPane.showConfirmDialog(frame,"扩展搜索可能包含成人内容。请确认已满18岁。","扩展搜索",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION;
    private final JLabel status=new JLabel("输入关键词，开始寻找资源 · 支持提取码筛选"),pageLabel=new JLabel("第 1 页");
    private final JTextArea diagnostics=new JTextArea();
    private final DefaultListModel<Result> resultModel=new DefaultListModel<>(),favoriteModel=new DefaultListModel<>();
    private final JList<Result> resultList=new JList<>(resultModel),favoriteList=new JList<>(favoriteModel);
    private final LinkedHashMap<String,Result> collected=new LinkedHashMap<>(),favorites=new LinkedHashMap<>();
    private final Map<String,JCheckBox> sourceChecks=new LinkedHashMap<>();
    private final List<Source> sources;
    private final JButton previous=new JButton("上一页"),next=new JButton("下一页 →"),search=new JButton("搜索");
    private final AtomicInteger generation=new AtomicInteger();
    private int sourcePage=1,displayPage=0,pending=0; private String activeQuery="",activeFilter="全部资源"; private boolean activeAdult=false,loading=false,browsing=false;
    private final List<Result> visible=new ArrayList<>();
    private final Path dataPath;

    private DesktopApp() throws Exception {
        String appData=System.getenv("LOCALAPPDATA");
        dataPath=Paths.get(appData==null?System.getProperty("user.home"):appData,"Xunyuan","favorites.json");
        String json=new String(Objects.requireNonNull(getClass().getResourceAsStream("/sources.json")).readAllBytes(),StandardCharsets.UTF_8);
        sources=new ArrayList<>(Source.read(json));
        if(sources.stream().noneMatch(s->s.id.equals("sukebei")))sources.add(new Source(new JSONObject().put("id","sukebei").put("name","Sukebei").put("kind","bt").put("parser","nyaa").put("prefix","https://sukebei.nyaa.si/?f=0&c=0_0&q=").put("middle","&p=").put("adult",true).put("enabled",false).put("note","需开启扩展搜索并勾选此来源；可用性取决于网络")));
        java.util.List<Image> icons=new ArrayList<>();for(int size:new int[]{16,32,48,128,256}){java.net.URL icon=getClass().getResource("/icons/icon-"+size+".png");if(icon!=null)icons.add(javax.imageio.ImageIO.read(icon));}frame.setIconImages(icons);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1000,640));frame.setSize(1080,780);frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){generation.incrementAndGet();engine.close();}});
        JPanel root=new JPanel(new BorderLayout(0,16));root.setBackground(BG);root.setBorder(new EmptyBorder(24,28,20,28));frame.setContentPane(root);
        JPanel header=new JPanel(new BorderLayout());header.setOpaque(false);
        title.setFont(title.getFont().deriveFont(Font.BOLD,30f));title.setForeground(GREEN);
        JLabel subtitle=new JLabel("网盘与磁力，一处寻找");subtitle.setForeground(MUTED);
        JPanel branding=new JPanel(new GridLayout(2,1,0,4));branding.setOpaque(false);branding.add(title);branding.add(subtitle);header.add(branding,BorderLayout.WEST);
        JButton release=new JButton("版本与更新");release.addActionListener(e->openAddress("https://github.com/hoylturtle/xunyuan-android/releases?q=windows&expanded=true"));header.add(release,BorderLayout.EAST);root.add(header,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("搜索",searchPanel());tabs.addTab("收藏",favoritesPanel());tabs.addTab("搜索来源",sourcesPanel());tabs.addTab("关于",aboutPanel());root.add(tabs,BorderLayout.CENTER);
        status.setForeground(MUTED);root.add(status,BorderLayout.SOUTH);
        readFavorites();paintFavorites();
        resultList.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)openSelected(resultList);}});
        favoriteList.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)openSelected(favoriteList);}});
        query.addActionListener(e->startSearch());search.addActionListener(e->startSearch());
        filter.addActionListener(e->refilter());onlyWithCode.addActionListener(e->refilter());
        previous.addActionListener(e->{if(displayPage>0){displayPage--;paintResults();}});
        next.addActionListener(e->{if(!browsing||loading&&(displayPage+1)*15>=visible.size())return;if((displayPage+1)*15<visible.size()){displayPage++;paintResults();}else{displayPage++;loadSourcePage();}});
        title.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))onTitleTap();}});
        title.setFocusable(true);title.addKeyListener(new KeyAdapter(){public void keyPressed(KeyEvent e){if(e.getKeyCode()==KeyEvent.VK_SPACE)onTitleTap();}});
        paintResults();
    }
    private void onTitleTap(){
        if(!titleTaps.tap(System.nanoTime()))return;
        if(!adultEnabled&&!confirmAdult.getAsBoolean())return;
        adultEnabled=!adultEnabled;
        generation.incrementAndGet();engine.cancel();loading=false;pending=0;browsing=false;
        activeAdult=false;activeQuery="";query.setText("");diagnostics.setText("");
        collected.clear();visible.clear();displayPage=0;paintResults();paintFavorites();search.setEnabled(true);
        for(Source s:sources)if(s.adult){sourceChecks.get(s.id).setSelected(adultEnabled);JPanel row=sourceRows.get(s.id);row.setVisible(adultEnabled);row.getParent().revalidate();row.getParent().repaint();}
        title.setText(adultEnabled?"寻源 ·":"寻源");
        status.setText(adultEnabled?"扩展模式已开启，请输入关键词":"已恢复普通模式，当前搜索已清空");
    }
    private JPanel searchPanel(){
        JPanel panel=new JPanel(new BorderLayout(0,12));panel.setBorder(new EmptyBorder(18,10,10,10));panel.setOpaque(false);
        JPanel top=new JPanel(new BorderLayout(10,10));top.setOpaque(false);query.setToolTipText("输入资源名称，例如 Ubuntu、Python");query.setFont(query.getFont().deriveFont(17f));query.setPreferredSize(new Dimension(500,42));top.add(query,BorderLayout.CENTER);top.add(search,BorderLayout.EAST);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,10,6));controls.setOpaque(false);controls.add(new JLabel("资源类型"));controls.add(filter);onlyWithCode.setOpaque(false);controls.add(onlyWithCode);
        JPanel inputs=new JPanel(new BorderLayout());inputs.setOpaque(false);inputs.add(top,BorderLayout.NORTH);inputs.add(controls,BorderLayout.SOUTH);panel.add(inputs,BorderLayout.NORTH);
        panel.add(new JScrollPane(configureList(resultList)),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(8,8));footer.setOpaque(false);JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));actions.setOpaque(false);
        JButton open=new JButton("打开资源"),copy=new JButton("复制链接 / 提取码"),save=new JButton("收藏"),origin=new JButton("查看来源");origin.addActionListener(e->{Result r=resultList.getSelectedValue();if(r!=null&&!r.pageUrl.isEmpty())openAddress(r.pageUrl);});open.addActionListener(e->openSelected(resultList));copy.addActionListener(e->copySelected(resultList));save.addActionListener(e->saveSelected());actions.add(open);actions.add(copy);actions.add(save);actions.add(origin);footer.add(actions,BorderLayout.WEST);
        JPanel paging=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));paging.setOpaque(false);paging.add(previous);paging.add(pageLabel);paging.add(next);footer.add(paging,BorderLayout.EAST);panel.add(footer,BorderLayout.SOUTH);return panel;
    }
    private JPanel favoritesPanel(){
        JPanel p=new JPanel(new BorderLayout(0,10));p.setOpaque(false);p.setBorder(new EmptyBorder(18,10,10,10));p.add(new JScrollPane(configureList(favoriteList)),BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));buttons.setOpaque(false);JButton open=new JButton("打开"),copy=new JButton("复制"),remove=new JButton("移除收藏");
        open.addActionListener(e->openSelected(favoriteList));copy.addActionListener(e->copySelected(favoriteList));remove.addActionListener(e->{Result r=favoriteList.getSelectedValue();if(r!=null){favorites.remove(r.key());writeFavorites();paintFavorites();}});buttons.add(open);buttons.add(copy);buttons.add(remove);p.add(buttons,BorderLayout.SOUTH);return p;
    }
    private JScrollPane sourcesPanel(){
        JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBackground(Color.WHITE);p.setBorder(new EmptyBorder(16,16,16,16));
        JLabel info=new JLabel("勾选参与搜索的来源。备用来源可能失效，可按需选择。");p.add(info);p.add(Box.createVerticalStrut(12));
        List<Source> ordered=new ArrayList<>(sources);ordered.sort(Comparator.comparing((Source s)->!s.defaultEnabled));
        for(Source s:ordered){JPanel row=new JPanel();row.setLayout(new BoxLayout(row,BoxLayout.Y_AXIS));row.setBackground(Color.WHITE);JCheckBox c=new JCheckBox(s.name+"  ·  "+(s.adult?"扩展来源":s.kind.equals("pan")?"网盘":"磁力"),s.defaultEnabled&&!s.adult);c.setBackground(Color.WHITE);c.setToolTipText(s.note+"  "+s.prefix);sourceChecks.put(s.id,c);sourceRows.put(s.id,row);row.add(c);JLabel note=new JLabel("      "+s.note);note.setForeground(MUTED);row.add(note);row.add(Box.createVerticalStrut(8));row.setVisible(!s.adult||adultEnabled);p.add(row);}
        return new JScrollPane(p);
    }
    private JPanel aboutPanel(){
        JPanel p=new JPanel(new BorderLayout(0,12));p.setBorder(new EmptyBorder(18,16,16,16));p.setOpaque(false);
        JTextArea text=new JTextArea("寻源 Windows "+VERSION+"\n\n手机与电脑共用搜索来源和解析引擎。请求由本机直接发往来源站点。\n每页最多15条；当前结果显示完后可继续请求网站下一页。\n\n网盘分享链接交给 Windows 默认浏览器或已注册的关联处理程序打开；\n磁力链接交给已安装并注册 magnet 协议的下载工具。\n\n搜索范围默认使用普通模式，关键词过滤可能漏判。\n收藏保存在本机，不自动上传。来源可能失效或要求浏览器验证。\n\n单文件便携版，运行时解压到临时目录，退出后清理。\n此版本未配置 Windows 代码签名证书。\n\n第三方依赖：Jsoup（MIT）、JSON-java（其许可证随安装包附带）。");text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setOpaque(false);p.add(text,BorderLayout.NORTH);
        diagnostics.setEditable(false);diagnostics.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));diagnostics.setLineWrap(true);diagnostics.setWrapStyleWord(true);p.add(new JScrollPane(diagnostics),BorderLayout.CENTER);return p;
    }
    private JList<Result> configureList(JList<Result> list){list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setFixedCellHeight(100);list.setBackground(Color.WHITE);list.setCellRenderer((view,r,index,selected,focus)->{
        JPanel cell=new JPanel(new BorderLayout(0,7));cell.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,new Color(230,237,231)),new EmptyBorder(12,16,12,16)));cell.setBackground(selected?new Color(224,240,229):Color.WHITE);
        JLabel heading=new JLabel(r.title);heading.setFont(heading.getFont().deriveFont(Font.BOLD,15f));heading.setForeground(INK);heading.setToolTipText(r.title);cell.add(heading,BorderLayout.NORTH);
        JLabel meta=new JLabel(r.kind+"  ·  "+r.source+(r.size.isEmpty()?"":"  ·  "+r.size)+(r.kind.equals("bt")?"":"  ·  "+DesktopFilters.codeLabel(r)+"  ·  "+r.linkMessage));meta.setForeground(MUTED);cell.add(meta,BorderLayout.CENTER);
        JLabel link=new JLabel(r.url);link.setForeground(GREEN);link.setFont(link.getFont().deriveFont(11f));cell.add(link,BorderLayout.SOUTH);return cell;});return list;}
    private void startSearch(){
        String q=query.getText().trim();if(q.isEmpty()){query.requestFocusInWindow();return;}if(!adultEnabled&&Result.adultText(q)){JOptionPane.showMessageDialog(frame,"当前搜索范围不支持该关键词。");return;}
        generation.incrementAndGet();engine.cancel();activeQuery=q;activeFilter=Objects.toString(filter.getSelectedItem());activeAdult=adultEnabled;sourcePage=0;displayPage=0;collected.clear();visible.clear();diagnostics.setText("");browsing=true;loadSourcePage();
    }
    private boolean accepts(Result r){return DesktopFilters.accepts(r,Objects.toString(filter.getSelectedItem()),onlyWithCode.isSelected(),adultEnabled);}
    private void refilter(){visible.clear();for(Result r:collected.values())if(accepts(r))visible.add(r);displayPage=0;paintResults();status.setText("筛选后 "+visible.size()+" 条；可继续加载下一批");}
    private void loadSourcePage(){
        List<Source> selected=new ArrayList<>();for(Source s:sources)if(sourceChecks.get(s.id).isSelected()&&(activeAdult||!s.adult)&&(sourcePage==0||!s.singlePage))selected.add(s);
        if(selected.isEmpty()){browsing=false;status.setText("请在搜索来源中至少勾选一个匹配的来源");paintResults();return;}
        sourcePage++;int ticket=generation.incrementAndGet();loading=true;pending=selected.size();search.setEnabled(true);status.setText("正在请求来源第 "+sourcePage+" 页…");paintResults();
        engine.search(selected,activeQuery,sourcePage,activeAdult,new SearchEngine.Listener(){
            public void removed(Source source,Result r){SwingUtilities.invokeLater(()->{if(ticket==generation.get()){hideInvalid(r);diagnostics.append(source.name+"：已隐藏1条明确失效的网盘链接\n");}});}
            public void found(Source source,Result r){SwingUtilities.invokeLater(()->{if(ticket!=generation.get())return;collected.merge(r.key(),r,Result::mergeKnown);refreshCollected();status.setText("正在搜索并检查链接 · 已显示 "+visible.size()+" 条");});}
            public void done(Source source,List<Result> results,String error){SwingUtilities.invokeLater(()->{
            if(ticket!=generation.get())return;
            diagnostics.append(source.name+"："+(error.isEmpty()?results.size()+" 条结果":error)+"\n");
            pending--;if(pending==0){loading=false;visible.clear();for(Result r:collected.values())if(accepts(r))visible.add(r);if(!visible.isEmpty())displayPage=Math.min(displayPage,(visible.size()-1)/15);else displayPage=0;paintResults();status.setText("已收集 "+visible.size()+" 条 · 来源第 "+sourcePage+" 页 · 若无新增，可更换关键词或查看关于页的来源状态");}
        });}});
    }
    private void hideInvalid(Result r){collected.remove(r.key());displayPage=0;refreshCollected();}
    private void refreshCollected(){visible.clear();for(Result r:collected.values())if(accepts(r))visible.add(r);paintResults();}
    private void paintResults(){resultModel.clear();int start=displayPage*15;for(int i=start;i<Math.min(start+15,visible.size());i++)resultModel.addElement(visible.get(i));pageLabel.setText("第 "+(displayPage+1)+" 页 · 最多15条");previous.setEnabled(displayPage>0);next.setEnabled(browsing&&((displayPage+1)*15<visible.size()||!loading));next.setText((displayPage+1)*15<visible.size()?"下一页 →":"加载下一批 →");}
    private void paintFavorites(){favoriteModel.clear();for(Result r:favorites.values())if(r.visible(adultEnabled))favoriteModel.addElement(r);}
    private void saveSelected(){Result r=resultList.getSelectedValue();if(r!=null){favorites.put(r.key(),r);writeFavorites();paintFavorites();status.setText("已收藏："+r.title);}}
    private void readFavorites(){try{if(Files.exists(dataPath)){JSONArray a=new JSONArray(Files.readString(dataPath));for(int i=0;i<a.length();i++){Result r=Result.from(a.getJSONObject(i));if(Result.safeUrl(r.url))favorites.put(r.key(),r);}}}catch(Exception e){status.setText("收藏读取失败："+e.getMessage());}}
    private void writeFavorites(){try{Files.createDirectories(dataPath.getParent());JSONArray a=new JSONArray();for(Result r:favorites.values())a.put(r.json());Path tmp=dataPath.resolveSibling("favorites.json.tmp");Files.writeString(tmp,a.toString(2),StandardCharsets.UTF_8);Files.move(tmp,dataPath,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){JOptionPane.showMessageDialog(frame,"收藏保存失败："+e.getMessage());}}
    private void copySelected(JList<Result> list){Result r=list.getSelectedValue();if(r!=null){copy(r.url+(r.password.isEmpty()?"":"\n提取码："+r.password));status.setText(r.kind.equals("bt")||!r.password.isEmpty()?"链接及已知提取码已复制":"链接已复制；来源未提供提取码，可查看来源页");}}
    private void copy(String value){Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value),null);}
    private void openSelected(JList<Result> list){Result r=list.getSelectedValue();if(r!=null){if(!r.password.isEmpty())copy(r.password);openAddress(r.url);}}
    private void openAddress(String address){try{if(!Result.safeUrl(address))throw new Exception("地址无效");Desktop.getDesktop().browse(new URI(address));}catch(Exception e){copy(address);JOptionPane.showMessageDialog(frame,"没有可用的关联应用或打开失败。地址已复制，可粘贴到浏览器或下载工具。\n"+e.getMessage());}}
    private void testTitleClick(){title.dispatchEvent(new MouseEvent(title,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,4,4,1,false,MouseEvent.BUTTON1));}
    private void smokeTest(){
        java.util.function.BooleanSupplier originalConfirm=confirmAdult;
        confirmAdult=()->false;
        for(int i=0;i<6;i++)testTitleClick();if(adultEnabled)throw new AssertionError("Cancelled unlock changed mode");
        confirmAdult=()->true;
        for(int i=0;i<5;i++)testTitleClick();if(adultEnabled)throw new AssertionError("Five taps unlocked");
        testTitleClick();if(!adultEnabled||!sourceRows.get("sukebei").isVisible())throw new AssertionError("Six taps did not unlock");
        query.setText("private test query");diagnostics.setText("private source status");
        Result sensitive=new Result();sensitive.title="NSFW sample";sensitive.adult=true;sensitive.url="https://example.com/test";collected.put(sensitive.key(),sensitive);favorites.put(sensitive.key(),sensitive);refilter();paintFavorites();
        for(int i=0;i<5;i++)testTitleClick();if(!adultEnabled)throw new AssertionError("Five taps locked early");
        testTitleClick();
        if(adultEnabled||sourceRows.get("sukebei").isVisible()||sourceChecks.get("sukebei").isSelected()||!collected.isEmpty()||!visible.isEmpty()||!query.getText().isEmpty()||!diagnostics.getText().isEmpty()||!favoriteModel.isEmpty())throw new AssertionError("Lock did not clear or hide sensitive state");
        favorites.remove(sensitive.key());confirmAdult=originalConfirm;
        for(int i=1;i<=31;i++){Result r=new Result();r.title="Windows 界面测试资源 "+i;r.kind=i%2==0?"百度":"bt";r.source="离线测试数据";r.password=i%2==0?"abcd":"";r.url=i%2==0?"https://pan.baidu.com/s/demo"+i:"magnet:?xt=urn:btih:"+String.format("%040x",i);visible.add(r);collected.put(r.key(),r);}browsing=true;paintResults();
        if(frame.getIconImages().size()!=5)throw new AssertionError("Window icons missing");
        if(adultEnabled||resultModel.size()!=15)throw new AssertionError("Initial UI state");
        next.doClick();if(displayPage!=1||resultModel.size()!=15||!resultModel.get(0).title.endsWith("16"))throw new AssertionError("Next 15 results");
        next.doClick();if(displayPage!=2||resultModel.size()!=1)throw new AssertionError("Last result");
        previous.doClick();previous.doClick();if(displayPage!=0||resultModel.size()!=15)throw new AssertionError("Previous page");
        onlyWithCode.doClick();if(visible.size()!=15)throw new AssertionError("Only results with codes");
        filter.setSelectedItem("磁力");if(!visible.isEmpty())throw new AssertionError("Combined filter");
        onlyWithCode.doClick();if(visible.size()!=16)throw new AssertionError("Magnet filter");
        filter.setSelectedItem("百度");if(visible.size()!=15)throw new AssertionError("Baidu filter");
        filter.setSelectedItem("全部资源");if(visible.size()!=31)throw new AssertionError("Filter restores cached results");
        Result doomed=visible.get(1);hideInvalid(doomed);if(visible.size()!=30)throw new AssertionError("Invalid link stays visible");collected.put(doomed.key(),doomed);refilter();
        status.setText("离线界面测试 · 31条样本 · 扩展搜索默认关闭");
        new javax.swing.Timer(1500,e->{try{java.awt.image.BufferedImage shot=new Robot().createScreenCapture(frame.getBounds());javax.imageio.ImageIO.write(shot,"png",Paths.get("desktop-smoke.png").toFile());System.out.println("DESKTOP_SMOKE_OK");frame.dispose();}catch(Exception ex){ex.printStackTrace();System.exit(1);}((javax.swing.Timer)e.getSource()).stop();}).start();
    }
    public static void main(String[] args){
        if(Arrays.asList(args).contains("--self-test")){try{DesktopTests.run();}catch(Exception e){e.printStackTrace();System.exit(1);}return;}
        System.setProperty("sun.java2d.uiScale.enabled","true");System.setProperty("java.net.useSystemProxies","true");
        SwingUtilities.invokeLater(()->{try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Font font=new Font("Microsoft YaHei UI",Font.PLAIN,14);for(Object key:Collections.list(UIManager.getDefaults().keys()))if(UIManager.get(key) instanceof javax.swing.plaf.FontUIResource)UIManager.put(key,new javax.swing.plaf.FontUIResource(font));DesktopApp app=new DesktopApp();app.frame.setVisible(true);if(Arrays.asList(args).contains("--smoke-test"))app.smokeTest();}catch(Throwable e){e.printStackTrace();if(!Arrays.asList(args).contains("--smoke-test"))JOptionPane.showMessageDialog(null,"寻源启动失败："+e.getMessage());System.exit(1);}});
    }
}

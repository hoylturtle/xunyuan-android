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
    public static final String VERSION="0.1.0";
    private static final Color BG=new Color(246,248,245),GREEN=new Color(35,105,80),INK=new Color(30,47,39),MUTED=new Color(103,119,109);
    private final JFrame frame=new JFrame("寻源 · Windows "+VERSION);
    private final SearchEngine engine=new SearchEngine();
    private final JTextField query=new JTextField();
    private final JComboBox<String> filter=new JComboBox<>(new String[]{"全部资源","网盘","百度","夸克","阿里","磁力"});
    private final JCheckBox adult=new JCheckBox("扩展搜索（18+）");
    private final JLabel status=new JLabel("输入关键词，开始寻找资源"),pageLabel=new JLabel("第 1 页");
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
        sources.add(new Source(new JSONObject().put("id","sukebei").put("name","Sukebei").put("kind","bt").put("parser","nyaa").put("prefix","https://sukebei.nyaa.si/?f=0&c=0_0&q=").put("middle","&p=").put("adult",true).put("enabled",false).put("note","需开启扩展搜索并勾选此来源；可用性取决于网络")));
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(860,640));frame.setSize(1080,780);frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){generation.incrementAndGet();engine.close();}});
        JPanel root=new JPanel(new BorderLayout(0,16));root.setBackground(BG);root.setBorder(new EmptyBorder(24,28,20,28));frame.setContentPane(root);
        JPanel header=new JPanel(new BorderLayout());header.setOpaque(false);
        JLabel title=new JLabel("寻源");title.setFont(title.getFont().deriveFont(Font.BOLD,30f));title.setForeground(GREEN);
        JLabel subtitle=new JLabel("网盘与磁力，一处寻找");subtitle.setForeground(MUTED);
        JPanel branding=new JPanel(new GridLayout(2,1,0,4));branding.setOpaque(false);branding.add(title);branding.add(subtitle);header.add(branding,BorderLayout.WEST);
        JButton release=new JButton("版本与更新");release.addActionListener(e->openAddress("https://github.com/hoylturtle/xunyuan-android/releases/tag/windows-v"+VERSION));header.add(release,BorderLayout.EAST);root.add(header,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("搜索",searchPanel());tabs.addTab("收藏",favoritesPanel());tabs.addTab("搜索来源",sourcesPanel());tabs.addTab("关于",aboutPanel());root.add(tabs,BorderLayout.CENTER);
        status.setForeground(MUTED);root.add(status,BorderLayout.SOUTH);
        readFavorites();paintFavorites();
        resultList.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)openSelected(resultList);}});
        favoriteList.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)openSelected(favoriteList);}});
        query.addActionListener(e->startSearch());search.addActionListener(e->startSearch());
        previous.addActionListener(e->{if(displayPage>0){displayPage--;paintResults();}});
        next.addActionListener(e->{if(loading||!browsing)return;if((displayPage+1)*15<visible.size()){displayPage++;paintResults();}else{displayPage++;loadSourcePage();}});
        adult.addActionListener(e->{
            if(adult.isSelected()&&JOptionPane.showConfirmDialog(frame,"扩展搜索可能包含成人内容。请确认已满18岁。","扩展搜索",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)adult.setSelected(false);
            generation.incrementAndGet();engine.cancel();loading=false;pending=0;browsing=false;collected.clear();visible.clear();displayPage=0;paintResults();paintFavorites();search.setEnabled(true);status.setText("搜索范围已更改，请重新搜索");
        });
        paintResults();
    }
    private JPanel searchPanel(){
        JPanel panel=new JPanel(new BorderLayout(0,12));panel.setBorder(new EmptyBorder(18,10,10,10));panel.setOpaque(false);
        JPanel top=new JPanel(new BorderLayout(10,10));top.setOpaque(false);query.setToolTipText("输入资源名称，例如 Ubuntu、Python");query.setFont(query.getFont().deriveFont(17f));query.setPreferredSize(new Dimension(500,42));top.add(query,BorderLayout.CENTER);top.add(search,BorderLayout.EAST);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,10,6));controls.setOpaque(false);controls.add(new JLabel("资源类型"));controls.add(filter);adult.setOpaque(false);controls.add(adult);
        JPanel inputs=new JPanel(new BorderLayout());inputs.setOpaque(false);inputs.add(top,BorderLayout.NORTH);inputs.add(controls,BorderLayout.SOUTH);panel.add(inputs,BorderLayout.NORTH);
        panel.add(new JScrollPane(configureList(resultList)),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(8,8));footer.setOpaque(false);JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));actions.setOpaque(false);
        JButton open=new JButton("打开资源"),copy=new JButton("复制链接 / 提取码"),save=new JButton("收藏");open.addActionListener(e->openSelected(resultList));copy.addActionListener(e->copySelected(resultList));save.addActionListener(e->saveSelected());actions.add(open);actions.add(copy);actions.add(save);footer.add(actions,BorderLayout.WEST);
        JPanel paging=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));paging.setOpaque(false);paging.add(previous);paging.add(pageLabel);paging.add(next);footer.add(paging,BorderLayout.EAST);panel.add(footer,BorderLayout.SOUTH);return panel;
    }
    private JPanel favoritesPanel(){
        JPanel p=new JPanel(new BorderLayout(0,10));p.setOpaque(false);p.setBorder(new EmptyBorder(18,10,10,10));p.add(new JScrollPane(configureList(favoriteList)),BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));buttons.setOpaque(false);JButton open=new JButton("打开"),copy=new JButton("复制"),remove=new JButton("移除收藏");
        open.addActionListener(e->openSelected(favoriteList));copy.addActionListener(e->copySelected(favoriteList));remove.addActionListener(e->{Result r=favoriteList.getSelectedValue();if(r!=null){favorites.remove(r.key());writeFavorites();paintFavorites();}});buttons.add(open);buttons.add(copy);buttons.add(remove);p.add(buttons,BorderLayout.SOUTH);return p;
    }
    private JScrollPane sourcesPanel(){
        JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBackground(Color.WHITE);p.setBorder(new EmptyBorder(16,16,16,16));
        JLabel info=new JLabel("勾选参与搜索的来源。备用来源可能失效；扩展来源还需开启18+开关。");p.add(info);p.add(Box.createVerticalStrut(12));
        List<Source> ordered=new ArrayList<>(sources);ordered.sort(Comparator.comparing((Source s)->!s.defaultEnabled));
        for(Source s:ordered){JCheckBox c=new JCheckBox(s.name+"  ·  "+(s.adult?"扩展来源":s.kind.equals("pan")?"网盘":"磁力"),s.defaultEnabled);c.setBackground(Color.WHITE);c.setToolTipText(s.note+"  "+s.prefix);sourceChecks.put(s.id,c);p.add(c);JLabel note=new JLabel("      "+s.note);note.setForeground(MUTED);p.add(note);p.add(Box.createVerticalStrut(8));}
        return new JScrollPane(p);
    }
    private JPanel aboutPanel(){
        JPanel p=new JPanel(new BorderLayout(0,12));p.setBorder(new EmptyBorder(18,16,16,16));p.setOpaque(false);
        JTextArea text=new JTextArea("寻源 Windows "+VERSION+"\n\n手机与电脑共用搜索来源和解析引擎。请求由本机直接发往来源站点。\n每页最多15条；当前结果显示完后可继续请求网站下一页。\n\n网盘分享链接交给 Windows 默认浏览器或已注册的关联处理程序打开；\n磁力链接交给已安装并注册 magnet 协议的下载工具。\n\n扩展搜索每次启动默认关闭，关键词过滤可能漏判。\n收藏保存在本机，不自动上传。来源可能失效或要求浏览器验证。\n\n此版本未配置 Windows 代码签名证书。\n\n第三方依赖：Jsoup（MIT）、JSON-java（其许可证随安装包附带）。");text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setOpaque(false);p.add(text,BorderLayout.NORTH);
        diagnostics.setEditable(false);diagnostics.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));diagnostics.setLineWrap(true);diagnostics.setWrapStyleWord(true);p.add(new JScrollPane(diagnostics),BorderLayout.CENTER);return p;
    }
    private JList<Result> configureList(JList<Result> list){list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setFixedCellHeight(100);list.setBackground(Color.WHITE);list.setCellRenderer((view,r,index,selected,focus)->{
        JPanel cell=new JPanel(new BorderLayout(0,7));cell.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,new Color(230,237,231)),new EmptyBorder(12,16,12,16)));cell.setBackground(selected?new Color(224,240,229):Color.WHITE);
        JLabel heading=new JLabel(r.title);heading.setFont(heading.getFont().deriveFont(Font.BOLD,15f));heading.setForeground(INK);heading.setToolTipText(r.title);cell.add(heading,BorderLayout.NORTH);
        JLabel meta=new JLabel(r.kind+"  ·  "+r.source+(r.size.isEmpty()?"":"  ·  "+r.size)+(r.password.isEmpty()?"":"  ·  提取码 "+r.password));meta.setForeground(MUTED);cell.add(meta,BorderLayout.CENTER);
        JLabel link=new JLabel(r.url);link.setForeground(GREEN);link.setFont(link.getFont().deriveFont(11f));cell.add(link,BorderLayout.SOUTH);return cell;});return list;}
    private void startSearch(){
        String q=query.getText().trim();if(q.isEmpty()){query.requestFocusInWindow();return;}if(!adult.isSelected()&&Result.adultText(q)){JOptionPane.showMessageDialog(frame,"当前搜索范围不支持该关键词。");return;}
        generation.incrementAndGet();engine.cancel();activeQuery=q;activeFilter=Objects.toString(filter.getSelectedItem());activeAdult=adult.isSelected();sourcePage=0;displayPage=0;collected.clear();visible.clear();diagnostics.setText("");browsing=true;loadSourcePage();
    }
    private boolean accepts(Result r){return r.visible(activeAdult)&&(activeFilter.equals("全部资源")||activeFilter.equals("网盘")&&!r.kind.equals("bt")||activeFilter.equals("磁力")&&r.kind.equals("bt")||activeFilter.equals(r.kind));}
    private void loadSourcePage(){
        List<Source> selected=new ArrayList<>();for(Source s:sources)if(sourceChecks.get(s.id).isSelected()&&(activeAdult||!s.adult)&&(activeFilter.equals("全部资源")||(activeFilter.equals("磁力")?s.kind.equals("bt"):s.kind.equals("pan"))))selected.add(s);
        if(selected.isEmpty()){browsing=false;status.setText("请在搜索来源中至少勾选一个匹配的来源");paintResults();return;}
        sourcePage++;int ticket=generation.incrementAndGet();loading=true;pending=selected.size();search.setEnabled(true);status.setText("正在请求来源第 "+sourcePage+" 页…");paintResults();
        engine.search(selected,activeQuery,sourcePage,activeAdult,(source,results,error)->SwingUtilities.invokeLater(()->{
            if(ticket!=generation.get())return;
            diagnostics.append(source.name+"："+(error.isEmpty()?results.size()+" 条结果":error)+"\n");
            for(Result r:results)if(accepts(r))collected.putIfAbsent(r.key(),r);
            pending--;if(pending==0){loading=false;visible.clear();visible.addAll(collected.values());if(!visible.isEmpty())displayPage=Math.min(displayPage,(visible.size()-1)/15);else displayPage=0;paintResults();status.setText("已收集 "+visible.size()+" 条 · 来源第 "+sourcePage+" 页 · 若无新增，可更换关键词或查看关于页的来源状态");}
        }));
    }
    private void paintResults(){resultModel.clear();int start=displayPage*15;for(int i=start;i<Math.min(start+15,visible.size());i++)resultModel.addElement(visible.get(i));pageLabel.setText("第 "+(displayPage+1)+" 页 · 最多15条");previous.setEnabled(!loading&&displayPage>0);next.setEnabled(!loading&&browsing);next.setText((displayPage+1)*15<visible.size()?"下一页 →":"加载下一批 →");}
    private void paintFavorites(){favoriteModel.clear();for(Result r:favorites.values())if(r.visible(adult.isSelected()))favoriteModel.addElement(r);}
    private void saveSelected(){Result r=resultList.getSelectedValue();if(r!=null){favorites.put(r.key(),r);writeFavorites();paintFavorites();status.setText("已收藏："+r.title);}}
    private void readFavorites(){try{if(Files.exists(dataPath)){JSONArray a=new JSONArray(Files.readString(dataPath));for(int i=0;i<a.length();i++){Result r=Result.from(a.getJSONObject(i));if(Result.safeUrl(r.url))favorites.put(r.key(),r);}}}catch(Exception e){status.setText("收藏读取失败："+e.getMessage());}}
    private void writeFavorites(){try{Files.createDirectories(dataPath.getParent());JSONArray a=new JSONArray();for(Result r:favorites.values())a.put(r.json());Path tmp=dataPath.resolveSibling("favorites.json.tmp");Files.writeString(tmp,a.toString(2),StandardCharsets.UTF_8);Files.move(tmp,dataPath,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){JOptionPane.showMessageDialog(frame,"收藏保存失败："+e.getMessage());}}
    private void copySelected(JList<Result> list){Result r=list.getSelectedValue();if(r!=null){copy(r.url+(r.password.isEmpty()?"":"\n提取码："+r.password));status.setText("链接已复制");}}
    private void copy(String value){Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value),null);}
    private void openSelected(JList<Result> list){Result r=list.getSelectedValue();if(r!=null){if(!r.password.isEmpty())copy(r.password);openAddress(r.url);}}
    private void openAddress(String address){try{if(!Result.safeUrl(address))throw new Exception("地址无效");Desktop.getDesktop().browse(new URI(address));}catch(Exception e){copy(address);JOptionPane.showMessageDialog(frame,"没有可用的关联应用或打开失败。地址已复制，可粘贴到浏览器或下载工具。\n"+e.getMessage());}}
    private void smokeTest(){
        for(int i=1;i<=31;i++){Result r=new Result();r.title="Windows 界面测试资源 "+i;r.kind=i%2==0?"百度":"bt";r.source="离线测试数据";r.url=i%2==0?"https://pan.baidu.com/s/demo"+i:"magnet:?xt=urn:btih:"+String.format("%040x",i);visible.add(r);}browsing=true;paintResults();
        if(adult.isSelected()||resultModel.size()!=15)throw new AssertionError("Initial UI state");
        next.doClick();if(displayPage!=1||resultModel.size()!=15||!resultModel.get(0).title.endsWith("16"))throw new AssertionError("Next 15 results");
        next.doClick();if(displayPage!=2||resultModel.size()!=1)throw new AssertionError("Last result");
        previous.doClick();previous.doClick();if(displayPage!=0||resultModel.size()!=15)throw new AssertionError("Previous page");
        status.setText("离线界面测试 · 31条样本 · 扩展搜索默认关闭");
        new javax.swing.Timer(1500,e->{try{java.awt.image.BufferedImage shot=new Robot().createScreenCapture(frame.getBounds());javax.imageio.ImageIO.write(shot,"png",Paths.get("desktop-smoke.png").toFile());System.out.println("DESKTOP_SMOKE_OK");frame.dispose();}catch(Exception ex){ex.printStackTrace();System.exit(1);}((javax.swing.Timer)e.getSource()).stop();}).start();
    }
    public static void main(String[] args){
        if(Arrays.asList(args).contains("--self-test")){try{DesktopTests.run();}catch(Exception e){e.printStackTrace();System.exit(1);}return;}
        System.setProperty("sun.java2d.uiScale.enabled","true");
        SwingUtilities.invokeLater(()->{try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Font font=new Font("Microsoft YaHei UI",Font.PLAIN,14);for(Object key:Collections.list(UIManager.getDefaults().keys()))if(UIManager.get(key) instanceof javax.swing.plaf.FontUIResource)UIManager.put(key,new javax.swing.plaf.FontUIResource(font));DesktopApp app=new DesktopApp();app.frame.setVisible(true);if(Arrays.asList(args).contains("--smoke-test"))app.smokeTest();}catch(Exception e){e.printStackTrace();JOptionPane.showMessageDialog(null,"寻源启动失败："+e.getMessage());System.exit(1);}});
    }
}

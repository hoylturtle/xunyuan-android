package com.btsou.search;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class AppUpdates {
    public static final String REPO_URL="https://github.com/hoylturtle/xunyuan-android";
    public static final String MANIFEST_URL=REPO_URL+"/releases/latest/download/update.json";
    private final Activity activity;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean closed=false,checking=false,downloading=false,waitingInstall=false;
    private ProgressDialog dialog;
    private Future<?> task;
    public AppUpdates(Activity a){activity=a;}
    public void close(){closed=true;worker.shutdownNow();if(dialog!=null)dialog.dismiss();}
    private void ui(Runnable action){activity.runOnUiThread(()->{if(!closed&&!activity.isFinishing())action.run();});}
    private void toast(String message){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
    private static long version(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    public void check(boolean manual){
        if(checking||downloading){if(manual)toast("正在检查或下载更新");return;}
        checking=true;if(manual)toast("正在检查更新…");
        task=worker.submit(()->{
            try{
                UpdateInfo info=new UpdateInfo(SearchEngine.request(MANIFEST_URL,null,null,"UTF-8"));
                long current=version(activity.getPackageManager().getPackageInfo(activity.getPackageName(),0));
                ui(()->{checking=false;if(!info.newerThan(current)){if(manual)toast("已是最新版本");return;}
                    new AlertDialog.Builder(activity).setTitle("发现新版本 "+info.versionName)
                        .setMessage(info.notes.substring(0,Math.min(2000,info.notes.length()))+"\n\n下载大小："+String.format(Locale.ROOT,"%.1f MB",info.apkSize/1048576.0)+"\n下载完成后由系统确认安装。")
                        .setPositiveButton("下载更新",(d,w)->download(info)).setNegativeButton("稍后",null).show();});
            }catch(Exception e){ui(()->{checking=false;if(manual)toast("检查失败："+SearchEngine.message(e)+"。可在项目主页查看发布版本。");});}
        });
    }
    private void download(UpdateInfo info){
        if(downloading)return;downloading=true;
        dialog=new ProgressDialog(activity);dialog.setTitle("下载寻源 "+info.versionName);dialog.setMessage("下载后将校验文件和签名");dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);dialog.setMax(100);dialog.setCancelable(false);
        dialog.setButton(ProgressDialog.BUTTON_NEGATIVE,"取消",(d,w)->{if(task!=null)task.cancel(true);});dialog.show();
        task=worker.submit(()->{
            File dir=new File(activity.getFilesDir(),"updates");File part=new File(dir,"download.part");
            try{
                if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法建立更新目录");
                downloadFile(info,part);verifyApk(part,info);
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                File target=new File(dir,"update.apk");if(target.exists()&&!target.delete())throw new IOException("无法替换旧更新文件");if(!part.renameTo(target))throw new IOException("无法保存更新文件");
                ui(()->{downloading=false;dialog.dismiss();install();});
            }catch(Exception e){ui(()->{downloading=false;if(dialog!=null)dialog.dismiss();if(!(e instanceof InterruptedException))toast("更新失败："+SearchEngine.message(e));});}
            finally{part.delete();}
        });
    }
    private void downloadFile(UpdateInfo info,File output)throws Exception{
        String address=info.apkUrl;
        for(int redirects=0;redirects<6;redirects++){
            UpdateInfo.requireHttps(address);HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Xunyuan-Android-Updater");
            try{
                int code=c.getResponseCode();if(code>=300&&code<400){String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("下载跳转无效");address=new URL(new URL(address),loc).toString();continue;}if(code!=200)throw new IOException("HTTP "+code);
                MessageDigest md=MessageDigest.getInstance("SHA-256");long count=0;int last=-1;long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(4);
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(output)){
                    byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(System.nanoTime()>deadline)throw new SocketTimeoutException();count+=n;if(count>info.apkSize)throw new IOException("更新文件大小不符");md.update(buf,0,n);out.write(buf,0,n);int progress=(int)(count*100/info.apkSize);if(progress!=last){last=progress;ui(()->{if(dialog!=null)dialog.setProgress(progress);});}}
                }
                if(count!=info.apkSize||!hex(md.digest()).equals(info.sha256))throw new IOException("更新文件校验失败");return;
            }finally{c.disconnect();}
        }
        throw new IOException("下载跳转次数过多");
    }
    private static String hex(byte[] value){StringBuilder b=new StringBuilder();for(byte v:value)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    private static Set<String> certs(PackageInfo p)throws Exception{
        Signature[] sigs=Build.VERSION.SDK_INT>=28&&p.signingInfo!=null?p.signingInfo.getApkContentsSigners():p.signatures;
        if(sigs==null||sigs.length==0)throw new IOException("安装包没有可验证的签名");Set<String> set=new HashSet<>();for(Signature s:sigs)set.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return set;
    }
    private void verifyApk(File file,UpdateInfo info)throws Exception{
        PackageManager pm=activity.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags),current=pm.getPackageInfo(activity.getPackageName(),flags);
        if(archive==null||!activity.getPackageName().equals(archive.packageName)||version(archive)!=info.versionCode||version(archive)<=version(current))throw new IOException("更新安装包版本或应用标识不符");
        if(!certs(archive).equals(certs(current)))throw new IOException("更新签名与当前安装版本不一致");
    }
    public void resumeInstall(){if(waitingInstall&&activity.getPackageManager().canRequestPackageInstalls()){waitingInstall=false;install();}}
    private void install(){
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity).setTitle("允许安装应用更新").setMessage("请在系统设置中允许寻源安装应用，然后返回。每次更新仍需在系统安装界面确认。")
                .setPositiveButton("前往设置",(d,w)->{try{waitingInstall=true;activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(ActivityNotFoundException e){waitingInstall=false;toast("请在系统设置中允许本应用安装更新");}}).setNegativeButton("取消",null).show();return;
        }
        try{Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/update.apk");Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("寻源更新",uri));activity.startActivity(i);}catch(ActivityNotFoundException e){toast("未找到系统安装器");}
    }
}

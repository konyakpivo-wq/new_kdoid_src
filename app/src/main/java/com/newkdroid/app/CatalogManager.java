package com.newkdroid.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CatalogManager {
    public static final String APPREPO_API = "https://api.github.com/repos/konyakpivo-wq/new_kdoid/contents/apprepo";
    public interface Callback { void onSuccess(List<AppEntry> apps, int version); void onError(Exception error); }
    public interface ReleasesCallback { void onSuccess(List<ReleaseEntry> releases); void onError(Exception error); }
    public interface DownloadCallback { void onProgress(int percent); void onSuccess(File file); void onError(Exception error); }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static final class AppEntry {
        public final int id;
        public final String name, repository, category, description;
        public AppEntry(int id,String n,String r,String c,String d){this.id=id;name=n;repository=r;category=c;description=d;}
    }
    public static final class AssetEntry {
        public final String name, downloadUrl;
        public AssetEntry(String n,String u){name=n;downloadUrl=u;}
    }
    public static final class ReleaseEntry {
        public final String tag,name,htmlUrl,publishedAt; public final boolean prerelease; public final List<AssetEntry> assets;
        public ReleaseEntry(String t,String n,String h,String p,boolean pre,List<AssetEntry> a){tag=t;name=n;htmlUrl=h;publishedAt=p;prerelease=pre;assets=a;}
        @Override public String toString(){return (name==null||name.isEmpty())?tag:name+" ("+tag+")";}
    }

    /** Finds every numeric *_repo.txt file in apprepo, so IDs 11, 12, 13... are detected automatically. */
    public void loadCatalog(Callback callback){executor.execute(()->{try{
        String listing=get(APPREPO_API);
        JSONArray files=new JSONArray(listing);
        TreeSet<Integer> ids=new TreeSet<>();
        for(int i=0;i<files.length();i++){
            JSONObject f=files.getJSONObject(i);
            String name=f.optString("name","");
            if(name.matches("\\d+_repo\\.txt")) ids.add(Integer.parseInt(name.substring(0,name.indexOf('_'))));
        }
        List<AppEntry> apps=new ArrayList<>();
        for(Integer id:ids){
            try{
                String repoText=get(rawUrl(id+"_repo.txt"));
                String decaText=get(rawUrl(id+"_deca.txt"));
                String repository=repositoryFromRepoFile(repoText);
                String[] meta=parseDeca(decaText,id);
                if(repository!=null&&!repository.isEmpty()&&meta!=null){
                    // The application name comes from the repository name; d: is the description.
                    String appName=repositoryName(repository);
                    apps.add(new AppEntry(id,appName,repository,meta[0],meta[1]));
                }
            }catch(Exception ignored){ }
        }
        callback.onSuccess(apps, apps.size());
    }catch(Exception e){callback.onError(e);}});}

    private String rawUrl(String file){return "https://raw.githubusercontent.com/konyakpivo-wq/new_kdoid/main/apprepo/"+file;}

    private String repositoryFromRepoFile(String text){
        String line=text==null?"":text.trim();
        int start=line.indexOf("repo:");
        if(start>=0) line=line.substring(start+5).trim();
        int marker=line.indexOf(" (id:");
        if(marker>0) line=line.substring(0,marker).trim();
        return repositoryFromUrl(line);
    }

    private String[] parseDeca(String text,int expectedId){
        String id="",cid="",d="";
        String[] lines=text.split("\\r?\\n");
        for(String raw:lines){
            String line=raw.trim();
            if(line.startsWith("id:")) id=line.substring(3).trim();
            else if(line.startsWith("cid:")) cid=line.substring(4).trim();
            else if(line.startsWith("d:")) d=line.substring(2).trim();
        }
        if(id.isEmpty()) id=String.valueOf(expectedId);
        if(d.isEmpty()) return null;
        String category=categoryName(cid);
        return new String[]{category,d};
    }

    private String categoryName(String cid){
        if("1".equals(cid)) return "Системные";
        if("2".equals(cid)) return "Приложения";
        if("3".equals(cid)) return "Утилиты";
        return cid.isEmpty()?"Другое":"Категория "+cid;
    }

    private String repositoryName(String repository){
        String s=repository==null?"":repository.trim();
        int slash=s.lastIndexOf('/');
        String name=slash>=0?s.substring(slash+1):s;
        return name.isEmpty()?"Приложение":name;
    }

    private String repositoryFromUrl(String url){String s=url.trim();if(s.endsWith("/"))s=s.substring(0,s.length()-1);if(s.endsWith(".git"))s=s.substring(0,s.length()-4);int x=s.indexOf("github.com/");return x>=0?s.substring(x+11):s;}

    public void loadReleases(String repository,ReleasesCallback callback){executor.execute(()->{try{
        JSONArray a=new JSONArray(get("https://api.github.com/repos/"+repository+"/releases?per_page=10"));List<ReleaseEntry> out=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(o.optBoolean("draft",false))continue;List<AssetEntry> assets=new ArrayList<>();JSONArray aa=o.optJSONArray("assets");if(aa!=null)for(int j=0;j<aa.length();j++){JSONObject z=aa.getJSONObject(j);assets.add(new AssetEntry(z.optString("name"),z.optString("browser_download_url")));}out.add(new ReleaseEntry(o.optString("tag_name"),o.optString("name"),o.optString("html_url"),o.optString("published_at"),o.optBoolean("prerelease",false),assets));}
        callback.onSuccess(out);
    }catch(Exception e){callback.onError(e);}});}

    public static AssetEntry chooseApk(ReleaseEntry r){List<AssetEntry> good=new ArrayList<>();for(AssetEntry a:r.assets){String n=a.name.toLowerCase(Locale.ROOT);if(!n.endsWith(".apk")||n.contains("src")||n.contains("source")||n.contains("debug")||n.contains("test"))continue;good.add(a);}if(good.isEmpty())return null;String arch=android.os.Build.SUPPORTED_ABIS.length>0?android.os.Build.SUPPORTED_ABIS[0]:"";for(AssetEntry a:good){String n=a.name.toLowerCase(Locale.ROOT);if((arch.contains("arm64")&&n.contains("arm64"))||(arch.contains("armeabi")&&n.contains("armeabi"))||(arch.contains("x86_64")&&n.contains("x86_64"))||(arch.equals("x86")&&n.contains("x86")))return a;}for(AssetEntry a:good)if(a.name.toLowerCase(Locale.ROOT).contains("universal"))return a;return good.get(0);}

    public void downloadApk(String url,File target,DownloadCallback cb){executor.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","New-KDroid/0.3");int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);long total=c.getContentLengthLong();try(InputStream in=new BufferedInputStream(c.getInputStream());OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){byte[] buf=new byte[8192];long done=0;int len;while((len=in.read(buf))!=-1){out.write(buf,0,len);done+=len;if(total>0)cb.onProgress((int)(done*100/total));}}cb.onProgress(100);cb.onSuccess(target);}catch(Exception e){if(target.exists())target.delete();cb.onError(e);}finally{if(c!=null)c.disconnect();}});}

    private String get(String address)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setRequestMethod("GET");c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json, text/plain");c.setRequestProperty("User-Agent","New-KDroid/0.3");try{int code=c.getResponseCode();InputStream s=code>=200&&code<300?c.getInputStream():c.getErrorStream();if(s==null)throw new IOException("HTTP "+code);BufferedReader r=new BufferedReader(new InputStreamReader(s,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line).append('\n');if(code<200||code>=300)throw new IOException("HTTP "+code);return b.toString();}finally{c.disconnect();}}
}

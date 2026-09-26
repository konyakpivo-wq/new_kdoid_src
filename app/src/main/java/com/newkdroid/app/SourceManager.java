package com.newkdroid.app;

import android.content.Context;
import android.util.JsonReader;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class SourceManager {
    public static final String FDROID = "F-Droid";
    private static final String OFFICIAL_FDROID = "https://f-droid.org/repo/";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String PREFS = "nkd_sources";
    private static final String GITHUB = "github";
    private static final String FDS = "fdroid";

    public SourceManager(Context c) { context = c.getApplicationContext(); }

    public static final class Source {
        public final String type,url,name,description;
        Source(String t,String u,String n,String d){type=t;url=u;name=n;description=d;}
    }

    public void addGitHub(String url,String name,String description) {
        String repo=normalizeGitHub(url);
        if(repo.isEmpty()) return;
        add(GITHUB,repo,name,description);
    }

    public void addFdroid(String url,String name) {
        add(FDS,normalizeFdroid(url),name,"Сторонний F-Droid репозиторий");
    }

    private void add(String type,String url,String name,String description) {
        JSONArray a=readSources(type);
        try {
            for(int i=0;i<a.length();i++) {
                JSONObject old=a.optJSONObject(i);
                if(old!=null&&url.equals(old.optString("url"))) return;
            }
            JSONObject o=new JSONObject();
            o.put("type",type);
            o.put("url",url);
            o.put("name",name==null?"":name);
            o.put("description",description==null?"":description);
            a.put(o);
            context.getSharedPreferences(PREFS,0).edit().putString(type,a.toString()).apply();
        } catch(Exception ignored){}
    }

    public List<Source> getSources() {
        List<Source> out=new ArrayList<>();
        boolean official=false;
        JSONArray fd=readSources(FDS);
        for(int i=0;i<fd.length();i++) try {
            JSONObject o=fd.getJSONObject(i);
            String url=normalizeFdroid(o.optString("url"));
            if(OFFICIAL_FDROID.equals(url)) official=true;
            out.add(new Source(FDS,url,o.optString("name"),o.optString("description")));
        } catch(Exception ignored){}
        if(!official) out.add(0,new Source(FDS,OFFICIAL_FDROID,"F-Droid","Официальный репозиторий F-Droid"));

        JSONArray gh=readSources(GITHUB);
        for(int i=0;i<gh.length();i++) try {
            JSONObject o=gh.getJSONObject(i);
            String repo=normalizeGitHub(o.optString("url"));
            if(!repo.isEmpty()) out.add(new Source(GITHUB,repo,o.optString("name"),o.optString("description")));
        } catch(Exception ignored){}
        return out;
    }

    private JSONArray readSources(String type) {
        try{return new JSONArray(context.getSharedPreferences(PREFS,0).getString(type,"[]"));}
        catch(Exception e){return new JSONArray();}
    }

    public interface Callback { void done(List<CatalogManager.AppEntry> apps); void error(Exception e); }

    public void loadCustom(Callback cb) {
        executor.execute(() -> {
            List<CatalogManager.AppEntry> out=new ArrayList<>();
            int id=100000;
            Exception firstError=null;

            for(Source source:getSources()) {
                try {
                    if(GITHUB.equals(source.type)) {
                        String repo=normalizeGitHub(source.url);
                        if(!repo.isEmpty()) {
                            try {
                                loadGitHubApp(repo,source,id++,out);
                            } catch(Exception githubError) {
                                // Импортированное приложение всё равно показываем.
                                // GitHub нужен для релизов только после нажатия «Установить».
                                String name=source.name.isEmpty()?repositoryName(repo):source.name;
                                String desc=source.description.isEmpty()?"GitHub repository":source.description;
                                out.add(new CatalogManager.AppEntry(id++,name,repo,"Сторонние",desc,""));
                                if(firstError==null)firstError=githubError;
                            }
                        }
                    } else if(FDS.equals(source.type)) {
                        id=loadFdroidApps(normalizeFdroid(source.url),id,out);
                    }
                } catch(Exception e) {
                    if(firstError==null)firstError=e;
                }
            }

            if(!out.isEmpty()) cb.done(out);
            else if(firstError!=null) cb.error(firstError);
            else cb.done(out);
        });
    }

    private String repositoryName(String repo){
        int p=repo.lastIndexOf('/');
        return p>=0&&p+1<repo.length()?repo.substring(p+1):repo;
    }

    private void loadGitHubApp(String repo,Source source,int id,List<CatalogManager.AppEntry> out)throws Exception {
        String[] p=repo.split("/",2);
        if(p.length!=2)throw new IOException("Некорректный GitHub репозиторий");
        JSONObject info=new JSONObject(get("https://api.github.com/repos/"+p[0]+"/"+p[1]));
        String name=source.name.isEmpty()?info.optString("name",repo):source.name;
        String desc=source.description.isEmpty()?info.optString("description","GitHub repository"):source.description;
        JSONObject owner=info.optJSONObject("owner");
        String icon=owner==null?"":owner.optString("avatar_url","");
        out.add(new CatalogManager.AppEntry(id,name,repo,"Сторонние",desc,icon));
    }

    /*
     * F-Droid index-v1 может занимать десятки мегабайт после распаковки.
     * Поэтому больше НЕ создаём один огромный JSONObject.
     * Читаем JSON потоково через Android JsonReader.
     */
    private int loadFdroidApps(String base,int id,List<CatalogManager.AppEntry> out)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(indexUrl(base)).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(90000);
        c.setRequestProperty("User-Agent","New-KDroid/1.1.0");
        c.setRequestProperty("Accept","application/json");

        try {
            int code=c.getResponseCode();
            if(code<200||code>=300)throw new IOException("F-Droid HTTP "+code);
            try(InputStream in=new BufferedInputStream(c.getInputStream());
                JsonReader reader=new JsonReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {

                reader.beginObject();
                HashMap<String,Meta> metadata=new HashMap<>();
                int[] next={id};
                while(reader.hasNext()){
                    String key=reader.nextName();
                    if("apps".equals(key)){
                        readApps(reader,metadata);
                    }else if("packages".equals(key)){
                        readPackages(reader,metadata,base,next,out);
                    }else{
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
            return next[0];
        } finally {
            c.disconnect();
        }
    }

    

    private static final class Meta {
        String name="",summary="",description="",icon="";
    }

    private void readApps(JsonReader r,HashMap<String,Meta> metadata)throws Exception {
        r.beginArray();
        while(r.hasNext()){
            Meta m=new Meta();
            String pkg="";
            r.beginObject();
            while(r.hasNext()){
                String k=r.nextName();
                if("packageName".equals(k))pkg=r.nextString();
                else if("name".equals(k))m.name=r.nextString();
                else if("summary".equals(k))m.summary=r.nextString();
                else if("description".equals(k)){
                    if(r.peek()==android.util.JsonToken.STRING)m.description=r.nextString();
                    else r.skipValue();
                }else if("icon".equals(k))m.icon=r.nextString();
                else r.skipValue();
            }
            r.endObject();
            if(!pkg.isEmpty())metadata.put(pkg,m);
        }
        r.endArray();
    }

    private void readPackages(JsonReader r,HashMap<String,Meta> metadata,String base,int[] next,List<CatalogManager.AppEntry> out)throws Exception {
        r.beginObject();
        while(r.hasNext()){
            String pkg=r.nextName();
            Meta m=metadata.get(pkg);
            JSONObject latest=new JSONObject();
            r.beginArray();
            while(r.hasNext()){
                r.beginObject();
                long versionCode=0;
                String apk="";
                while(r.hasNext()){
                    String k=r.nextName();
                    if("versionCode".equals(k))versionCode=r.nextLong();
                    else if("apkName".equals(k))apk=r.nextString();
                    else r.skipValue();
                }
                r.endObject();
                if(!apk.isEmpty()&&versionCode>=latest.optLong("versionCode",-1)){
                    latest.put("versionCode",versionCode);
                    latest.put("apkName",apk);
                }
            }
            r.endArray();

            String apk=latest.optString("apkName","");
            if(apk.isEmpty())continue;

            String name=m==null||m.name.isEmpty()?pkg:m.name;
            String desc=m==null?"":(!m.summary.isEmpty()?m.summary:m.description);
            String icon=m==null?"":m.icon;
            icon=resolveFdroidFile(base,icon);

            out.add(new CatalogManager.AppEntry(next[0]++,name,"FDROID|"+base+"|"+apk,"F-Droid",desc,icon));
        }
        r.endObject();
    }

    private String resolveFdroidFile(String base,String file){
        if(file==null||file.isEmpty())return "";
        if(file.startsWith("http://")||file.startsWith("https://"))return file;
        if(file.startsWith("/"))return base+file.substring(1);
        return base+file;
    }

    public static String normalizeGitHub(String s){
        if(s==null)return "";
        s=s.trim();
        s=s.replace("https://www.github.com/","").replace("http://www.github.com/","")
           .replace("https://github.com/","").replace("http://github.com/","");
        int q=s.indexOf('?');if(q>=0)s=s.substring(0,q);
        int h=s.indexOf('#');if(h>=0)s=s.substring(0,h);
        if(s.startsWith("github.com/"))s=s.substring(11);
        while(s.endsWith("/"))s=s.substring(0,s.length()-1);
        if(s.endsWith(".git"))s=s.substring(0,s.length()-4);
        String[] parts=s.split("/");
        if(parts.length>=3&&("releases".equalsIgnoreCase(parts[2])||"tree".equalsIgnoreCase(parts[2])||"blob".equalsIgnoreCase(parts[2])))
            s=parts[0]+"/"+parts[1];
        return s;
    }

    public static String normalizeFdroid(String s){
        if(s==null||s.trim().isEmpty())return OFFICIAL_FDROID;
        s=s.trim();
        while(s.endsWith("index-v1.json")||s.endsWith("index-v2.json"))
            s=s.substring(0,s.lastIndexOf("index-v"));
        if(!s.endsWith("/"))s+="/";
        return s;
    }

    private String indexUrl(String base){
        return base+"index-v1.json";
    }

    private String get(String address)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(20000);c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent","New-KDroid/1.1.0");
        c.setRequestProperty("Accept","application/json");
        try{
            int code=c.getResponseCode();
            InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            if(in==null)throw new IOException("HTTP "+code);
            StringBuilder b=new StringBuilder();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                String line;while((line=r.readLine())!=null)b.append(line);
            }
            if(code<200||code>=300)throw new IOException("HTTP "+code);
            return b.toString();
        }finally{c.disconnect();}
    }
}
package com.newkdroid.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class SourceManager {
    public static final String FDROID = "F-Droid";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String PREFS = "nkd_sources";
    private static final String GITHUB = "github";
    private static final String FDS = "fdroid";

    public SourceManager(Context c) { context = c.getApplicationContext(); }

    public static final class Source {
        public final String type, url, name, description;
        Source(String t,String u,String n,String d){type=t;url=u;name=n;description=d;}
    }

    public void addGitHub(String url,String name,String description) {
        add(GITHUB,url,name,description);
    }
    public void addFdroid(String url,String name) {
        add(FDS,normalizeFdroid(url),name,"Сторонний F-Droid репозиторий");
    }

    private void add(String type,String url,String name,String description) {
        JSONArray a=readSources(type);
        JSONObject o=new JSONObject();
        try { o.put("type",type);o.put("url",url);o.put("name",name);o.put("description",description);a.put(o); } catch(Exception ignored){}
        context.getSharedPreferences(PREFS,0).edit().putString(type,a.toString()).apply();
    }

    public List<Source> getSources() {
        List<Source> out=new ArrayList<>();
        for(String type:new String[]{GITHUB,FDS}) {
            JSONArray a=readSources(type);
            for(int i=0;i<a.length();i++) try {
                JSONObject o=a.getJSONObject(i);
                out.add(new Source(type,o.optString("url"),o.optString("name"),o.optString("description")));
            } catch(Exception ignored){}
        }
        return out;
    }

    private JSONArray readSources(String type) {
        try{return new JSONArray(context.getSharedPreferences(PREFS,0).getString(type,"[]"));}catch(Exception e){return new JSONArray();}
    }

    public interface Callback { void done(List<CatalogManager.AppEntry> apps); void error(Exception e); }

    public void loadCustom(Callback cb) {
        executor.execute(() -> {
            try {
                List<CatalogManager.AppEntry> out=new ArrayList<>();
                int id=100000;
                for(Source source:getSources()) {
                    if(GITHUB.equals(source.type)) {
                        String repo=normalizeGitHub(source.url);
                        if(repo.isEmpty()) continue;
                        loadGitHubApp(repo,source,id++,out);
                    } else if(FDS.equals(source.type)) {
                        id=loadFdroidApps(normalizeFdroid(source.url),id,out);
                    }
                }
                cb.done(out);
            } catch(Exception e){cb.error(e);}
        });
    }

    private void loadGitHubApp(String repo,Source source,int id,List<CatalogManager.AppEntry> out)throws Exception {
        String[] p=repo.split("/",2);
        if(p.length!=2)return;
        JSONObject info=new JSONObject(get("https://api.github.com/repos/"+p[0]+"/"+p[1]));
        String name=source.name.isEmpty()?info.optString("name",repo):source.name;
        String desc=source.description.isEmpty()?info.optString("description","GitHub repository"):source.description;
        JSONObject owner=info.optJSONObject("owner");
        String icon=owner==null?"":owner.optString("avatar_url","");
        out.add(new CatalogManager.AppEntry(id,name,repo,"Сторонние",desc,icon));
    }

    private int loadFdroidApps(String base,int id,List<CatalogManager.AppEntry> out)throws Exception {
        JSONObject root=new JSONObject(get(indexUrl(base)));
        JSONArray appList=root.optJSONArray("apps");
        JSONObject packages=root.optJSONObject("packages");
        if(packages==null)return id;

        HashMap<String,JSONObject> metadata=new HashMap<>();
        if(appList!=null)for(int i=0;i<appList.length();i++){
            JSONObject a=appList.optJSONObject(i);
            if(a!=null)metadata.put(a.optString("packageName",""),a);
        }

        Iterator<String> keys=packages.keys();
        while(keys.hasNext()){
            String pkg=keys.next();
            JSONArray versions=packages.optJSONArray(pkg);
            if(versions==null||versions.length()==0)continue;
            JSONObject latest=versions.optJSONObject(0);
            for(int i=1;i<versions.length();i++){
                JSONObject v=versions.optJSONObject(i);
                if(v!=null&&v.optLong("versionCode",0)>latest.optLong("versionCode",0))latest=v;
            }
            String apk=latest.optString("apkName",latest.optString("apkname",""));
            if(apk.isEmpty())continue;

            JSONObject meta=metadata.get(pkg);
            String name=pkg,desc="",icon="";
            if(meta!=null){
                name=meta.optString("name",pkg);
                desc=meta.optString("summary",meta.optString("description",""));
                icon=meta.optString("icon","");
                JSONObject localized=meta.optJSONObject("localized");
                if(localized!=null){
                    JSONObject en=localized.optJSONObject("en-US");
                    if(en!=null){
                        if(name.equals(pkg))name=en.optString("name",name);
                        if(desc.isEmpty())desc=en.optString("summary",desc);
                        if(icon.isEmpty())icon=en.optString("icon",icon);
                    }
                }
            }
            icon=resolveFdroidFile(base,icon);
            out.add(new CatalogManager.AppEntry(id++,name,"FDROID|"+base+"|"+apk,"F-Droid",desc,icon));
        }
        return id;
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
        s=s.replace("https://www.github.com/","").replace("http://www.github.com/","");
        s=s.replace("https://github.com/","").replace("http://github.com/","");
        int q=s.indexOf('?');if(q>=0)s=s.substring(0,q);
        int h=s.indexOf('#');if(h>=0)s=s.substring(0,h);
        if(s.startsWith("github.com/"))s=s.substring(11);
        while(s.endsWith("/"))s=s.substring(0,s.length()-1);
        if(s.endsWith(".git"))s=s.substring(0,s.length()-4);
        return s;
    }

    public static String normalizeFdroid(String s){
        if(s==null||s.trim().isEmpty()) return "https://f-droid.org/repo/";
        s=s.trim();
        if(!s.endsWith("/"))s+="/";
        if(s.endsWith("index-v1.json/"))s=s.substring(0,s.length()-15);
        return s;
    }

    private String indexUrl(String base){
        if(base.endsWith("index-v1.json")) return base;
        return base+"index-v1.json";
    }

    private String get(String address)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent","New-KDroid/1.0.0");
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

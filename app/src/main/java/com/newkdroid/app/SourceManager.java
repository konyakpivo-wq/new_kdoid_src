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
                for(Source s:getSources()) {
                    if(GITHUB.equals(s.type)) {
                        String repo=normalizeGitHub(s.url);
                        if(repo.isEmpty()) continue;
                        out.add(new CatalogManager.AppEntry(id++,s.name.isEmpty()?repo:s.name,repo,"Сторонние",""+(s.description.isEmpty()?"GitHub":s.description),""));
                    } else if(FDS.equals(s.type)) {
                        String base=normalizeFdroid(s.url);
                        String index=get(indexUrl(base));
                        JSONObject root=new JSONObject(index);
                        JSONObject apps=root.optJSONObject("packages");
                        if(apps==null) continue;
                        Iterator<String> keys=apps.keys();
                        while(keys.hasNext()) {
                            String pkg=keys.next();
                            JSONObject app=apps.optJSONObject(pkg);
                            if(app==null) continue;
                            String name=app.optString("name",pkg);
                            String desc=app.optString("summary",app.optString("description",""));\n                            String icon=app.optString("icon","");\n                            if(icon.startsWith("icon/")) icon=base+icon;\n                            else if(!icon.isEmpty()&&!icon.startsWith("http://")&&!icon.startsWith("https://")) icon=base+icon;
                            JSONArray packages=app.optJSONArray("packages");
                            if(packages==null||packages.length()==0) continue;
                            JSONObject latest=packages.getJSONObject(packages.length()-1);
                            String apk=latest.optString("apkname","");
                            if(apk.isEmpty()) continue;
                            out.add(new CatalogManager.AppEntry(id++,name,"FDROID|"+base+"|"+apk,"F-Droid",desc,icon));
                        }
                    }
                }
                cb.done(out);
            } catch(Exception e){cb.error(e);}
        });
    }

    public static String normalizeGitHub(String s){
        if(s==null)return "";
        s=s.trim().replace("https://github.com/","").replace("http://github.com/","");
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

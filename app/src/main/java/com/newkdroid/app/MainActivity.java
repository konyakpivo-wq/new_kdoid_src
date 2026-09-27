package com.newkdroid.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.text.*;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.net.HttpURLConnection;

public class MainActivity extends Activity {
    private RecyclerView list;
    private AppAdapter adapter;
    private LinearLayout categoryMenu;
    private final List<CatalogManager.AppEntry> displayedApps=new ArrayList<>();
    private String activeCategory="";
    private ExecutorService iconExecutor=Executors.newFixedThreadPool(2);
    private EditText search;
    private TextView status;
    private final List<CatalogManager.AppEntry> allApps=new ArrayList<>();
    private CatalogManager manager;
    private SourceManager sources;
    private static final int NKD_FOLDER_REQUEST=1001;
    private static final int OBTAINIUM_JSON_REQUEST=1002;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setTheme(getSharedPreferences("nkd_theme",0).getBoolean("amoled",false)?R.style.Theme_NewKDroid_Amoled:R.style.Theme_NewKDroid);
        applyTheme();
        setContentView(R.layout.activity_main);
        manager=new CatalogManager(this);
        sources=new SourceManager(this);
        list=findViewById(R.id.appList);
        categoryMenu=findViewById(R.id.categoryMenu);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter=new AppAdapter();
        list.setAdapter(adapter);
        search=findViewById(R.id.searchBox);
        status=findViewById(R.id.statusText);
        findViewById(R.id.refreshButton).setOnClickListener(v->loadCatalog(true));
        findViewById(R.id.settingsButton).setOnClickListener(v->showSettings());
        findViewById(R.id.newAppsButton).setOnClickListener(v->render(""));
        
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int d){}
            public void onTextChanged(CharSequence s,int a,int b,int c){render(s.toString());}
            public void afterTextChanged(Editable e){}
        });
        loadCatalog(false);
    }

    private void applyTheme(){
        boolean amoled=getSharedPreferences("nkd_theme",0).getBoolean("amoled",false);
        int bg=amoled?Color.BLACK:Color.rgb(18,10,30);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private void loadCatalog(boolean manual){
        status.setText("Обновляем каталог…");
        manager.loadCatalog(new CatalogManager.Callback(){
            public void onSuccess(List<CatalogManager.AppEntry> a,int v){
                runOnUiThread(()->{
                    allApps.clear();
                    allApps.addAll(a);
                    refreshCategoryMenu();
                    render(search.getText().toString());
                    loadCustomSources(manual);
                });
            }
            public void onError(Exception e){
                runOnUiThread(()->{
                    allApps.clear();
                    refreshCategoryMenu();
                    render(search.getText().toString());
                    loadCustomSources(manual);
                });
            }
        });
    }

    private void loadCustomSources(boolean manual){
        sources.loadCustom(new SourceManager.Callback(){
            public void item(CatalogManager.AppEntry app){
                runOnUiThread(()->{
                    if(!allApps.contains(app)){allApps.add(app);refreshCategoryMenu();render(search.getText().toString());}
                });
            }
            public void done(List<CatalogManager.AppEntry> custom){
                runOnUiThread(()->{
                    for(CatalogManager.AppEntry app:custom) if(!allApps.contains(app)) allApps.add(app);
                    refreshCategoryMenu();
                    status.setText("Каталог • "+allApps.size()+" приложений");
                    render(search.getText().toString());
                    if(manual) toast("Каталог обновлён");
                });
            }
            public void error(Exception e){
                runOnUiThread(()->{
                    status.setText("Каталог • "+allApps.size()+" приложений");
                    render(search.getText().toString());
                    if(manual) toast("Основной каталог обновлён, часть источников недоступна");
                });
            }
        });
    }

    private void render(String q0){
        displayedApps.clear();
        String q=q0==null?"":q0.trim().toLowerCase(Locale.ROOT);
        for(CatalogManager.AppEntry a:allApps){
            if(!activeCategory.isEmpty()&&!activeCategory.equals(a.category))continue;
            if(!q.isEmpty()&&!((a.name+" "+a.description+" "+a.category).toLowerCase(Locale.ROOT).contains(q)))continue;
            displayedApps.add(a);
        }
        adapter.notifyDataSetChanged();
        status.setText("Каталог • "+allApps.size()+" приложений"+(activeCategory.isEmpty()?"":" • "+activeCategory));
    }

    private void showCategory(String c){
        activeCategory=c==null?"":c;
        render(search.getText().toString());
        refreshCategoryMenu();
    }

    private void refreshCategoryMenu(){
        if(categoryMenu==null)return;
        categoryMenu.removeAllViews();
        addCategoryChip("Все","".equals(activeCategory));
        addCategoryChip("Новинки","__NEW__".equals(activeCategory));
        TreeSet<String> cats=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for(CatalogManager.AppEntry a:allApps) if(a.category!=null&&!a.category.trim().isEmpty()) cats.add(a.category);
        for(String c:cats) addCategoryChip(c,c.equals(activeCategory));
    }

    private void addCategoryChip(String name,boolean selected){
        TextView chip=label(name,14,selected?Color.WHITE:Color.LTGRAY);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(22,0,22,0);
        GradientDrawable bg=new GradientDrawable();
        boolean amoled=getSharedPreferences("nkd_theme",0).getBoolean("amoled",false);
        bg.setColor(selected?(amoled?Color.rgb(70,35,100):Color.rgb(95,45,125)):(amoled?Color.rgb(20,20,20):Color.rgb(48,28,60)));
        bg.setCornerRadius(40);
        chip.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,44);
        p.rightMargin=8;
        categoryMenu.addView(chip,p);
        chip.setOnClickListener(v->{
            activeCategory="Новинки".equals(name)?"__NEW__":("Все".equals(name)?"":name);
            render(search.getText().toString());
            refreshCategoryMenu();
        });
    }

    private boolean matchesCategory(CatalogManager.AppEntry a){
        if(!"__NEW__".equals(activeCategory))return true;
        return true;
    }

    private void downloadFdroid(String base,String apkName,String appName){
        String url=(base.endsWith("/")?base:base+"/")+apkName;
        File file=new File(getCacheDir(),"newkdroid_"+safe(appName)+"_"+safe(apkName));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        TextView text=label("Подготовка скачивания…",15,Color.WHITE);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,12,24,12);
        box.addView(text);box.addView(bar);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Скачивание "+appName).setView(box).setNegativeButton("Отмена",null).create();
        dialog.show();
        manager.downloadApk(url,file,new CatalogManager.DownloadCallback(){
            public void onProgress(int p){runOnUiThread(()->{bar.setProgress(p);text.setText("Скачивание… "+p+"%");});}
            public void onSuccess(File f){runOnUiThread(()->{dialog.dismiss();installApk(f);});}
            public void onError(Exception e){runOnUiThread(()->{dialog.dismiss();toast("Ошибка скачивания: "+e.getMessage());});}
        });
    }

    private void startDownload(CatalogManager.ReleaseEntry release,String appName){
        CatalogManager.AssetEntry asset=CatalogManager.chooseApk(release);
        if(asset==null){toast("В этом релизе не найден подходящий APK");return;}
        File file=new File(getCacheDir(),"newkdroid_"+safe(appName)+"_"+safe(release.tag)+".apk");
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);
        TextView text=label("Подготовка скачивания…",15,Color.WHITE);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,12,24,12);box.addView(text);box.addView(bar);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Скачивание "+appName).setView(box).setNegativeButton("Отмена",null).create();
        dialog.show();
        manager.downloadApk(asset.downloadUrl,file,new CatalogManager.DownloadCallback(){
            public void onProgress(int p){runOnUiThread(()->{bar.setProgress(p);text.setText("Скачивание… "+p+"%");});}
            public void onSuccess(File f){runOnUiThread(()->{dialog.dismiss();installApk(f);});}
            public void onError(Exception e){runOnUiThread(()->{dialog.dismiss();toast("Ошибка скачивания: "+e.getMessage());});}
        });
    }

    private String safe(String s){return s==null?"app":s.replaceAll("[^A-Za-z0-9._-]","_");}

    private void installApk(File file){
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this).setTitle("Требуется разрешение")
                .setMessage("Разрешите New KDroid устанавливать приложения из неизвестных источников.")
                .setPositiveButton("Открыть настройки",(d,w)->{
                    try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}
                    catch(Exception e){startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));}
                }).setNegativeButton("Отмена",null).show();
            return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(this,"com.newkdroid.app.fileprovider",file);
            Intent i=new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri,"application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }catch(Exception e){toast("Не удалось открыть установщик: "+e.getMessage());}
    }

    private void showSettings(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(12,4,12,0);
        TextView info=label("New KDroid 1.1.0\n\nИсточники: официальный F-Droid + добавленные тобой источники.",15,Color.LTGRAY);
        box.addView(info);
        addSettingButton(box,"＋ Добавить GitHub репозиторий",v->addGitHubDialog());
        addSettingButton(box,"＋ Добавить F-Droid репозиторий",v->addFdroidDialog());
        addSettingButton(box,"◈ Obtainium",v->showObtainiumMenu());
        addSettingButton(box,"📁 Подключить папку NKD",v->chooseNkdFolder());
        addSettingButton(box,"🎨 Тема: тёмно-фиолетовая / AMOLED",v->chooseTheme());
        new AlertDialog.Builder(this).setTitle("Настройки New KDroid").setView(box).setPositiveButton("Готово",null).show();
    }

    private void addSettingButton(LinearLayout box,String text,View.OnClickListener click){
        TextView b=label(text,16,Color.rgb(220,205,240));b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(18,18,18,18);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(58,58,64));bg.setCornerRadius(22);b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,60);p.topMargin=10;box.addView(b,p);b.setOnClickListener(click);
    }

    private void chooseTheme(){
        String[] items={"Тёмно-фиолетовая","AMOLED"};
        boolean amoled=getSharedPreferences("nkd_theme",0).getBoolean("amoled",false);
        new AlertDialog.Builder(this).setTitle("Тема New KDroid").setSingleChoiceItems(items,amoled?1:0,(d,w)->{
            getSharedPreferences("nkd_theme",0).edit().putBoolean("amoled",w==1).apply();
            d.dismiss(); recreate();
        }).setNegativeButton("Отмена",null).show();
    }

    private void addGitHubDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(12,0,12,0);
        EditText url=field("https://github.com/user/repo"),name=field("Название приложения"),desc=field("Описание");
        box.addView(url);box.addView(name);box.addView(desc);
        new AlertDialog.Builder(this).setTitle("Добавить GitHub приложение").setView(box)
            .setPositiveButton("Добавить",(d,w)->{
                if(url.getText().toString().trim().isEmpty()){toast("Укажите репозиторий");return;}
                sources.addGitHub(url.getText().toString(),name.getText().toString().trim(),desc.getText().toString().trim());
                loadCatalog(true);
            }).setNegativeButton("Отмена",null).show();
    }

    private void addFdroidDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(12,0,12,0);
        EditText url=field("https://example.org/fdroid/repo/"),name=field("Название репозитория");
        box.addView(url);box.addView(name);
        new AlertDialog.Builder(this).setTitle("Добавить F-Droid репозиторий").setView(box)
            .setPositiveButton("Добавить",(d,w)->{
                if(url.getText().toString().trim().isEmpty()){toast("Укажите URL");return;}
                sources.addFdroid(url.getText().toString(),name.getText().toString().trim());
                loadCatalog(true);
            }).setNegativeButton("Отмена",null).show();
    }

    private EditText field(String hint){
        EditText e=new EditText(this);e.setHint(hint);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);
        e.setSingleLine(true);e.setPadding(8,8,8,8);return e;
    }

    private void showObtainiumMenu(){
        String[] items={"Войти на сайт","Импортировать JSON"};
        new AlertDialog.Builder(this).setTitle("Obtainium")
            .setItems(items,(d,w)->{if(w==0)openObtainium();else chooseObtainiumJson();})
            .setNegativeButton("Отмена",null).show();
    }

    private void openObtainium(){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://apps.obtainium.imranr.dev/")));}catch(Exception e){toast("Не удалось открыть Obtainium Apps");}
    }

    private void chooseObtainiumJson(){
        toast("Выбери JSON-файл Obtainium");
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,OBTAINIUM_JSON_REQUEST);}catch(Exception e){
            i=new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i,OBTAINIUM_JSON_REQUEST);
        }
    }

    private String readUriText(Uri uri)throws Exception{
        try(InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Файл недоступен");
            BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder();String line;
            while((line=r.readLine())!=null)b.append(line);
            return b.toString().trim();
        }
    }

    private JSONArray obtainiumApps(String text)throws Exception{
        if(text==null||text.trim().isEmpty())throw new IOException("Пустой JSON");
        String s=text.trim();

        if(s.startsWith("["))return new JSONArray(s);

        JSONObject root=new JSONObject(s);

        // Поддерживаем все основные форматы Obtainium:
        // 1) массив приложений
        // 2) {"apps":[...]}
        // 3) {"configs":[...]} — каталог apps.obtainium.imranr.dev
        // 4) {"config":{...}} — простой файл каталога
        JSONArray apps=root.optJSONArray("apps");
        if(apps!=null)return apps;

        JSONArray configs=root.optJSONArray("configs");
        if(configs!=null)return configs;

        JSONObject config=root.optJSONObject("config");
        if(config!=null){
            JSONArray one=new JSONArray();
            one.put(config);
            return one;
        }

        JSONArray one=new JSONArray();
        one.put(root);
        return one;
    }

    private String obtainiumDescription(JSONObject a){
        Object raw=a.opt("additionalSettings");
        if(raw instanceof JSONObject)return ((JSONObject)raw).optString("about","");
        if(raw instanceof String){
            String s=((String)raw).trim();
            if(!s.isEmpty())try{return new JSONObject(s).optString("about","");}catch(Exception ignored){}
        }
        return a.optString("description","");
    }

    private void importObtainiumJson(Uri uri){
        try{
            JSONArray apps=obtainiumApps(readUriText(uri));
            int added=0;
            for(int i=0;i<apps.length();i++){
                JSONObject a=apps.optJSONObject(i);if(a==null)continue;
                String url=a.optString("url","").trim();if(url.isEmpty())continue;
                String name=a.optString("name","").trim();if(name.isEmpty())name=url;
                String desc=obtainiumDescription(a);
                if(url.contains("github.com/")){sources.addGitHub(url,name,desc);added++;}
            }
            if(added>0){
                toast("Импортировано: "+added);
                loadCatalog(true);
            }else toast("В JSON не найдено поддерживаемых GitHub приложений");
        }catch(Exception e){toast("Ошибка импорта JSON: "+e.getMessage());}
    }

    private void chooseNkdFolder(){
        toast("Выбери папку, где лежат файлы .repo");
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i,NKD_FOLDER_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            if(requestCode==NKD_FOLDER_REQUEST){
                manager.saveStorageAccess(data.getData());
                toast("Папка NKD подключена. Ищу .repo…");
                loadCatalog(true);
            }
            else if(requestCode==OBTAINIUM_JSON_REQUEST)importObtainiumJson(data.getData());
        }
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder>{
        final class Holder extends RecyclerView.ViewHolder{
            ImageView icon; TextView title,cat,desc,install;
            Holder(View v,ImageView i,TextView t,TextView c,TextView d,TextView b){
                super(v);icon=i;title=t;cat=c;desc=d;install=b;
            }
        }
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int type){
            LinearLayout card=new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL); card.setPadding(20,18,20,18);
            GradientDrawable cbg=new GradientDrawable();
            cbg.setColor(getSharedPreferences("nkd_theme",0).getBoolean("amoled",false)?Color.rgb(8,8,8):Color.rgb(36,25,44));
            cbg.setCornerRadius(28); card.setBackground(cbg);
            LinearLayout head=new LinearLayout(MainActivity.this);
            head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon=new ImageView(MainActivity.this); icon.setImageResource(android.R.drawable.sym_def_app_icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable ibg=new GradientDrawable();ibg.setColor(Color.rgb(55,45,60));ibg.setCornerRadius(22);icon.setBackground(ibg);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(68,68);ip.rightMargin=16;head.addView(icon,ip);
            LinearLayout titles=new LinearLayout(MainActivity.this);titles.setOrientation(LinearLayout.VERTICAL);
            TextView t=label("",20,Color.WHITE);TextView c=label("",12,Color.rgb(205,170,235));
            titles.addView(t);titles.addView(c);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));card.addView(head);
            TextView d=label("",14,Color.rgb(205,205,210));d.setPadding(0,12,0,0);card.addView(d);
            TextView b=label("УСТАНОВИТЬ",13,Color.rgb(230,215,245));b.setGravity(Gravity.CENTER);
            GradientDrawable bb=new GradientDrawable();bb.setColor(Color.rgb(70,50,82));bb.setCornerRadius(22);b.setBackground(bb);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,48);bp.topMargin=12;card.addView(b,bp);
            card.setTag(new Object[]{icon,t,c,d,b});
            return new Holder(card,icon,t,c,d,b);
        }
        @Override public void onBindViewHolder(Holder h,int pos){
            CatalogManager.AppEntry a=displayedApps.get(pos);
            h.title.setText(a.name);h.cat.setText(a.category);h.desc.setText(a.description==null||a.description.isEmpty()?"Описание отсутствует":a.description);
            h.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            h.install.setOnClickListener(v->chooseRelease(a));
            loadIcon(h.icon,a.iconUrl);
        }
        @Override public int getItemCount(){return displayedApps.size();}
    }

    private void loadIcon(ImageView view,String url){
        if(url==null||url.trim().isEmpty())return;
        Object key=view.getTag();
        view.setTag(url);
        android.graphics.drawable.Drawable cached=null;
        final String wanted=url;
        iconExecutor.execute(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new java.net.URL(wanted).openConnection();
                c.setConnectTimeout(8000);c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent","New-KDroid/1.1.0");
                if(c.getResponseCode()>=200&&c.getResponseCode()<300){
                    android.graphics.Bitmap b=android.graphics.BitmapFactory.decodeStream(c.getInputStream());
                    if(b!=null)runOnUiThread(()->{
                        if(wanted.equals(view.getTag()))view.setImageBitmap(b);
                    });
                }
                c.disconnect();
            }catch(Exception ignored){}
        });
    }

}
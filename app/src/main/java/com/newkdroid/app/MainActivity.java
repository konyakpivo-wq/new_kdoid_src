package com.newkdroid.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import android.text.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout list;
    private EditText search;
    private TextView status;
    private final List<CatalogManager.AppEntry> allApps=new ArrayList<>();
    private CatalogManager manager;
    private SourceManager sources;
    private static final int NKD_FOLDER_REQUEST=1001;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setTheme(R.style.Theme_NewKDroid);
        applyTheme();
        setContentView(R.layout.activity_main);
        manager=new CatalogManager(this);
        sources=new SourceManager(this);
        list=findViewById(R.id.appList);
        search=findViewById(R.id.searchBox);
        status=findViewById(R.id.statusText);
        findViewById(R.id.refreshButton).setOnClickListener(v->loadCatalog(true));
        findViewById(R.id.settingsButton).setOnClickListener(v->showSettings());
        findViewById(R.id.newAppsButton).setOnClickListener(v->render(""));
        findViewById(R.id.appsButton).setOnClickListener(v->showCategory("Приложения"));
        findViewById(R.id.utilitiesButton).setOnClickListener(v->showCategory("Утилиты"));
        findViewById(R.id.systemButton).setOnClickListener(v->showCategory("Системные"));
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int d){}
            public void onTextChanged(CharSequence s,int a,int b,int c){render(s.toString());}
            public void afterTextChanged(Editable e){}
        });
        loadCatalog(false);
    }

    private void applyTheme(){
        int bg=Color.rgb(18,18,20);
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
                    loadCustomSources(manual);
                });
            }
            public void onError(Exception e){
                runOnUiThread(()->{
                    allApps.clear();
                    loadCustomSources(manual);
                });
            }
        });
    }

    private void loadCustomSources(boolean manual){
        sources.loadCustom(new SourceManager.Callback(){
            public void done(List<CatalogManager.AppEntry> custom){
                runOnUiThread(()->{
                    allApps.addAll(custom);
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
        list.removeAllViews();
        String q=q0==null?"":q0.trim().toLowerCase(Locale.ROOT);
        for(CatalogManager.AppEntry a:allApps){
            if(!q.isEmpty()&&!(a.name+" "+a.description+" "+a.category).toLowerCase(Locale.ROOT).contains(q))continue;
            addCard(a);
        }
        if(list.getChildCount()==0){
            TextView e=label("Ничего не найдено",18,Color.LTGRAY);
            e.setGravity(Gravity.CENTER);
            list.addView(e,new LinearLayout.LayoutParams(-1,180));
        }
    }

    private void showCategory(String c){
        list.removeAllViews();
        for(CatalogManager.AppEntry a:allApps)if(c.equals(a.category))addCard(a);
        if(list.getChildCount()==0){
            TextView e=label("В этой категории пока нет приложений",16,Color.LTGRAY);
            e.setGravity(Gravity.CENTER);
            list.addView(e,new LinearLayout.LayoutParams(-1,160));
        }
    }

    private void addCard(CatalogManager.AppEntry a){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24,20,24,20);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.rgb(36,36,40));
        bg.setCornerRadius(28);
        card.setBackground(bg);
        TextView t=label(a.name,20,Color.WHITE);
        TextView d=label(a.description,14,Color.rgb(205,205,210));
        TextView c=label(a.category,12,Color.rgb(190,170,230));
        TextView i=label("УСТАНОВИТЬ",13,Color.rgb(220,205,240));
        d.setPadding(0,7,0,7);
        i.setGravity(Gravity.CENTER);
        card.addView(t);card.addView(d);card.addView(c);
        GradientDrawable ib=new GradientDrawable();
        ib.setColor(Color.rgb(58,48,70));ib.setCornerRadius(22);i.setBackground(ib);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,48);
        ip.topMargin=12;card.addView(i,ip);
        i.setOnClickListener(v->chooseRelease(a));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(0,0,0,16);list.addView(card,cp);
    }

    private TextView label(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;
    }

    private void chooseRelease(CatalogManager.AppEntry a){
        if(a.repository.startsWith("FDROID|")){
            String[] p=a.repository.split("\\|",3);
            if(p.length==3) downloadFdroid(p[1],p[2],a.name);
            else toast("Некорректный F-Droid источник");
            return;
        }
        Toast.makeText(this,"Получаем версии…",Toast.LENGTH_SHORT).show();
        manager.loadReleases(a.repository,new CatalogManager.ReleasesCallback(){
            public void onSuccess(List<CatalogManager.ReleaseEntry> r){
                runOnUiThread(()->{
                    if(r.isEmpty()){toast("Релизов нет");return;}
                    if(r.size()==1)startDownload(r.get(0),a.name);
                    else{
                        String[] n=new String[r.size()];
                        for(int i=0;i<n.length;i++)n[i]=r.get(i).toString();
                        new AlertDialog.Builder(MainActivity.this).setTitle("Выберите версию")
                            .setItems(n,(d,w)->startDownload(r.get(w),a.name))
                            .setNegativeButton("Отмена",null).show();
                    }
                });
            }
            public void onError(Exception e){runOnUiThread(()->toast("Ошибка GitHub: "+e.getMessage()));}
        });
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
        TextView info=label("New KDroid 1.0.0\n\nИсточники: официальный F-Droid + добавленные тобой источники.",15,Color.LTGRAY);
        box.addView(info);
        addSettingButton(box,"＋ Добавить GitHub репозиторий",v->addGitHubDialog());
        addSettingButton(box,"＋ Добавить F-Droid репозиторий",v->addFdroidDialog());
        addSettingButton(box,"◈ Выбрать приложение из Obtainium",v->openObtainium());
        addSettingButton(box,"📁 Подключить папку NKD",v->chooseNkdFolder());
        new AlertDialog.Builder(this).setTitle("Настройки New KDroid").setView(box).setPositiveButton("Готово",null).show();
    }

    private void addSettingButton(LinearLayout box,String text,View.OnClickListener click){
        TextView b=label(text,16,Color.rgb(220,205,240));b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(18,18,18,18);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(58,58,64));bg.setCornerRadius(22);b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,60);p.topMargin=10;box.addView(b,p);b.setOnClickListener(click);
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

    private void openObtainium(){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://apps.obtainium.imranr.dev/")));}catch(Exception e){toast("Не удалось открыть Obtainium Apps");}
    }

    private void chooseNkdFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i,NKD_FOLDER_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==NKD_FOLDER_REQUEST&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            manager.saveStorageAccess(data.getData());
            toast("Папка NKD подключена");
            loadCatalog(true);
        }
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}

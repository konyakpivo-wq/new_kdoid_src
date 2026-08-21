package com.newkdroid.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout list;
    private EditText search;
    private TextView status;
    private final List<CatalogManager.AppEntry> allApps = new ArrayList<>();
    private CatalogManager manager;
    private int catalogVersion = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        manager = new CatalogManager();
        list = findViewById(R.id.appList);
        search = findViewById(R.id.searchBox);
        status = findViewById(R.id.statusText);

        findViewById(R.id.refreshButton).setOnClickListener(v -> loadCatalog(true));
        findViewById(R.id.settingsButton).setOnClickListener(v -> showSettings());
        findViewById(R.id.newAppsButton).setOnClickListener(v -> render("") );
        findViewById(R.id.appsButton).setOnClickListener(v -> showCategory("Приложения"));
        findViewById(R.id.utilitiesButton).setOnClickListener(v -> showCategory("Утилиты"));
        findViewById(R.id.systemButton).setOnClickListener(v -> showCategory("Системные"));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){ render(s.toString()); }
            public void afterTextChanged(Editable e){}
        });
        loadCatalog(false);
    }

    private void loadCatalog(boolean manual) {
        status.setText("Обновляем каталог…");
        manager.loadCatalog(new CatalogManager.Callback() {
            @Override public void onSuccess(List<CatalogManager.AppEntry> apps, int version) {
                runOnUiThread(() -> {
                    allApps.clear(); allApps.addAll(apps); catalogVersion = version;
                    status.setText("Каталог v" + version + " • " + apps.size() + " приложений");
                    render(search.getText().toString());
                    if (manual) Toast.makeText(MainActivity.this, "Каталог обновлён", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onError(Exception error) { runOnUiThread(() -> status.setText("Не удалось загрузить каталог. Проверьте интернет.")); }
        });
    }

    private void render(String query) {
        list.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (CatalogManager.AppEntry app : allApps) {
            if (!q.isEmpty() && !(app.name + " " + app.description + " " + app.category).toLowerCase(Locale.ROOT).contains(q)) continue;
            addCard(app);
        }
        if (list.getChildCount() == 0) {
            TextView empty = label("Ничего не найдено", 18, Color.GRAY);
            empty.setGravity(Gravity.CENTER); list.addView(empty, new LinearLayout.LayoutParams(-1, 180));
        }
    }

    private void showCategory(String category) {
        list.removeAllViews();
        for (CatalogManager.AppEntry app : allApps) if (category.equals(app.category)) addCard(app);
        if (list.getChildCount() == 0) {
            TextView empty = label("В этой категории пока нет приложений", 16, Color.GRAY);
            empty.setGravity(Gravity.CENTER); list.addView(empty, new LinearLayout.LayoutParams(-1, 160));
        }
    }

    private void addCard(CatalogManager.AppEntry app) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL); card.setPadding(24,20,24,20);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(28); card.setBackground(bg); card.setElevation(3);
        TextView title = label(app.name, 20, Color.rgb(30,30,34));
        TextView desc = label(app.description, 14, Color.rgb(100,100,105)); desc.setPadding(0,7,0,7);
        TextView cat = label(app.category, 12, Color.rgb(103,80,164));
        TextView install = label("УСТАНОВИТЬ", 13, Color.rgb(103,80,164)); install.setGravity(Gravity.CENTER);
        card.addView(title); card.addView(desc); card.addView(cat);
        GradientDrawable ibg = new GradientDrawable(); ibg.setColor(Color.rgb(244,239,251)); ibg.setCornerRadius(22); install.setBackground(ibg);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, 48); ip.topMargin=12; card.addView(install, ip);
        install.setOnClickListener(v -> chooseRelease(app));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(0,0,0,16); list.addView(card, cp);
    }

    private TextView label(String text, int size, int color) { TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); return t; }

    private void chooseRelease(CatalogManager.AppEntry app) {
        Toast.makeText(this, "Получаем версии…", Toast.LENGTH_SHORT).show();
        manager.loadReleases(app.repository, new CatalogManager.ReleasesCallback() {
            @Override public void onSuccess(List<CatalogManager.ReleaseEntry> releases) {
                runOnUiThread(() -> {
                    if (releases.isEmpty()) { Toast.makeText(MainActivity.this,"Релизов нет",Toast.LENGTH_SHORT).show(); return; }
                    String[] names = new String[releases.size()]; for(int i=0;i<names.length;i++) names[i]=releases.get(i).toString();
                    if (releases.size()==1) openRelease(releases.get(0));
                    else new AlertDialog.Builder(MainActivity.this).setTitle("Выберите версию").setItems(names,(d,w)->openRelease(releases.get(w))).setNegativeButton("Отмена",null).show();
                });
            }
            @Override public void onError(Exception error) { runOnUiThread(() -> Toast.makeText(MainActivity.this,"Ошибка GitHub",Toast.LENGTH_LONG).show()); }
        });
    }

    private void openRelease(CatalogManager.ReleaseEntry release) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)));
    }

    private void showSettings() {
        new AlertDialog.Builder(this).setTitle("Настройки New KDroid")
                .setMessage("Каталог: v" + catalogVersion + "\n\nПредложения по добавлению репозиториев:\ndemoda228@gmail.com\n\nNew KDroid использует GitHub как источник приложений и релизов.")
                .setPositiveButton("OK", null).show();
    }
}

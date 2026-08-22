package com.newkdroid.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class AdminActivity extends Activity {
    private final List<String> repositories = new ArrayList<>();
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 24, 24, 24);
        setContentView(content);
        render();
    }

    private void render() {
        content.removeAllViews();
        TextView title = text("Админ-панель New KDroid", 26, Color.rgb(35,35,40));
        content.addView(title);
        TextView info = text("Управление каталогом и репозиториями", 15, Color.GRAY);
        info.setPadding(0, 8, 0, 24); content.addView(info);
        addButton("➕ Добавить репозиторий", v -> addRepository());
        addButton("📋 Управление репозиториями", v -> manageRepositories());
        addButton("🔄 Создать обновление каталога", v -> createCatalogUpdate());
        addButton("📊 Информация о каталоге", v -> showInfo());
        addButton("🚪 Выйти из админ-режима", v -> finish());
    }

    private void addRepository() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        EditText name = field("Название приложения");
        EditText repo = field("GitHub repository: owner/name");
        EditText category = field("Категория");
        EditText description = field("Описание");
        box.addView(name); box.addView(repo); box.addView(category); box.addView(description);
        new AlertDialog.Builder(this).setTitle("Добавить репозиторий").setView(box)
            .setPositiveButton("Добавить", (d,w) -> {
                String value = name.getText().toString().trim() + " — " + repo.getText().toString().trim();
                if (!name.getText().toString().trim().isEmpty() && !repo.getText().toString().trim().isEmpty()) repositories.add(value);
                Toast.makeText(this, "Репозиторий добавлен в черновик", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Отмена", null).show();
    }

    private void manageRepositories() {
        if (repositories.isEmpty()) { Toast.makeText(this, "Черновиков пока нет", Toast.LENGTH_SHORT).show(); return; }
        String[] items = repositories.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Черновики репозиториев").setItems(items, (d, which) -> {
            repositories.remove(which); Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Закрыть", null).show();
    }

    private void createCatalogUpdate() {
        EditText version = field("Новая версия каталога, например 2");
        EditText notes = field("Что добавлено в обновлении");
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.addView(version); box.addView(notes);
        new AlertDialog.Builder(this).setTitle("Новое обновление каталога").setView(box)
            .setPositiveButton("Создать", (d,w) -> Toast.makeText(this, "Черновик обновления создан", Toast.LENGTH_SHORT).show())
            .setNegativeButton("Отмена", null).show();
    }

    private void showInfo() {
        new AlertDialog.Builder(this).setTitle("Каталог").setMessage("Репозиториев в черновике: " + repositories.size() + "\n\nПубликация в GitHub пока выполняется отдельным безопасным этапом.").setPositiveButton("OK", null).show();
    }

    private EditText field(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(false); e.setPadding(8, 10, 8, 10); return e; }
    private TextView text(String s, int size, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private void addButton(String label, android.view.View.OnClickListener listener) {
        TextView b = text(label, 16, Color.rgb(80,60,130)); b.setGravity(Gravity.CENTER_VERTICAL); b.setPadding(20, 16, 20, 16);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(244,240,250)); bg.setCornerRadius(24); b.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 58); p.setMargins(0,0,0,12); content.addView(b,p); b.setOnClickListener(listener);
    }
}

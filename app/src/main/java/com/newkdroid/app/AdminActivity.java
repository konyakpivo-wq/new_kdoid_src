package com.newkdroid.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Development/admin panel. The login is intentionally local for this first version.
 * Do not use the embedded credential scheme for a public production release.
 */
public class AdminActivity extends Activity {
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_CODE = "iixc_hgxd_kyft";
    private static final String PREFS = "new_kdroid_admin";
    private static final String KEY_DRAFTS = "draft_repositories";
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showLogin();
    }

    private void showLogin() {
        LinearLayout box = box();
        EditText user = field("Логин");
        EditText code = field("Код администратора");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button login = button("Войти");
        box.addView(user); box.addView(code); box.addView(login);
        setContentView(box);
        login.setOnClickListener(v -> {
            if (ADMIN_USER.equals(user.getText().toString().trim()) && ADMIN_CODE.equals(code.getText().toString())) {
                showPanel();
            } else {
                Toast.makeText(this, "Неверные данные администратора", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPanel() {
        LinearLayout root = box();
        TextView title = text("Административная панель", 24);
        TextView info = text("Управление каталогом New KDroid", 15);
        root.addView(title); root.addView(info);

        Button add = button("+ Добавить репозиторий");
        Button drafts = button("Черновики обновления");
        Button version = button("Создать обновление каталога");
        Button logout = button("Выйти");
        root.addView(add); root.addView(drafts); root.addView(version); root.addView(logout);

        add.setOnClickListener(v -> addRepository());
        drafts.setOnClickListener(v -> showDrafts());
        version.setOnClickListener(v -> createUpdate());
        logout.setOnClickListener(v -> showLogin());
        setContentView(root);
    }

    private void addRepository() {
        LinearLayout box = box();
        EditText name = field("Название приложения");
        EditText repo = field("GitHub repository (owner/name)");
        EditText category = field("Категория");
        EditText description = field("Описание");
        box.addView(name); box.addView(repo); box.addView(category); box.addView(description);
        new AlertDialog.Builder(this).setTitle("Добавить репозиторий").setView(box)
                .setPositiveButton("Добавить", (d, w) -> {
                    String r = repo.getText().toString().trim();
                    if (!r.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                        Toast.makeText(this, "Неверный формат owner/name", Toast.LENGTH_LONG).show(); return;
                    }
                    String item = name.getText().toString().trim() + "|" + r + "|" + category.getText().toString().trim() + "|" + description.getText().toString().trim();
                    String old = prefs.getString(KEY_DRAFTS, "");
                    prefs.edit().putString(KEY_DRAFTS, old.isEmpty() ? item : old + "\n" + item).apply();
                    Toast.makeText(this, "Репозиторий добавлен в черновик", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Отмена", null).show();
    }

    private void showDrafts() {
        String drafts = prefs.getString(KEY_DRAFTS, "");
        if (drafts.isEmpty()) drafts = "Черновиков пока нет.";
        new AlertDialog.Builder(this).setTitle("Черновики").setMessage(drafts.replace("|", " • "))
                .setPositiveButton("OK", null).setNeutralButton("Очистить", (d, w) -> prefs.edit().remove(KEY_DRAFTS).apply()).show();
    }

    private void createUpdate() {
        String drafts = prefs.getString(KEY_DRAFTS, "");
        if (drafts.isEmpty()) {
            Toast.makeText(this, "Сначала добавьте хотя бы один репозиторий", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText note = field("Описание обновления, например: Добавлены новые приложения");
        new AlertDialog.Builder(this).setTitle("Новое обновление каталога").setView(note)
                .setMessage("Будет создано локальное обновление из текущих черновиков. Публикацию в GitHub подключим через безопасную авторизацию.")
                .setPositiveButton("Создать", (d, w) -> {
                    int oldVersion = prefs.getInt("catalog_version", 1);
                    prefs.edit().putInt("catalog_version", oldVersion + 1).putString("last_update_note", note.getText().toString()).apply();
                    Toast.makeText(this, "Обновление каталога v" + (oldVersion + 1) + " создано", Toast.LENGTH_LONG).show();
                }).setNegativeButton("Отмена", null).show();
    }

    private LinearLayout box() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(32, 32, 32, 32); l.setGravity(Gravity.CENTER_HORIZONTAL); return l;
    }
    private EditText field(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(false); e.setPadding(8, 12, 8, 12); return e; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setMinHeight(54); return b; }
    private TextView text(String s, int size) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setPadding(0, 8, 0, 20); return t; }
}

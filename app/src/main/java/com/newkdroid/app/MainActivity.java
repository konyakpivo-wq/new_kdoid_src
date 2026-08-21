package com.newkdroid.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setAction(R.id.newAppsButton, "Новинки");
        setAction(R.id.appsButton, "Приложения");
        setAction(R.id.utilitiesButton, "Утилиты");
        setAction(R.id.systemButton, "Системные приложения");
        setAction(R.id.settingsButton, "Настройки");
        setAction(R.id.searchButton, "Поиск");
        setAction(R.id.updateButton, "Проверка обновлений каталога");
    }

    private void setAction(int id, String title) {
        findViewById(id).setOnClickListener(v ->
                Toast.makeText(this, title + " — скоро будет доступно", Toast.LENGTH_SHORT).show()
        );
    }
}

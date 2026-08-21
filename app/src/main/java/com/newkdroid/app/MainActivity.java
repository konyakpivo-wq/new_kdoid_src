package com.newkdroid.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int[] buttons = {
                R.id.newAppsButton,
                R.id.appsButton,
                R.id.utilitiesButton,
                R.id.systemButton,
                R.id.settingsButton
        };

        for (int id : buttons) {
            findViewById(id).setOnClickListener(v ->
                    Toast.makeText(this, "Раздел будет добавлен на следующем этапе", Toast.LENGTH_SHORT).show()
            );
        }
    }
}

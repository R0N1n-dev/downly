package com.devn1n.downly;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DownlyPlugin.class);
        super.onCreate(savedInstanceState);
        DownlyPlugin.remember(getIntent());

        // Edge-to-edge the recommended way: draw behind the bars, then pad the content by the bar and keyboard
        // insets so the web view never sits under the status bar, camera cut-out or navigation bar.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        DownlyPlugin.remember(intent);
    }
}

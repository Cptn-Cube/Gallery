package com.example.gallery;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        findViewById(R.id.btnImage).setOnClickListener(v ->
                openMedia("image"));

        findViewById(R.id.btnVideo).setOnClickListener(v ->
                openMedia("video"));
    }

    private void openMedia(String type) {
        Intent intent = new Intent(this, MediaListActivity.class);
        intent.putExtra("type", type);
        startActivity(intent);
    }
}
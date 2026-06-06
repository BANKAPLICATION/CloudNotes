package com.cloudnotes.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudnotes.app.R;
import com.cloudnotes.app.adapters.AchievementsAdapter;
import com.cloudnotes.app.utility.Achievement;
import com.cloudnotes.app.utility.AchievementManager;

import java.util.List;

public class AchievementsActivity extends AppCompatActivity {

    private AchievementsAdapter adapter;
    private TextView textAchievementProgress;
    private ProgressBar progressAchievements;
    private ProgressBar progressLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievements);

        Toolbar toolbar = findViewById(R.id.achievements_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        textAchievementProgress = findViewById(R.id.textAchievementProgress);
        progressAchievements = findViewById(R.id.progressAchievements);
        progressLoading = findViewById(R.id.progressLoading);

        RecyclerView recyclerView = findViewById(R.id.recyclerAchievements);
        adapter = new AchievementsAdapter();
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAchievements();
    }

    private void loadAchievements() {
        progressLoading.setVisibility(View.VISIBLE);

        AchievementManager.syncInBackground(this, new AchievementManager.SyncCallback() {
            @Override
            public void onComplete(List<Achievement> achievements, List<Achievement> newlyUnlocked) {
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    adapter.setAchievements(achievements);
                    updateProgressHeader(achievements);
                    showUnlockToasts(newlyUnlocked);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    Toast.makeText(
                            AchievementsActivity.this,
                            getString(R.string.achievements_load_error),
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void updateProgressHeader(List<Achievement> achievements) {
        int unlocked = 0;
        for (Achievement achievement : achievements) {
            if (achievement.isUnlocked()) {
                unlocked++;
            }
        }

        int total = achievements.size();
        textAchievementProgress.setText(getString(R.string.achievements_progress_format, unlocked, total));

        int percent = total == 0 ? 0 : (unlocked * 100) / total;
        progressAchievements.setProgress(percent);
    }

    private void showUnlockToasts(List<Achievement> newlyUnlocked) {
        if (newlyUnlocked == null || newlyUnlocked.isEmpty()) {
            return;
        }

        for (Achievement achievement : newlyUnlocked) {
            Toast.makeText(
                    this,
                    getString(R.string.achievement_unlocked_format, achievement.getEmoji(), achievement.getTitle()),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}

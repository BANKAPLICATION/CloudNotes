package com.cloudnotes.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudnotes.app.R;
import com.cloudnotes.app.utility.Achievement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AchievementsAdapter extends RecyclerView.Adapter<AchievementsAdapter.BadgeViewHolder> {

    private final List<Achievement> achievements = new ArrayList<>();

    public void setAchievements(List<Achievement> items) {
        achievements.clear();
        if (items != null) {
            achievements.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BadgeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_achievement_badge, parent, false);
        return new BadgeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BadgeViewHolder holder, int position) {
        Achievement achievement = achievements.get(position);
        holder.bind(achievement);
    }

    @Override
    public int getItemCount() {
        return achievements.size();
    }

    static class BadgeViewHolder extends RecyclerView.ViewHolder {

        private final TextView textBadgeEmoji;
        private final TextView textLockedOverlay;
        private final TextView textBadgeTitle;
        private final TextView textBadgeDescription;
        private final TextView textBadgeCategory;

        BadgeViewHolder(@NonNull View itemView) {
            super(itemView);
            textBadgeEmoji = itemView.findViewById(R.id.textBadgeEmoji);
            textLockedOverlay = itemView.findViewById(R.id.textLockedOverlay);
            textBadgeTitle = itemView.findViewById(R.id.textBadgeTitle);
            textBadgeDescription = itemView.findViewById(R.id.textBadgeDescription);
            textBadgeCategory = itemView.findViewById(R.id.textBadgeCategory);
        }

        void bind(Achievement achievement) {
            textBadgeEmoji.setText(achievement.getEmoji());
            textBadgeTitle.setText(achievement.getTitle());
            textBadgeDescription.setText(achievement.getDescription());
            textBadgeCategory.setText(achievement.getCategory().toUpperCase(Locale.US));

            if (achievement.isUnlocked()) {
                textBadgeEmoji.setAlpha(1f);
                textBadgeEmoji.setBackgroundResource(R.drawable.bg_achievement_badge_unlocked);
                textLockedOverlay.setVisibility(View.GONE);
                textBadgeTitle.setAlpha(1f);
                textBadgeDescription.setAlpha(1f);
            } else {
                textBadgeEmoji.setAlpha(0.35f);
                textBadgeEmoji.setBackgroundResource(R.drawable.bg_achievement_badge_locked);
                textLockedOverlay.setVisibility(View.VISIBLE);
                textBadgeTitle.setAlpha(0.55f);
                textBadgeDescription.setAlpha(0.55f);
            }
        }
    }
}

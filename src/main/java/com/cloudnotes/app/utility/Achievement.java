package com.cloudnotes.app.utility;

public class Achievement {

    private final String id;
    private final String title;
    private final String description;
    private final String emoji;
    private final String category;
    private boolean unlocked;

    public Achievement(String id, String title, String description, String emoji, String category) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.emoji = emoji;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getCategory() {
        return category;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }
}

package com.cloudnotes.app.utility;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.cloudnotes.app.entities.FitnessRecord;
import com.cloudnotes.app.database.DynamoDBHelper;
import com.cloudnotes.app.entities.Note;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AchievementManager {

    private static final String TAG = "AchievementManager";
    private static final String PREFS = "cloudnotes_achievements";
    private static final String KEY_UNLOCKED = "unlocked_ids";
    private static final String KEY_DEMO_FITNESS = "event_demo_fitness";
    private static final String KEY_PROFILE_SAVE = "event_profile_save";
    private static final String KEY_PROFILE_PHOTO = "event_profile_photo";

    public static final String ID_FIRST_NOTE = "first_note";
    public static final String ID_NOTE_5 = "note_5";
    public static final String ID_NOTE_10 = "note_10";
    public static final String ID_TAG_STARTER = "tag_starter";
    public static final String ID_TAG_MASTER = "tag_master";
    public static final String ID_IMAGE_NOTE = "image_note";
    public static final String ID_WEB_LINK = "web_link";
    public static final String ID_STEPS_ANY = "steps_any";
    public static final String ID_STEPS_5K = "steps_5k";
    public static final String ID_STEPS_10K = "steps_10k";
    public static final String ID_DEMO_FITNESS = "demo_fitness";
    public static final String ID_PROFILE_SAVE = "profile_save";
    public static final String ID_PROFILE_PHOTO = "profile_photo";

    public interface SyncCallback {
        void onComplete(List<Achievement> achievements, List<Achievement> newlyUnlocked);

        void onError(Exception error);
    }

    private AchievementManager() {
    }

    public static List<Achievement> buildDefinitions() {
        List<Achievement> list = new ArrayList<>();
        list.add(new Achievement(ID_FIRST_NOTE, "First Cloud Note", "Create your first note", "📝", "notes"));
        list.add(new Achievement(ID_NOTE_5, "Note Explorer", "Create 5 notes", "📚", "notes"));
        list.add(new Achievement(ID_NOTE_10, "Cloud Archivist", "Create 10 notes", "☁️", "notes"));
        list.add(new Achievement(ID_TAG_STARTER, "Tag Starter", "Add a tag to a note", "🏷️", "notes"));
        list.add(new Achievement(ID_TAG_MASTER, "Tag Master", "Use 3 different tags", "🎯", "notes"));
        list.add(new Achievement(ID_IMAGE_NOTE, "Visual Thinker", "Attach an image to a note", "🖼️", "notes"));
        list.add(new Achievement(ID_WEB_LINK, "Link Collector", "Save a web link in a note", "🔗", "notes"));
        list.add(new Achievement(ID_STEPS_ANY, "Step Starter", "Record fitness activity", "👟", "fitness"));
        list.add(new Achievement(ID_STEPS_5K, "5K Walker", "Reach 5,000 steps in a day", "🚶", "fitness"));
        list.add(new Achievement(ID_STEPS_10K, "Goal Crusher", "Hit the 10,000 step daily goal", "🏆", "fitness"));
        list.add(new Achievement(ID_DEMO_FITNESS, "Demo Hero", "Load demo fitness data", "🎮", "fitness"));
        list.add(new Achievement(ID_PROFILE_SAVE, "Profile Pro", "Update your profile info", "👤", "profile"));
        list.add(new Achievement(ID_PROFILE_PHOTO, "Snapshot", "Upload a profile photo", "📸", "profile"));
        return list;
    }

    public static void markEvent(Context context, String eventKey) {
        getPrefs(context).edit().putBoolean(eventKey, true).apply();
    }

    public static void markDemoFitnessLoaded(Context context) {
        markEvent(context, KEY_DEMO_FITNESS);
    }

    public static void markProfileUpdated(Context context) {
        markEvent(context, KEY_PROFILE_SAVE);
    }

    public static void markProfilePhotoUploaded(Context context) {
        markEvent(context, KEY_PROFILE_PHOTO);
    }

    public static int getUnlockedCount(Context context) {
        return getUnlockedIds(context).size();
    }

    public static int getTotalCount() {
        return buildDefinitions().size();
    }

    public static void syncInBackground(Context context, SyncCallback callback) {
        new Thread(() -> {
            try {
                AchievementStats stats = loadStats(context);
                SyncResult result = syncAchievements(context, stats);
                if (callback != null) {
                    callback.onComplete(result.all, result.newlyUnlocked);
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync failed", e);
                if (callback != null) {
                    callback.onError(e);
                }
            }
        }).start();
    }

    public static SyncResult syncAchievements(Context context, AchievementStats stats) {
        Set<String> unlocked = getUnlockedIds(context);
        List<Achievement> definitions = buildDefinitions();
        List<Achievement> newlyUnlocked = new ArrayList<>();

        for (Achievement achievement : definitions) {
            if (isMet(achievement.getId(), stats) && !unlocked.contains(achievement.getId())) {
                unlocked.add(achievement.getId());
                achievement.setUnlocked(true);
                newlyUnlocked.add(achievement);
            } else {
                achievement.setUnlocked(unlocked.contains(achievement.getId()));
            }
        }

        saveUnlockedIds(context, unlocked);
        return new SyncResult(definitions, newlyUnlocked);
    }

    private static boolean isMet(String id, AchievementStats stats) {
        switch (id) {
            case ID_FIRST_NOTE:
                return stats.noteCount >= 1;
            case ID_NOTE_5:
                return stats.noteCount >= 5;
            case ID_NOTE_10:
                return stats.noteCount >= 10;
            case ID_TAG_STARTER:
                return stats.uniqueTagCount >= 1;
            case ID_TAG_MASTER:
                return stats.uniqueTagCount >= 3;
            case ID_IMAGE_NOTE:
                return stats.hasImageNote;
            case ID_WEB_LINK:
                return stats.hasWebLinkNote;
            case ID_STEPS_ANY:
                return stats.maxStepsInDay > 0;
            case ID_STEPS_5K:
                return stats.maxStepsInDay >= 5000;
            case ID_STEPS_10K:
                return stats.maxStepsInDay >= 10000;
            case ID_DEMO_FITNESS:
                return stats.demoFitnessLoaded;
            case ID_PROFILE_SAVE:
                return stats.profileUpdated;
            case ID_PROFILE_PHOTO:
                return stats.profilePhotoUploaded;
            default:
                return false;
        }
    }

    private static AchievementStats loadStats(Context context) {
        AchievementStats stats = new AchievementStats();
        SharedPreferences prefs = getPrefs(context);

        stats.demoFitnessLoaded = prefs.getBoolean(KEY_DEMO_FITNESS, false);
        stats.profileUpdated = prefs.getBoolean(KEY_PROFILE_SAVE, false);
        stats.profilePhotoUploaded = prefs.getBoolean(KEY_PROFILE_PHOTO, false);

        try {
            String notesUserId = AWSMobileClient.getInstance().getIdentityId();
            String fitnessUserId = AWSMobileClient.getInstance().getUsername();
            if (notesUserId == null || notesUserId.isEmpty()) {
                return stats;
            }

            AmazonDynamoDBClient client = new AmazonDynamoDBClient(AWSMobileClient.getInstance());
            client.setRegion(Region.getRegion(Regions.EU_NORTH_1));
            DynamoDBMapper mapper = DynamoDBMapper.builder().dynamoDBClient(client).build();
            DynamoDBHelper helper = new DynamoDBHelper(mapper);

            List<Note> notes = helper.getAllNotes(notesUserId);
            stats.noteCount = notes.size();

            Set<String> tags = new HashSet<>();
            for (Note note : notes) {
                if (note.getTags() != null && !note.getTags().trim().isEmpty()) {
                    tags.add(note.getTags().trim().toLowerCase());
                }
                if (note.getImagePath() != null && !note.getImagePath().trim().isEmpty()) {
                    stats.hasImageNote = true;
                }
                if (note.getWebLink() != null && !note.getWebLink().trim().isEmpty()) {
                    stats.hasWebLinkNote = true;
                }
            }
            stats.uniqueTagCount = tags.size();
            if (fitnessUserId != null && !fitnessUserId.isEmpty()) {
                stats.maxStepsInDay = loadMaxStepsToday(mapper, fitnessUserId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not load cloud stats", e);
        }

        return stats;
    }

    private static int loadMaxStepsToday(DynamoDBMapper mapper, String userId) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        FitnessRecord hashKey = new FitnessRecord();
        hashKey.setUserId(userId);

        DynamoDBQueryExpression<FitnessRecord> query = new DynamoDBQueryExpression<FitnessRecord>()
                .withHashKeyValues(hashKey)
                .withRangeKeyCondition("timestamp",
                        new Condition()
                                .withComparisonOperator(ComparisonOperator.BETWEEN)
                                .withAttributeValueList(
                                        new AttributeValue().withN(String.valueOf(startTime)),
                                        new AttributeValue().withN(String.valueOf(endTime))
                                ));

        List<FitnessRecord> results = mapper.query(FitnessRecord.class, query);
        int maxSteps = 0;
        for (FitnessRecord item : results) {
            maxSteps = Math.max(maxSteps, item.getSteps());
        }
        return maxSteps;
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> getUnlockedIds(Context context) {
        Set<String> stored = getPrefs(context).getStringSet(KEY_UNLOCKED, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    private static void saveUnlockedIds(Context context, Set<String> ids) {
        getPrefs(context).edit().putStringSet(KEY_UNLOCKED, ids).apply();
    }

    public static void syncAndNotify(Context context) {
        syncInBackground(context, new SyncCallback() {
            @Override
            public void onComplete(List<Achievement> achievements, List<Achievement> newlyUnlocked) {
                if (newlyUnlocked == null || newlyUnlocked.isEmpty()) {
                    return;
                }
                for (Achievement achievement : newlyUnlocked) {
                    android.widget.Toast.makeText(
                            context.getApplicationContext(),
                            achievement.getEmoji() + " " + achievement.getTitle() + " unlocked!",
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "Background sync failed", error);
            }
        });
    }

    public static class SyncResult {
        public final List<Achievement> all;
        public final List<Achievement> newlyUnlocked;

        SyncResult(List<Achievement> all, List<Achievement> newlyUnlocked) {
            this.all = all;
            this.newlyUnlocked = newlyUnlocked;
        }
    }
}

package com.cloudnotes.app.config;

/**
 * App-wide constants. AWS credentials and pool IDs live in
 * res/raw/awsconfiguration.json and amplifyconfiguration.json (not in git).
 */
public final class CloudConfig {

    public static final String AWS_REGION = "eu-north-1";
    public static final String S3_BUCKET_NAME = "YOUR_S3_BUCKET_NAME";

    public static final String NOTES_TABLE = "UserNotesPro";
    public static final String FITNESS_TABLE = "FitnessData";

    public static final int DAILY_STEP_GOAL = 10_000;
    public static final String DEEP_LINK_SCHEME = "cloudnotes";

    private CloudConfig() {
    }
}

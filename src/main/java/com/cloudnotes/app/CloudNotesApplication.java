package com.cloudnotes.app;

import android.app.Application;
import android.util.Log;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amplifyframework.AmplifyException;
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin;
import com.amplifyframework.core.Amplify;
import com.amplifyframework.storage.s3.AWSS3StoragePlugin;
import com.cloudnotes.app.config.CloudConfig;
import com.cloudnotes.app.database.DynamoDBHelper;

public class CloudNotesApplication extends Application {

    private static final String TAG = "CloudNotesApp";
    private static volatile boolean isAmplifyInitialized = false;
    private static final Object initLock = new Object();
    private static DynamoDBMapper dynamoDBMapper;
    private static DynamoDBHelper dbHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        initializeAmplify();
    }

    private void initializeAmplify() {
        try {
            synchronized (initLock) {
                if (!isAmplifyInitialized) {
                    Amplify.addPlugin(new AWSCognitoAuthPlugin());
                    Amplify.addPlugin(new AWSS3StoragePlugin());
                    Amplify.configure(getApplicationContext());
                    isAmplifyInitialized = true;
                    initLock.notifyAll();
                    Log.i(TAG, "Amplify configured");
                }
            }

            AWSMobileClient.getInstance().initialize(getApplicationContext(), new Callback<UserStateDetails>() {
                @Override
                public void onResult(UserStateDetails result) {
                    Log.i(TAG, "AWSMobileClient ready: " + result.getUserState());
                    initializeDynamoDb();
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "AWSMobileClient init failed", e);
                }
            });
        } catch (AmplifyException e) {
            Log.e(TAG, "Amplify init failed", e);
            synchronized (initLock) {
                isAmplifyInitialized = false;
                initLock.notifyAll();
            }
        }
    }

    public static boolean waitForAmplifyInit(long timeoutMillis) {
        if (isAmplifyInitialized) {
            return true;
        }

        synchronized (initLock) {
            if (!isAmplifyInitialized) {
                try {
                    initLock.wait(timeoutMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return isAmplifyInitialized;
    }

    private void initializeDynamoDb() {
        try {
            AmazonDynamoDBClient client = new AmazonDynamoDBClient(AWSMobileClient.getInstance());
            client.setRegion(Region.getRegion(Regions.fromName(CloudConfig.AWS_REGION)));

            dynamoDBMapper = DynamoDBMapper.builder()
                    .dynamoDBClient(client)
                    .build();
            dbHelper = new DynamoDBHelper(dynamoDBMapper);

            Log.i(TAG, "DynamoDB mapper ready");
        } catch (Exception e) {
            Log.e(TAG, "DynamoDB init failed", e);
        }
    }

    public static DynamoDBMapper getDynamoDBMapper() {
        return dynamoDBMapper;
    }

    public static DynamoDBHelper getDbHelper() {
        return dbHelper;
    }
}

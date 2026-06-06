package com.cloudnotes.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.cloudnotes.app.R;
import com.cloudnotes.app.activities.FitnessActivity;
import com.cloudnotes.app.entities.FitnessRecord;

public class FitnessUploadService extends Service implements SensorEventListener {
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "FitnessServiceChannel";
    private static final int UPLOAD_INTERVAL = 15 * 60 * 1000; // 15 minutes

    private Handler handler = new Handler();
    private DynamoDBMapper dynamoDBMapper;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private int initialSteps = -1;
    private int currentSteps = 0;

    private Runnable uploadRunnable = new Runnable() {
        @Override
        public void run() {
            uploadFitnessData();
            handler.postDelayed(this, UPLOAD_INTERVAL);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        setupDynamoDB();
        setupStepSensor();
        handler.post(uploadRunnable);

        return START_STICKY;
    }

    private void setupStepSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Fitness Tracking Service",
                    NotificationManager.IMPORTANCE_LOW);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, FitnessActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fitness Tracking Active")
                .setContentText("Counting steps: " + currentSteps)
                .setSmallIcon(R.drawable.note_logo) // Use your app icon for now
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void setupDynamoDB() {
        AmazonDynamoDBClient dynamoDBClient = new AmazonDynamoDBClient(AWSMobileClient.getInstance());
        dynamoDBClient.setRegion(Region.getRegion(Regions.EU_NORTH_1));
        this.dynamoDBMapper = DynamoDBMapper.builder()
                .dynamoDBClient(dynamoDBClient)
                .build();
    }

    private void uploadFitnessData() {
        float distance = currentSteps * 0.7f; // approximate distance

        FitnessRecord item = new FitnessRecord();
        item.setUserId(AWSMobileClient.getInstance().getUsername());
        item.setTimestamp(System.currentTimeMillis());
        item.setSteps(currentSteps);
        item.setDistance(distance);

        new Thread(() -> {
            try {
                dynamoDBMapper.save(item);
                Log.d("UPLOAD", "Data uploaded successfully: Steps=" + currentSteps);

                // Update notification with current steps
                NotificationManager notificationManager =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(NOTIFICATION_ID, buildNotification());
            } catch (Exception e) {
                Log.e("UPLOAD", "Error uploading data: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            if (initialSteps < 0) {
                initialSteps = (int) event.values[0];
            }
            currentSteps = (int) event.values[0] - initialSteps;
            Log.d("STEPS", "Current steps: " + currentSteps);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (stepSensor != null) {
            sensorManager.unregisterListener(this);
        }
        handler.removeCallbacks(uploadRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
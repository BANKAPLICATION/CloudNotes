package com.cloudnotes.app.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;

import androidx.annotation.ColorRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.cloudnotes.app.config.CloudConfig;
import com.cloudnotes.app.entities.FitnessRecord;
import com.cloudnotes.app.R;
import com.cloudnotes.app.services.FitnessUploadService;
import com.cloudnotes.app.utility.AchievementManager;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.amazonaws.regions.Regions;
import androidx.core.graphics.ColorUtils;
import android.graphics.drawable.GradientDrawable;

public class FitnessActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "FitnessActivity";
    private static final int DAILY_STEP_GOAL = CloudConfig.DAILY_STEP_GOAL;
    private TextView stepsTextView;
    private TextView distanceTextView;
    private TextView lastUpdatedText;
    private TextView stepGoalProgressText;
    private TextView stepGoalStatusText;
    private ProgressBar stepGoalProgressBar;
    private MaterialButton demoFitnessButton;
    private LineChart fitnessChart;
    private DynamoDBMapper dynamoDBMapper;

    private static final int PERMISSION_REQUEST_ACTIVITY_RECOGNITION = 45;

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private int initialSteps = -1;
    private long lastSaveTime = 0;
    private static final long SAVE_INTERVAL = 5000; // 5 seconds


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fitness);

        // Request permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this,
                            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.ACTIVITY_RECOGNITION,
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        PERMISSION_REQUEST_ACTIVITY_RECOGNITION);
            } else {
                startFitnessService();
            }
        }


        initializeViews();
        initializeAWS();
        startStepCounting();

        //startFitnessService();
        resetStepCounterAtMidnight();
        //addTestData();
    }


    // Add this method to test the graph
    private void addTestData() {
        List<Entry> stepEntries = new ArrayList<>();
        List<Entry> distanceEntries = new ArrayList<>();

        // Create a smooth sine-like curve over 24 hours
        for (int hour = 0; hour < 24; hour++) {
            // Steps curve: using sine function for smooth curve
            float stepValue = (float) (5000 + 5000 * Math.sin(hour * Math.PI / 12));
            stepEntries.add(new Entry(hour, stepValue));

            // Distance curve (0.7m per step)
            float distanceValue = stepValue * 0.7f / 1000; // Convert to kilometers
            distanceEntries.add(new Entry(hour, distanceValue));
        }

        // Update UI with test data
        updateUI(10000, 7.0f, stepEntries, distanceEntries);
    }



    @Override
    protected void onResume() {
        super.onResume();
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stepSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void startStepCounting() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        if (stepSensor == null) {
            Toast.makeText(this, R.string.demo_fitness_hint, Toast.LENGTH_LONG).show();
            if (demoFitnessButton != null) {
                demoFitnessButton.setVisibility(View.VISIBLE);
            }
            return;
        }

        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (initialSteps < 0) {
            initialSteps = (int) event.values[0];
        }

        int currentSteps = (int) event.values[0] - initialSteps;
        float distance = currentSteps * 0.7f;

        // Update UI
        runOnUiThread(() -> {
            stepsTextView.setText("Steps: " + currentSteps);
            distanceTextView.setText(String.format("Distance: %.2f km", distance / 1000));
            updateStepGoalProgress(currentSteps);
        });

        // Save data periodically instead of every step
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSaveTime > SAVE_INTERVAL) {
            saveFitnessData(currentSteps, distance);
            lastSaveTime = currentTime;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }


    private void saveFitnessData(int steps, float distance) {
        FitnessRecord item = new FitnessRecord();
        item.setUserId(AWSMobileClient.getInstance().getUsername());
        item.setTimestamp(System.currentTimeMillis());
        item.setSteps(steps);
        item.setDistance(distance);

        new Thread(() -> {
            try {
                dynamoDBMapper.save(item);
                Log.d("STEP_COUNT", "Saved: Steps=" + steps + ", Distance=" + distance);
            } catch (Exception e) {
                Log.e("STEP_COUNT", "Error saving data: " + e.getMessage());
            }
        }).start();
    }
    private void initializeViews() {
        stepsTextView = findViewById(R.id.steps_text_view);
        distanceTextView = findViewById(R.id.distance_text_view);
        lastUpdatedText = findViewById(R.id.last_updated_text);
        stepGoalProgressText = findViewById(R.id.step_goal_progress_text);
        stepGoalStatusText = findViewById(R.id.step_goal_status_text);
        stepGoalProgressBar = findViewById(R.id.step_goal_progress_bar);
        demoFitnessButton = findViewById(R.id.btn_demo_fitness);
        fitnessChart = findViewById(R.id.fitness_chart);

        updateStepGoalProgress(0);

        if (demoFitnessButton != null) {
            demoFitnessButton.setOnClickListener(v -> populateDemoFitnessData());
        }

        // Configure basic chart settings
        setupChart();
    }

    /**
     * Saves realistic sample fitness points to DynamoDB so charts work on emulators
     * without a physical step sensor — useful for demos and testing.
     */
    private void populateDemoFitnessData() {
        if (dynamoDBMapper == null) {
            Toast.makeText(this, "Sign in first", Toast.LENGTH_SHORT).show();
            return;
        }

        demoFitnessButton.setEnabled(false);
        lastUpdatedText.setText("Generating demo data...");

        new Thread(() -> {
            try {
                String userId = AWSMobileClient.getInstance().getUsername();
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                for (int hour = 6; hour <= 20; hour += 2) {
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    int steps = 400 + (hour - 6) * 650;
                    float distance = steps * 0.7f;

                    FitnessRecord item = new FitnessRecord();
                    item.setUserId(userId);
                    item.setTimestamp(cal.getTimeInMillis());
                    item.setSteps(steps);
                    item.setDistance(distance);
                    dynamoDBMapper.save(item);
                }

                runOnUiThread(() -> {
                    demoFitnessButton.setEnabled(true);
                    Toast.makeText(this, R.string.demo_fitness_saved, Toast.LENGTH_LONG).show();
                    AchievementManager.markDemoFitnessLoaded(this);
                    AchievementManager.syncAndNotify(this);
                    fetchFitnessData();
                });
            } catch (Exception e) {
                Log.e(TAG, "Demo fitness error", e);
                runOnUiThread(() -> {
                    demoFitnessButton.setEnabled(true);
                    lastUpdatedText.setText("Demo data failed: " + e.getMessage());
                    Toast.makeText(this, "Demo error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }


    private void setupChart() {
        fitnessChart.setTouchEnabled(true);
        fitnessChart.setDragEnabled(true);
        fitnessChart.setScaleEnabled(true);
        fitnessChart.setPinchZoom(false);
        fitnessChart.setDrawGridBackground(false);  // Remove grid background
        fitnessChart.setBackgroundColor(getResources().getColor(R.color.bg_surface_elevated));
        fitnessChart.getDescription().setEnabled(false);

        // Remove right border
        fitnessChart.setDrawBorders(false);

        // Add padding
        fitnessChart.setExtraLeftOffset(15f);
        fitnessChart.setExtraRightOffset(15f);
    }

    private void startFitnessService() {
        Intent serviceIntent = new Intent(this, FitnessUploadService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void initializeAWS() {
        AWSMobileClient.getInstance().initialize(getApplicationContext(), new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails userStateDetails) {
                switch (userStateDetails.getUserState()) {
                    case SIGNED_IN:
                        runOnUiThread(() -> onSignedIn());
                        Log.d("USER_ID", "Current user ID: " + AWSMobileClient.getInstance().getUsername());
                        break;
                    case SIGNED_OUT:
                        runOnUiThread(FitnessActivity.this::showSignIn);
                        break;
                    default:
                        AWSMobileClient.getInstance().signOut();
                        break;
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "AWS initialization error", e);
            }
        });
    }

    private void onSignedIn() {
        String cognitoUserId = AWSMobileClient.getInstance().getUsername();
        Log.d("COGNITO_USER", "Cognito User ID: " + cognitoUserId);
        setupDynamoDB();
        fetchFitnessData();
    }

    private void showSignIn() {
        AWSMobileClient.getInstance().showSignIn(this, new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails result) {
                onSignedIn();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Sign-in error", e);
            }
        });
    }

    private void setupDynamoDB() {
        final AmazonDynamoDBClient dynamoDBClient = new AmazonDynamoDBClient(AWSMobileClient.getInstance());
        dynamoDBClient.setRegion(com.amazonaws.regions.Region.getRegion(Regions.EU_NORTH_1)); // Set correct region

        this.dynamoDBMapper = DynamoDBMapper.builder()
                .dynamoDBClient(dynamoDBClient)
                .build();
    }

    private void fetchFitnessData() {
        String userId = AWSMobileClient.getInstance().getUsername();
        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        Log.d("FETCH_DEBUG", "User ID: " + userId);
        Log.d("FETCH_DEBUG", "Start Time: " + new Date(startTime));
        Log.d("FETCH_DEBUG", "End Time: " + new Date(endTime));

        // Create a FitnessRecord to use as the hash key
        FitnessRecord hashKey = new FitnessRecord();
        hashKey.setUserId(userId);

        DynamoDBQueryExpression<FitnessRecord> queryExpression = new DynamoDBQueryExpression<FitnessRecord>()
                .withHashKeyValues(hashKey)
                .withRangeKeyCondition("timestamp",
                        new Condition()
                                .withComparisonOperator(ComparisonOperator.BETWEEN)
                                .withAttributeValueList(
                                        new AttributeValue().withN(String.valueOf(startTime)),
                                        new AttributeValue().withN(String.valueOf(endTime))
                                ));

        new Thread(() -> {
            try {
                List<FitnessRecord> results = dynamoDBMapper.query(FitnessRecord.class, queryExpression);
                Log.d("FETCH_DEBUG", "Query returned " + results.size() + " items");
                if (!results.isEmpty()) {
                    Log.d("FETCH_DEBUG", "First item: Steps=" + results.get(0).getSteps() +
                            " Distance=" + results.get(0).getDistance());
                }
                processResults(results);
            } catch (Exception e) {
                Log.e("FETCH_DEBUG", "Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }



    private void processResults(List<FitnessRecord> results) {
        Log.d("PROCESS_DEBUG", "Processing " + results.size() + " results");

        if (results.isEmpty()) {
            Log.d("PROCESS_DEBUG", "No data to display");
            runOnUiThread(() -> {
                stepsTextView.setText("Steps: 0");
                distanceTextView.setText("Distance: 0.00 km");
                lastUpdatedText.setText("No data available");
                fitnessChart.clear();
                fitnessChart.invalidate();
                updateStepGoalProgress(0);
                if (demoFitnessButton != null && stepSensor == null) {
                    demoFitnessButton.setVisibility(View.VISIBLE);
                }
            });
            return;
        }

        List<Entry> stepEntries = new ArrayList<>();
        List<Entry> distanceEntries = new ArrayList<>();

        // Convert results to ArrayList to ensure it's a modifiable list
        List<FitnessRecord> sortedResults = new ArrayList<>(results);
        Collections.sort(sortedResults, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        int totalSteps = 0;
        float totalDistance = 0;

        for (FitnessRecord item : sortedResults) {
            totalSteps += item.getSteps();
            totalDistance += item.getDistance();

            // Get hour of day for x-axis
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(item.getTimestamp());
            float hourOfDay = cal.get(Calendar.HOUR_OF_DAY);

            Log.d("PROCESS_DEBUG", String.format("Adding point: Hour=%.1f, Steps=%d, Distance=%.2f",
                    hourOfDay, item.getSteps(), item.getDistance()));

            stepEntries.add(new Entry(hourOfDay, item.getSteps()));
            distanceEntries.add(new Entry(hourOfDay, item.getDistance()));
        }

        final int finalTotalSteps = totalSteps;
        final float finalTotalDistance = totalDistance;

        runOnUiThread(() -> {
            try {
                updateUI(finalTotalSteps, finalTotalDistance, stepEntries, distanceEntries);
                Log.d("UI_DEBUG", "Updated UI with " + stepEntries.size() + " data points");
            } catch (Exception e) {
                Log.e("UI_DEBUG", "Error updating UI: " + e.getMessage());
            }
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_ACTIVITY_RECOGNITION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startStepCounting();
            } else {
                Toast.makeText(this, "Permission required for step counting", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void resetStepCounterAtMidnight() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        Handler handler = new Handler();
        handler.postDelayed(() -> {
            initialSteps = -1;  // Reset step counter
            resetStepCounterAtMidnight();  // Schedule next reset
        }, calendar.getTimeInMillis() - System.currentTimeMillis());
    }




    private void updateUI(final int steps, final float distance,
                          final List<Entry> stepEntries, final List<Entry> distanceEntries) {
        // Update TextViews
        stepsTextView.setText(String.format("Steps: %d", steps));
        distanceTextView.setText(String.format("Distance: %.2f km", distance / 1000));
        updateStepGoalProgress(steps);
        lastUpdatedText.setText("Last updated: " + new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new Date()));

        // Configure chart
        fitnessChart.setTouchEnabled(true);
        fitnessChart.setDragEnabled(true);
        fitnessChart.setScaleEnabled(true);
        fitnessChart.setPinchZoom(false);
        fitnessChart.setDrawGridBackground(false);
        fitnessChart.setBackgroundColor(getResources().getColor(R.color.bg_surface_elevated));

        // Add padding
        fitnessChart.setExtraOffsets(10, 10, 10, 10);


        // X-Axis styling
        XAxis xAxis = fitnessChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.parseColor("#E0E0E0"));  // Light gray grid
        xAxis.setAxisLineColor(Color.parseColor("#CCCCCC"));
        xAxis.setTextColor(Color.parseColor("#666666"));
        xAxis.setTextSize(10f);
        xAxis.setGranularity(3f);  // Show every 3 hours
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%02d:00", (int)value);
            }
        });

        // Y-Axis styling
        YAxis leftAxis = fitnessChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#E0E0E0"));
        leftAxis.setAxisLineColor(Color.parseColor("#CCCCCC"));
        leftAxis.setTextColor(Color.parseColor("#666666"));
        leftAxis.setTextSize(10f);
        leftAxis.setAxisMinimum(0f);

        // Disable right axis
        fitnessChart.getAxisRight().setEnabled(false);

        // Create datasets with improved styling
        LineDataSet stepsDataSet = new LineDataSet(stepEntries, "Steps");
        stepsDataSet.setColor(getResources().getColor(R.color.purple));
        stepsDataSet.setLineWidth(2.5f);
        stepsDataSet.setDrawCircles(true);
        stepsDataSet.setCircleColor(getResources().getColor(R.color.purple));
        stepsDataSet.setCircleRadius(4f);
        stepsDataSet.setDrawCircleHole(true);
        stepsDataSet.setCircleHoleRadius(2f);
        stepsDataSet.setDrawFilled(true);
        stepsDataSet.setFillDrawable(gradientDrawable(R.color.purple));
        stepsDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        stepsDataSet.setCubicIntensity(0.15f);
        stepsDataSet.setDrawValues(false);

        LineDataSet distanceDataSet = new LineDataSet(distanceEntries, "Distance (km)");
        distanceDataSet.setColor(getResources().getColor(R.color.profilePrimaryDark));
        distanceDataSet.setLineWidth(2.5f);
        distanceDataSet.setDrawCircles(true);
        distanceDataSet.setCircleColor(getResources().getColor(R.color.profilePrimaryDark));
        distanceDataSet.setCircleRadius(4f);
        distanceDataSet.setDrawCircleHole(true);
        distanceDataSet.setCircleHoleRadius(2f);
        distanceDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        distanceDataSet.setCubicIntensity(0.15f);
        distanceDataSet.setDrawValues(false);

        LineData lineData = new LineData(stepsDataSet, distanceDataSet);
        fitnessChart.setData(lineData);

        // Improve legend appearance
        Legend legend = fitnessChart.getLegend();
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setTextSize(12f);
        legend.setTextColor(Color.parseColor("#666666"));
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);

        fitnessChart.animateX(1000);
        fitnessChart.invalidate();
    }

    private void updateStepGoalProgress(int currentSteps) {
        if (stepGoalProgressBar == null || stepGoalProgressText == null || stepGoalStatusText == null) {
            return;
        }

        int safeSteps = Math.max(0, currentSteps);
        int progressPercent = (int) Math.min(100, (safeSteps * 100L) / DAILY_STEP_GOAL);

        stepGoalProgressBar.setProgress(progressPercent);
        stepGoalProgressText.setText(
                getString(R.string.step_goal_progress, safeSteps, DAILY_STEP_GOAL));

        if (safeSteps >= DAILY_STEP_GOAL) {
            stepGoalStatusText.setText(R.string.step_goal_reached);
            stepGoalStatusText.setTextColor(getResources().getColor(R.color.purple));
        } else {
            int remaining = DAILY_STEP_GOAL - safeSteps;
            stepGoalStatusText.setText(getString(R.string.step_goal_remaining, remaining));
            stepGoalStatusText.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    // Add this method to create gradient background for the steps line
    private Drawable gradientDrawable(@ColorRes int colorRes) {
        int color = getResources().getColor(colorRes);
        int transparent = Color.TRANSPARENT;

        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        ColorUtils.setAlphaComponent(color, 100),
                        transparent
                });

        return gradient;
    }

    private void setupYAxes() {
        // Left Y-Axis (Steps)
        YAxis leftAxis = fitnessChart.getAxisLeft();
        leftAxis.setTextColor(getResources().getColor(R.color.purple));
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);
        leftAxis.setGridLineWidth(0.5f);
        leftAxis.setAxisLineWidth(1.5f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setSpaceTop(15f);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);

        // Right Y-Axis (Distance)
        YAxis rightAxis = fitnessChart.getAxisRight();
        rightAxis.setTextColor(getResources().getColor(R.color.profilePrimaryDark));
        rightAxis.setDrawGridLines(false);
        rightAxis.setAxisLineWidth(1.5f);
        rightAxis.setAxisMinimum(0f);
        rightAxis.setSpaceTop(15f);
    }


    private LineDataSet createStepsDataSet(List<Entry> entries) {
        LineDataSet dataSet = new LineDataSet(entries, "Steps");

        // Make line smoother
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.1f);  // Lower value = smoother curve

        // Style improvements
        dataSet.setColor(getResources().getColor(R.color.purple));
        dataSet.setLineWidth(3f);

        // Point styling
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(getResources().getColor(R.color.purple));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleRadius(2f);

        // Fill area under the line
        dataSet.setDrawFilled(true);
        dataSet.setFillAlpha(50);
        dataSet.setFillColor(getResources().getColor(R.color.purple));

        // Value text styling
        dataSet.setDrawValues(false);

        return dataSet;
    }


    private LineDataSet createDistanceDataSet(List<Entry> entries) {
        LineDataSet dataSet = new LineDataSet(entries, "Distance (km)");

        // Make line smoother
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.1f);

        // Style improvements
        dataSet.setColor(getResources().getColor(R.color.profilePrimaryDark));
        dataSet.setLineWidth(3f);

        // Point styling
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(getResources().getColor(R.color.profilePrimaryDark));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleRadius(2f);

        dataSet.setDrawValues(false);

        return dataSet;
    }
}
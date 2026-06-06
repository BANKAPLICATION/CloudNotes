package com.cloudnotes.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize and check authentication state
        AWSMobileClient.getInstance().initialize(this, new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails userStateDetails) {
                Log.d(TAG, "Authentication state: " + userStateDetails.getUserState());

                Intent intent;
                switch (userStateDetails.getUserState()) {
                    case SIGNED_IN:
                        intent = new Intent(SplashActivity.this, MainActivity.class);
                        break;
                    case SIGNED_OUT_USER_POOLS_TOKENS_INVALID:
                    case SIGNED_OUT:
                    default:
                        intent = new Intent(SplashActivity.this, LoginActivity.class);
                        break;
                }

                startActivity(intent);
                finish();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Initialization error", e);
                // Fallback to login screen on initialization failure
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                finish();
            }
        });
    }
}
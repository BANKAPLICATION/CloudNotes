package com.cloudnotes.app.activities;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.cloudnotes.app.R;

import java.util.Map;

public class ChangePasswordActivity extends AppCompatActivity {
    private static final String TAG = "ChangePasswordActivity";
    private EditText EDT_currentPassword, EDT_newPassword, EDT_ConfirmNewPassword;
    private Button BTN_ChangePassword;
    private String userEmail;
    ImageButton IB_cngPwdBackButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        initializeViews();
        setupChangePasswordButton();
        getCurrentUserEmail();

        IB_cngPwdBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    private void initializeViews() {
        EDT_currentPassword = findViewById(R.id.EDT_currentPassword);
        EDT_newPassword = findViewById(R.id.EDT_newPassword);
        EDT_ConfirmNewPassword = findViewById(R.id.EDT_ConfirmNewPassword);
        BTN_ChangePassword = findViewById(R.id.BTN_changePassword);
        IB_cngPwdBackButton = findViewById(R.id.IB_cngPwdBackButton);
    }

    private void getCurrentUserEmail() {
        try {
            AWSMobileClient.getInstance().getUserAttributes(new Callback<Map<String, String>>() {
                @Override
                public void onResult(Map<String, String> userAttributes) {
                    userEmail = userAttributes.get("email");
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error getting user attributes", e);
                    showToast("Error getting user details");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error getting user session", e);
            showToast("Error getting user session");
        }
    }

    private void setupChangePasswordButton() {
        BTN_ChangePassword.setOnClickListener(v -> validateAndChangePassword());
    }

    private void validateAndChangePassword() {
        String currentPassword = EDT_currentPassword.getText().toString().trim();
        String newPassword = EDT_newPassword.getText().toString().trim();
        String confirmNewPassword = EDT_ConfirmNewPassword.getText().toString().trim();

        // Validation
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmNewPassword.isEmpty()) {
            showToast("Please fill all fields");
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            showToast("New passwords don't match");
            return;
        }

        // Password strength validation
       /* if (!isPasswordStrong(newPassword)) {
            showToast("Password must be at least 8 characters long and contain uppercase, lowercase, number and special character");
            return;
        }*/

        // Show progress
        showLoading();

        try {
            AWSMobileClient.getInstance().changePassword(currentPassword, newPassword, new Callback<Void>() {
                @Override
                public void onResult(Void result) {
                    runOnUiThread(() -> {
                        hideLoading();
                        showToast("Password changed successfully");
                        finish();
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        hideLoading();
                        Log.e(TAG, "Change password error", e);
                        if (e.getMessage().contains("Incorrect username or password")) {
                            showToast("Current password is incorrect");
                        } else {
                            showToast("Error: " + e.getMessage());
                        }
                    });
                }
            });
        } catch (Exception e) {
            hideLoading();
            Log.e(TAG, "Error initiating password change", e);
            showToast("Error initiating password change");
        }
    }

    private boolean isPasswordStrong(String password) {
        // Password must be at least 8 characters long and contain:
        // - At least one uppercase letter
        // - At least one lowercase letter
        // - At least one number
        // - At least one special character
        String passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";
        return password.matches(passwordPattern);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private ProgressDialog progressDialog;

    private void showLoading() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Please wait...");
            progressDialog.setCancelable(false);
        }
        progressDialog.show();
    }

    private void hideLoading() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
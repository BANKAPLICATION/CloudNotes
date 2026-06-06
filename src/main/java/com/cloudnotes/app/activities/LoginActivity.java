package com.cloudnotes.app.activities;

import static android.content.ContentValues.TAG;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobile.client.results.SignInResult;
import com.amazonaws.mobile.client.results.SignInState;
import com.amazonaws.mobile.client.results.SignUpResult;
import com.cloudnotes.app.R;

public class LoginActivity extends AppCompatActivity {

    private EditText EDT_usernameField, EDT_login_passwordField;
    private Button BTN_loginButton;
    TextView TV_signupLink, TV_forgot_password;

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "=== LoginActivity onCreate ===");

        // Initialize AWS Mobile Client safely
        initializeAWSMobileClientSafely();

        // Initialize views
        EDT_usernameField = findViewById(R.id.EDT_usernameField);
        EDT_login_passwordField = findViewById(R.id.EDT_login_passwordField);
        BTN_loginButton = findViewById(R.id.BTN_loginButton);
        TV_signupLink = findViewById(R.id.TV_signupLink);
        TV_forgot_password = findViewById(R.id.TV_forgot_password);

        // Set up login button
        BTN_loginButton.setOnClickListener(v -> {
            BTN_loginButton.setEnabled(false);
            String email = EDT_usernameField.getText().toString().trim();
            String password = EDT_login_passwordField.getText().toString().trim();

            if (validateInputs(email, password)) {
                loginUser(email, password);
            } else {
                BTN_loginButton.setEnabled(true);
            }
        });

        // Set up other click listeners
        TV_signupLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        TV_forgot_password.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
        });
    }

    private void initializeAWSMobileClientSafely() {
        try {
            AWSMobileClient.getInstance().initialize(getApplicationContext(), new Callback<UserStateDetails>() {
                @Override
                public void onResult(UserStateDetails userStateDetails) {
                    Log.d(TAG, "AWSMobileClient initialized successfully");
                    Log.d(TAG, "User state: " + userStateDetails.getUserState());
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Initialization error", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AWS Mobile Client", e);
            Toast.makeText(this, "Authentication service initialization failed", Toast.LENGTH_LONG).show();
        }
    }

    private boolean validateInputs(String email, String password) {
        if (email.isEmpty()) {
            EDT_usernameField.setError("Email cannot be empty");
            return false;
        }
        if (password.isEmpty()) {
            EDT_login_passwordField.setError("Password cannot be empty");
            return false;
        }
        return true;
    }

    private void loginUser(String email, String password) {
        AWSMobileClient.getInstance().signIn(email, password, null, new Callback<SignInResult>() {
            @Override
            public void onResult(SignInResult signInResult) {
                runOnUiThread(() -> {
                    BTN_loginButton.setEnabled(true);
                    if (signInResult.getSignInState() == SignInState.DONE) {
                        handleSuccessfulLogin(email);
                    } else {
                        Toast.makeText(LoginActivity.this,
                                "Login failed: Invalid credentials",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    BTN_loginButton.setEnabled(true);
                    Log.e(TAG, "Login error", e);
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";

                    if (errorMessage.contains("User is not confirmed")) {
                        showConfirmationDialog(email);
                    } else {
                        Toast.makeText(LoginActivity.this,
                                "Login error: " + errorMessage,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showConfirmationDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Email")
                .setMessage("Your account is not confirmed. Would you like to resend the confirmation email?")
                .setPositiveButton("Resend OTP", (dialog, which) -> resendConfirmationOTP(email))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void resendConfirmationOTP(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Verify Email");
        builder.setMessage("A verification code will be sent to: " + email + "\nDo you want to proceed?");

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            sendVerificationCode(email);
            showOTPInputDialog(email);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void sendVerificationCode(String email) {
        AWSMobileClient.getInstance().resendSignUp(email, new Callback<SignUpResult>() {
            @Override
            public void onResult(SignUpResult signUpResult) {
                Log.i(TAG, "Verification code sent via: " +
                        signUpResult.getUserCodeDeliveryDetails().getDeliveryMedium() +
                        " at " +
                        signUpResult.getUserCodeDeliveryDetails().getDestination());
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Resend error", e);
            }
        });
    }

    private void showOTPInputDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter OTP");

        final EditText inputOTP = new EditText(this);
        inputOTP.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(inputOTP);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            String otpInput = inputOTP.getText().toString().trim();
            if (!otpInput.isEmpty()) {
                showLoadingDialog("Verifying OTP...");
                confirmUser(email, otpInput);
            } else {
                Toast.makeText(this, "Please enter OTP", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void confirmUser(String email, String otp) {
        AWSMobileClient.getInstance().confirmSignUp(email, otp, new Callback<SignUpResult>() {
            @Override
            public void onResult(final SignUpResult signUpResult) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Sign-up confirmation state: " + signUpResult.getConfirmationState());
                    dismissLoadingDialog();

                    if (signUpResult.getConfirmationState()) {
                        Toast.makeText(LoginActivity.this, "Email verified successfully!", Toast.LENGTH_SHORT).show();
                        handleSuccessfulLogin(email);
                    } else {
                        Toast.makeText(LoginActivity.this, "Verification pending", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Confirm sign-up error", e);
                    dismissLoadingDialog();
                    Toast.makeText(LoginActivity.this, "Verification error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void handleSuccessfulLogin(String email) {
        dismissLoadingDialog();
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoadingDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    private void dismissLoadingDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
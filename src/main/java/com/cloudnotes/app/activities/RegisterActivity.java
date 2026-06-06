package com.cloudnotes.app.activities;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobile.client.results.SignUpResult;
import com.amazonaws.mobile.client.results.UserCodeDeliveryDetails;
import com.amazonaws.services.cognitoidentityprovider.model.CodeMismatchException;
import com.amazonaws.services.cognitoidentityprovider.model.InvalidPasswordException;
import com.amazonaws.services.cognitoidentityprovider.model.LimitExceededException;
import com.amazonaws.services.cognitoidentityprovider.model.UsernameExistsException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.cloudnotes.app.R;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "RegisterActivity";
    private boolean isAwsInitialized = false;
    private ProgressDialog progressDialog;
    private EditText EDT_fullName, EDT_emailField, EDT_passwordField, EDT_confirmPasswordField, EDT_emailOTP;
    private Button BTN_signUp, BTN_Confirm_SignUp;
    private TextView TV_signinLink, TV_resendOtp;
    private LinearLayout LL_otpLayout;
    private String email, password, confirmPassword, name;
    private Map<String, String> attributes = new HashMap<>();
    private AmazonS3Client s3Client;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize SharedPreferences


        initializeViews();
        setupClickListeners();
        checkPermissions();
        initializeAWSMobileClient();

        // Set default profile picture
        //IV_profilePicture.setImageResource(R.drawable.demo_profile_3);
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    123);
        }
    }

    private void initializeViews() {

        EDT_fullName = findViewById(R.id.EDT_fullName);
        EDT_emailField = findViewById(R.id.EDT_emailField);
        BTN_signUp = findViewById(R.id.BTN_signUp);
        EDT_passwordField = findViewById(R.id.EDT_passwordField);
        EDT_confirmPasswordField = findViewById(R.id.EDT_confirmPasswordField);
        EDT_emailOTP = findViewById(R.id.EDT_emailOTP);
        BTN_Confirm_SignUp = findViewById(R.id.BTN_Confirm_SignUp);
        TV_signinLink = findViewById(R.id.TV_signinLink);
        TV_resendOtp = findViewById(R.id.TV_resendOtp);
        LL_otpLayout = findViewById(R.id.LL_otpLayout);
    }


    private void setupClickListeners() {
        BTN_signUp.setOnClickListener(v -> validateAndSendOtp());
        BTN_Confirm_SignUp.setOnClickListener(v -> validateAndRegister());
        TV_resendOtp.setOnClickListener(v -> resendOtp());
        TV_signinLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void validateAndSendOtp() {
        email = EDT_emailField.getText().toString().trim();
        name = EDT_fullName.getText().toString().trim();

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Please enter a valid email address");
            return;
        }

        if (name.isEmpty()) {
            showToast("Please enter your full name");
            return;
        }

        sendOtp();
    }

    private void initializeAWSMobileClient() {
        AWSMobileClient.getInstance().initialize(getApplicationContext(), new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails userStateDetails) {
                isAwsInitialized = true;
                Log.d(TAG, "AWSMobileClient initialized, state: " + userStateDetails.getUserState());
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "AWSMobileClient initialization failed", e);
                runOnUiThread(() -> showToast(
                        "AWS not configured. Check res/raw/awsconfiguration.json"));
            }
        });
    }

    private void sendOtp() {
        email = EDT_emailField.getText().toString().trim();
        name = EDT_fullName.getText().toString().trim();
        password = EDT_passwordField.getText().toString().trim();
        confirmPassword = EDT_confirmPasswordField.getText().toString().trim();

        // Validate inputs
        if (!validateInputs()) {
            return;
        }

        if (!isAwsInitialized) {
            showToast("Connecting to AWS, please try again in a moment...");
            initializeAWSMobileClient();
            return;
        }

        showProgressDialog("Sending OTP...");

        // Set up attributes for Cognito
        attributes = new HashMap<>();
        attributes.put("name", name);
        // Use Cognito's signUp which will automatically send OTP
        AWSMobileClient.getInstance().signUp(email, password, attributes, null, new Callback<SignUpResult>() {
                    @Override
                    public void onResult(SignUpResult result) {
                        hideProgressDialog();
                        runOnUiThread(() -> {
                            UserCodeDeliveryDetails details = result.getUserCodeDeliveryDetails();
                            if (details != null) {
                                Log.i(TAG, "OTP delivery: medium=" + details.getDeliveryMedium()
                                        + ", destination=" + details.getDestination());
                                showToast("Code sent to " + maskEmail(details.getDestination())
                                        + ". Check inbox and spam.");
                            } else {
                                showToast("Account created. Check your email for the code.");
                            }
                            showOtpStep();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        hideProgressDialog();
                        Log.e(TAG, "SignUp error", e);
                        runOnUiThread(() -> {
                            if (e instanceof UsernameExistsException) {
                                showToast("Email already registered. Resending confirmation code...");
                                resendOtp();
                            } else if (e instanceof InvalidPasswordException) {
                                showToast("Password too weak. Use 8+ chars with upper, lower and a number.");
                            } else if (e instanceof LimitExceededException) {
                                showToast("Too many attempts. Wait a few minutes and try again.");
                            } else if (e instanceof NullPointerException) {
                                showToast("AWS Cognito not configured. Rebuild the app after adding awsconfiguration.json");
                            } else {
                                showToast("Error: " + e.getMessage());
                            }
                        });
                    }
                });
    }

    private void showOtpStep() {
        LL_otpLayout.setVisibility(View.VISIBLE);
        BTN_signUp.setVisibility(View.GONE);
        BTN_Confirm_SignUp.setVisibility(View.VISIBLE);
        TV_resendOtp.setVisibility(View.VISIBLE);
    }

    private void resendOtp() {
        email = EDT_emailField.getText().toString().trim();
        if (email.isEmpty()) {
            showToast("Enter your email first");
            return;
        }
        if (!isAwsInitialized) {
            showToast("Connecting to AWS, please try again in a moment...");
            return;
        }

        showProgressDialog("Resending code...");
        AWSMobileClient.getInstance().resendSignUp(email, new Callback<SignUpResult>() {
            @Override
            public void onResult(SignUpResult signUpResult) {
                hideProgressDialog();
                runOnUiThread(() -> {
                    UserCodeDeliveryDetails details = signUpResult.getUserCodeDeliveryDetails();
                    if (details != null) {
                        Log.i(TAG, "Resent OTP: medium=" + details.getDeliveryMedium()
                                + ", destination=" + details.getDestination());
                        showToast("Code resent to " + maskEmail(details.getDestination())
                                + ". Check spam folder too.");
                    } else {
                        showToast("Confirmation code resent. Check your email.");
                    }
                    showOtpStep();
                });
            }

            @Override
            public void onError(Exception e) {
                hideProgressDialog();
                Log.e(TAG, "Resend OTP error", e);
                runOnUiThread(() -> showToast("Could not resend code: " + e.getMessage()));
            }
        });
    }

    private String maskEmail(String destination) {
        if (destination == null || !destination.contains("@")) {
            return destination != null ? destination : "your email";
        }
        int at = destination.indexOf('@');
        if (at <= 2) {
            return destination;
        }
        return destination.substring(0, 2) + "***" + destination.substring(at);
    }
    private void validateAndRegister() {
        password = EDT_passwordField.getText().toString().trim();
        confirmPassword = EDT_confirmPasswordField.getText().toString().trim();
        String otp = EDT_emailOTP.getText().toString().trim();

       /* if (!isPasswordValid(password)) {
            showToast("Password must be at least 8 characters long and contain letters and numbers");
            return;
        }*/

        if (!password.equals(confirmPassword)) {
            showToast("Passwords do not match");
            return;
        }

        if (otp.isEmpty()) {
            showToast("Please enter the OTP");
            return;
        }
        showProgressDialog("Verifying OTP...");
        //BTN_signUp.setEnabled(false); // Disable button to prevent multiple clicks
        registerUser();
    }

    /*private boolean isPasswordValid(String password) {
        return password.length() >= 8 &&
                password.matches(".*[A-Za-z].*") &&
                password.matches(".*[0-9].*");
    }*/


    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }


    private void registerUser() {

        if (!isNetworkAvailable()) {
            hideProgressDialog();
            showToast("No internet connection");

            return;
        }

        showProgressDialog("Registering user...");

        // Add a timeout for the entire registration process
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                hideProgressDialog();
                //BTN_signUp.setEnabled(true);
                showToast("Registration timed out - please try again");
            }
        }, 120000); // 2 minute timeout


        String enteredOTP = EDT_emailOTP.getText().toString().trim();

        if (enteredOTP.isEmpty()) {
            showToast("Please enter the OTP");
            return;
        }


        // Confirm signup with Cognito using the entered OTP
        AWSMobileClient.getInstance().confirmSignUp(email, enteredOTP, new Callback<SignUpResult>() {
                    @Override
                    public void onResult(SignUpResult result) {
                        // After successful verification, upload profile picture
                        Log.d("AWS_Signup", "OTP verification successful");

                        //BTN_signUp.setEnabled(true);
                        showToast("Registration successful!");
                        startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                        finish();

                    }

                    @Override
                    public void onError(Exception e) {
                        //hideLoadingDialog();
                        Log.e("AWS", "Confirmation error", e);
                        runOnUiThread(() -> {
                            //BTN_signUp.setEnabled(true);
                            // Hide progress dialog/indicator
                            hideProgressDialog();
                            if (e instanceof CodeMismatchException) {
                                showToast("Invalid OTP. Please try again");
                            } else {
                                showToast("Verification failed: " + e.getMessage());
                            }
                        });
                    }
                }
        );
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private boolean validateInputs() {
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Please enter a valid email address");
            return false;
        }

        if (name.isEmpty()) {
            showToast("Please enter your full name");
            return false;
        }

        if (password.isEmpty() || password.length() < 8) {
            showToast("Password must be at least 8 characters");
            return false;
        }
        if (!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")
                || !password.matches(".*[0-9].*")) {
            showToast("Password needs upper, lower case and a number (Cognito rules)");
            return false;
        }

        if (!password.equals(confirmPassword)) {
            showToast("Passwords do not match");
            return false;
        }

        return true;
    }

    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

}



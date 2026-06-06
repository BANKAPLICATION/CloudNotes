package com.cloudnotes.app.activities;
import static android.content.ContentValues.TAG;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


import com.amazonaws.mobile.client.results.ForgotPasswordResult;
import com.amazonaws.mobile.client.results.ForgotPasswordState;
import com.cloudnotes.app.R;

import java.security.SecureRandom;

import java.util.Objects;

public class ForgotPasswordActivity extends AppCompatActivity {

    public interface UserExistenceCallback {
        void onResult(boolean userExists);
    }


    private EditText EDT_forgotPasswordEmailField, EDT_forgotPasswordField, EDT_forgotPassConfirmPasswordField, EDT_forgotPasswordOtpField ;
    private Button BTN_ContinueForgotPassword, BTN_update_password;
    private LinearLayout LL_forgotPasswordOtpField, LL_forgotPassNewPasswordField, LL_forgotPassConfirmPasswordField,LL_forgotPasswordEmailField;
    private ImageView IV_forgotPassBackArrow;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_forgot_password);
       /* ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.forgot_pass_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });*/

        initiializeViews();
        forgotPassword();


        // Set click listener
        IV_forgotPassBackArrow.setOnClickListener(view -> onBackPressed()); // Navigate back to the previous activity


    }

    private void forgotPassword() {
        BTN_ContinueForgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = EDT_forgotPasswordEmailField.getText().toString().trim();

                if (email.isEmpty()) {
                    EDT_forgotPasswordEmailField.setError("Email cannot be empty");
                    return;
                }
                LL_forgotPasswordEmailField.setVisibility(View.GONE);
                LL_forgotPasswordOtpField.setVisibility(View.VISIBLE);
                LL_forgotPassNewPasswordField.setVisibility(View.VISIBLE);
                LL_forgotPassConfirmPasswordField.setVisibility(View.VISIBLE);
                BTN_ContinueForgotPassword.setVisibility(View.GONE);
                BTN_update_password.setVisibility(View.VISIBLE);

                AWSMobileClient.getInstance().forgotPassword(email, new Callback<ForgotPasswordResult>() {
                    @Override
                    public void onResult(final ForgotPasswordResult result) {
                        runOnUiThread(() -> {
                            if (result.getState() == ForgotPasswordState.CONFIRMATION_CODE) {
                                Toast.makeText(ForgotPasswordActivity.this,
                                        "Confirmation code is sent to reset password",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ForgotPasswordActivity.this,
                                        "Error sending confirmation code. Please try again.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(final Exception e) {
                        runOnUiThread(() -> {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Error sending confirmation code. Please try again.",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });

                updatePassword();


            }
        });
    }

    private void updatePassword() {
        BTN_update_password.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = EDT_forgotPasswordEmailField.getText().toString().trim();
                String otp = EDT_forgotPasswordOtpField.getText().toString().trim();
                String password = EDT_forgotPasswordField.getText().toString().trim();
                String confirmPassword = EDT_forgotPassConfirmPasswordField.getText().toString().trim();

                // Validate inputs


                if (!validateInputs(email, otp, password, confirmPassword)) {
                    return;
                }

                AWSMobileClient.getInstance().confirmForgotPassword(email, password, otp, new Callback<ForgotPasswordResult>() {
                    @Override
                    public void onResult(final ForgotPasswordResult result) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "forgot password state: " + result.getState());
                                if (Objects.requireNonNull(result.getState()) == ForgotPasswordState.DONE) {
                                    Toast.makeText(ForgotPasswordActivity.this, "Password reset successfully! please login with new password", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    Log.e(TAG, "un-supported forgot password state");
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "forgot password error", e);
                    }
                });


            }
        });
    }


    public static String generateRandomPassword(int length) {
        // Characters allowed in the password
        String upperCaseLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCaseLetters = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specialCharacters = "!@#$%^&*()-_=+[]{}|;:',.<>?/`~";
        String allCharacters = upperCaseLetters + lowerCaseLetters + digits + specialCharacters;

        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        // Ensure the password contains at least one character from each category
        password.append(upperCaseLetters.charAt(random.nextInt(upperCaseLetters.length())));
        password.append(lowerCaseLetters.charAt(random.nextInt(lowerCaseLetters.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(specialCharacters.charAt(random.nextInt(specialCharacters.length())));

        // Fill the rest of the password with random characters from all categories
        for (int i = 4; i < length; i++) {
            password.append(allCharacters.charAt(random.nextInt(allCharacters.length())));
        }

        // Shuffle the password to ensure randomness
        return shuffleString(password.toString());
    }

    public static String shuffleString(String input) {
        char[] characters = input.toCharArray();
        SecureRandom random = new SecureRandom();
        for (int i = characters.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            char temp = characters[i];
            characters[i] = characters[index];
            characters[index] = temp;
        }
        return new String(characters);
    }

    private void initiializeViews() {
        EDT_forgotPasswordEmailField = findViewById(R.id.EDT_forgotPasswordEmailField);
        EDT_forgotPasswordField = findViewById(R.id.EDT_forgotPasswordField);
        EDT_forgotPassConfirmPasswordField = findViewById(R.id.EDT_forgotPassConfirmPasswordField);
        EDT_forgotPasswordOtpField = findViewById(R.id.EDT_forgotPasswordOtpField);

        BTN_ContinueForgotPassword = findViewById(R.id.BTN_ContinueForgotPassword);
        BTN_update_password = findViewById(R.id.BTN_update_password);

        LL_forgotPasswordEmailField = findViewById(R.id.LL_forgotPasswordEmailField);
        LL_forgotPasswordOtpField = findViewById(R.id.LL_forgotPasswordOtpField);
        LL_forgotPassNewPasswordField = findViewById(R.id.LL_forgotPassNewPasswordField);
        LL_forgotPassConfirmPasswordField = findViewById(R.id.LL_forgotPassConfirmPasswordField);

        IV_forgotPassBackArrow = findViewById(R.id.IV_forgotPassBackArrow);
    }

    private boolean validateInputs(String email,String otp, String password, String confirmPassword) {

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(ForgotPasswordActivity.this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (otp.isEmpty()) {
            Toast.makeText(ForgotPasswordActivity.this, "Please enter the OTP", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (password.isEmpty() || password.length() < 6) {
            Toast.makeText(ForgotPasswordActivity.this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

}
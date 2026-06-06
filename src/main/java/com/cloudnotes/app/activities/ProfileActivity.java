package com.cloudnotes.app.activities;

import static com.cloudnotes.app.config.CloudConfig.AWS_REGION;
import static com.cloudnotes.app.config.CloudConfig.S3_BUCKET_NAME;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amplifyframework.auth.AuthUserAttribute;
import com.amplifyframework.auth.AuthUserAttributeKey;
import com.amplifyframework.core.Amplify;
import com.cloudnotes.app.CloudNotesApplication;
import com.cloudnotes.app.R;
import com.cloudnotes.app.utility.AchievementManager;
import com.cloudnotes.app.utility.UserUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProfileActivity extends AppCompatActivity {


    //private TextView TV_fullName, TV_mobileNumber, TV_email, TV_address, TV_dob;
    TextView TV_userId, TV_userEmail, TV_date;
    private EditText EDT_fullName, EDT_mobileNumber, EDT_email, EDT_address, EDT_dob, EDT_userId, EDT_userEmail;
    Button BTN_editProfile;
    LinearLayout LL_backToHome;
    ImageView IV_editProfileImage,IV_change_profile;
    private boolean isEditing = false;
    private ProgressDialog progressDialog;
    private static final int PICK_IMAGE_REQUEST = 100;
    private Uri imageUri;
    private File imageFile;

    public static String image_url;
    ProgressBar progressBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_profile);
        try {
            initializeViews();

            // Verify views were initialized
            if (EDT_fullName == null || EDT_mobileNumber == null ||
                    EDT_email == null || EDT_address == null || EDT_dob == null) {
                Toast.makeText(this, "Error initializing views", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            if (!AWSMobileClient.getInstance().isSignedIn()) {
                Toast.makeText(this, "Please sign in first", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

        Toolbar toolbar = findViewById(R.id.profile_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        if (LL_backToHome != null) {
            LL_backToHome.setOnClickListener(v -> finish());
        }


        IV_editProfileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Toggle visibility
                if (IV_change_profile.getVisibility() == View.VISIBLE) {
                    IV_change_profile.setVisibility(View.GONE); // Hide if visible
                } else {
                    IV_change_profile.setVisibility(View.VISIBLE); // Show if hidden
                }
            }
        });
        IV_change_profile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImageChooser();
            }

        });


        BTN_editProfile.setOnClickListener(v -> {
            if (!isEditing) {
                enableEditing(true);
                BTN_editProfile.setText(R.string.profile_save);
            } else {
                saveProfileData();
                enableEditing(false);
                BTN_editProfile.setText(R.string.profile_edit);
            }
            isEditing = !isEditing;
        });

        showImageFromS3();

            // Fetch user details
            fetchUserDetails();

            // Rest of your onCreate code...
        } catch (Exception e) {
            Toast.makeText(this, "Error initializing profile: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }

    }

    private void openImageChooser() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            // User selected an image
            imageUri = data.getData();
            try {
                // Open InputStream safely
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    throw new IOException("Failed to open InputStream");
                }

                // Decode bitmap
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close(); // Close InputStream to avoid memory leaks

                if (bitmap == null) {
                    throw new IOException("Failed to decode bitmap");
                }

                // Display the image in ImageView


                // Delete the old image file if it exists
                if (imageFile != null && imageFile.exists()) {
                    boolean deleted = imageFile.delete();
                    if (!deleted) {
                        Log.e("ImageFile", "Old image file could not be deleted");
                    }
                }

                // Generate unique file name
                String userId = UUID.randomUUID().toString(); // Unique ID
                String fileName = userId + "_" + System.currentTimeMillis() + ".jpg";

                // Create file in cache directory
                imageFile = new File(getCacheDir(), fileName);
                Log.d("ImageURI", "Created Image File: " + imageFile.getAbsolutePath());

                // Save the bitmap to file
                FileOutputStream fos = new FileOutputStream(imageFile);
                boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.flush();
                fos.close();

                if (!compressed) {
                    throw new IOException("Failed to compress image");
                }

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("ImageLoadError", "Error loading image", e);
            }

            new AlertDialog.Builder(this)
                    .setTitle("Confirm Image")
                    .setMessage("Are you sure you want to upload this image?")
                    .setPositiveButton("Yes", (dialog, which) -> uploadImageToS3())
                    .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                    .show();

        } else {
            // User did not select an image
            Toast.makeText(this, "No image selected. Keeping the previous one.", Toast.LENGTH_SHORT).show();
        }


        // Upload the image to S3 (whether it's the selected image or the default image)

    }

    private void uploadImageToS3() {
        if (imageFile == null || !imageFile.exists()) {
            Toast.makeText(this, "Image file is missing!", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressDialog("Uploading Image...");

        try {
            // Generate the image key with user email for organization
            String email = EDT_email.getText().toString();
            String imageKey = "users/" + email + "/" + System.currentTimeMillis() + "_" + imageFile.getName();
            Log.d("S3Upload", "imageKey: " + imageKey);

            // Initialize S3 client with AWSMobileClient
            AmazonS3Client s3Client = new AmazonS3Client(AWSMobileClient.getInstance());
            s3Client.setRegion(Region.getRegion(Regions.fromName(AWS_REGION)));

            // Build TransferUtility
            TransferUtility transferUtility = TransferUtility.builder()
                    .context(getApplicationContext())
                    .s3Client(s3Client)
                    .build();

            // Start upload
            TransferObserver uploadObserver = transferUtility.upload(
                    S3_BUCKET_NAME,
                    imageKey,
                    imageFile
            );

            uploadObserver.setTransferListener(new TransferListener() {
                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (state == TransferState.COMPLETED) {
                        // Generate the S3 URL
                        image_url = "https://" + S3_BUCKET_NAME + ".s3." + AWS_REGION + ".amazonaws.com/" + imageKey;

                        // Update user attributes with new image URL
                        List<AuthUserAttribute> attributes = new ArrayList<>();
                        attributes.add(new AuthUserAttribute(AuthUserAttributeKey.picture(), image_url));

                        Amplify.Auth.updateUserAttributes(
                                attributes,
                                result -> runOnUiThread(() -> {
                                    hideProgressDialog();
                                    Toast.makeText(ProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                                    AchievementManager.markProfilePhotoUploaded(ProfileActivity.this);
                                    AchievementManager.syncAndNotify(ProfileActivity.this);
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        showImageFromS3();
                                    }, 1000);
                                }),
                                error -> runOnUiThread(() -> {
                                    hideProgressDialog();
                                    Toast.makeText(ProfileActivity.this, "Failed to update profile: " + error.getMessage(), Toast.LENGTH_LONG).show();
                                    Log.e("S3Upload", "Failed to update user attributes", error);
                                })
                        );
                    } else if (state == TransferState.FAILED) {
                        runOnUiThread(() -> {
                            hideProgressDialog();
                            Toast.makeText(ProfileActivity.this, "Upload failed!", Toast.LENGTH_SHORT).show();
                            Log.e("S3Upload", "Upload failed with state: " + state);
                        });
                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    int percentage = (int) (bytesCurrent * 100 / bytesTotal);
                    runOnUiThread(() -> updateProgressDialog(percentage));
                }

                @Override
                public void onError(int id, Exception ex) {
                    Log.e("S3Upload", "Error during upload", ex);
                    runOnUiThread(() -> {
                        hideProgressDialog();
                        Toast.makeText(ProfileActivity.this, "Upload error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });

        } catch (Exception e) {
            Log.e("S3Upload", "Error preparing upload", e);
            hideProgressDialog();
            Toast.makeText(this, "Error preparing upload: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private void showImageFromS3() {

        UserUtils.fetchUserDetails(this, new UserUtils.OnUserDetailsFetchedListener() {
            @Override
            public void onUserDetailsFetched(Map<String, String> attributes) {
                // Handle the fetched user details
                attributes.get("name");

                String imageUrl = attributes.get("picture");
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.VISIBLE); // Show ProgressBar before loading
                        Glide.with(ProfileActivity.this)
                                .load(imageUrl)
                                .circleCrop()
                                //.error(getDrawable(R.drawable.demo_profile_3))
                                //.placeholder(R.drawable.demo_profile_3) // Default image
                                .signature(new ObjectKey(System.currentTimeMillis()))
                                .listener(new RequestListener<Drawable>() {
                                    @Override
                                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                        progressBar.setVisibility(View.GONE); // Hide ProgressBar on error

                                        return false;
                                    }

                                    @Override
                                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                        progressBar.setVisibility(View.GONE);
                                        return false;
                                    }
                                })
                                .into(IV_editProfileImage);
                    });
                }

            }

            @Override
            public void onError(Exception exception) {
                // Handle the error
                exception.printStackTrace();
            }
        });
    }

    private void updateProgressDialog(int percentage) {
        if (progressDialog != null) {
            progressDialog.setProgress(percentage);
        }
    }
    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); // Change to spinner style
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    // Hide progress dialog
    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }


    private void enableEditing(boolean enable) {
        // Safely enable/disable EditText fields with null checks
        if (EDT_fullName != null) {
            EDT_fullName.setEnabled(enable);
            EDT_fullName.setFocusable(enable);
            EDT_fullName.setFocusableInTouchMode(enable);
            EDT_fullName.setClickable(enable);
        }

        if (EDT_mobileNumber != null) {
            EDT_mobileNumber.setEnabled(enable);
            EDT_mobileNumber.setFocusable(enable);
            EDT_mobileNumber.setFocusableInTouchMode(enable);
            EDT_mobileNumber.setClickable(enable);
        }

        if (EDT_address != null) {
            EDT_address.setEnabled(enable);
            EDT_address.setFocusable(enable);
            EDT_address.setFocusableInTouchMode(enable);
            EDT_address.setClickable(enable);
        }

        if (EDT_dob != null) {
            EDT_dob.setEnabled(enable);
            EDT_dob.setFocusable(enable);
            EDT_dob.setFocusableInTouchMode(enable);
            EDT_dob.setClickable(enable);
        }

        // Only request focus if EDT_fullName exists
        if (enable && EDT_fullName != null) {
            EDT_fullName.requestFocus();
            EDT_fullName.setSelection(EDT_fullName.getText().length());

            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(EDT_fullName, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void saveProfileData() {
        try {
            Log.d("ProfileActivity", "Starting profile update...");

            // Wait for Amplify initialization with more detailed logging
            Log.d("ProfileActivity", "Checking Amplify initialization...");
            if (!CloudNotesApplication.waitForAmplifyInit(10000)) {
                String errorMsg = "AWS services not initialized after timeout";
                Log.e("ProfileActivity", errorMsg);
                Toast.makeText(this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
                return;
            }

            Log.d("ProfileActivity", "Amplify initialization confirmed");
            showProgressDialog("Updating profile...");

            // Get updated values with additional null checks
            String name = EDT_fullName != null && EDT_fullName.getText() != null
                    ? EDT_fullName.getText().toString().trim()
                    : "";
            String mobile = EDT_mobileNumber != null && EDT_mobileNumber.getText() != null
                    ? EDT_mobileNumber.getText().toString().trim()
                    : "";
            String address = EDT_address != null && EDT_address.getText() != null
                    ? EDT_address.getText().toString().trim()
                    : "";
            String dob = EDT_dob != null && EDT_dob.getText() != null
                    ? EDT_dob.getText().toString().trim()
                    : "";

            Log.d("ProfileActivity", "Collected profile data - Name: " + name + ", Mobile: " + mobile);

            // Create a list of attributes to update
            List<AuthUserAttribute> attributes = new ArrayList<>();

            // Only add non-empty attributes
            if (!name.isEmpty()) {
                attributes.add(new AuthUserAttribute(AuthUserAttributeKey.name(), name));
            }
            if (!mobile.isEmpty()) {
                attributes.add(new AuthUserAttribute(AuthUserAttributeKey.phoneNumber(), mobile));
            }
            if (!address.isEmpty()) {
                attributes.add(new AuthUserAttribute(AuthUserAttributeKey.address(), address));
            }
            if (!dob.isEmpty()) {
                attributes.add(new AuthUserAttribute(AuthUserAttributeKey.birthdate(), dob));
            }

            // Only proceed if there are attributes to update
            if (!attributes.isEmpty()) {
                Log.d("ProfileActivity", "Attempting to update " + attributes.size() + " attributes");

                try {
                    Amplify.Auth.updateUserAttributes(
                            attributes,
                            result -> {
                                Log.d("ProfileActivity", "Update successful");
                                runOnUiThread(() -> {
                                    hideProgressDialog();
                                    Toast.makeText(ProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                                    AchievementManager.markProfileUpdated(ProfileActivity.this);
                                    AchievementManager.syncAndNotify(ProfileActivity.this);
                                    fetchUserDetails(); // Refresh the displayed data
                                });
                            },
                            error -> {
                                Log.e("ProfileActivity", "Update failed", error);
                                runOnUiThread(() -> {
                                    hideProgressDialog();
                                    String errorMessage = error.getMessage() != null ? error.getMessage() : "Unknown error occurred";
                                    Toast.makeText(ProfileActivity.this, "Update failed: " + errorMessage, Toast.LENGTH_LONG).show();
                                });
                            }
                    );
                } catch (Exception e) {
                    Log.e("ProfileActivity", "Exception during Amplify.Auth.updateUserAttributes", e);
                    throw e;
                }
            } else {
                Log.d("ProfileActivity", "No attributes to update");
                hideProgressDialog();
                Toast.makeText(this, "No changes to update", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("ProfileActivity", "Error updating profile", e);
            hideProgressDialog();
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
            Toast.makeText(this, "Error updating profile: " + errorMessage, Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void initializeViews() {
        // Initialize TextViews
        EDT_fullName = findViewById(R.id.EDT_fullName);
        EDT_mobileNumber = findViewById(R.id.EDT_mobileNumber);
        EDT_email = findViewById(R.id.EDT_email);
        EDT_address = findViewById(R.id.EDT_address);
        EDT_dob = findViewById(R.id.EDT_dob);
        TV_userId = findViewById(R.id.TV_userId);
        TV_userEmail = findViewById(R.id.TV_userEmail);

        LL_backToHome = findViewById(R.id.LL_backToHome);
        IV_editProfileImage = findViewById(R.id.IV_editProfileImage);
        IV_change_profile = findViewById(R.id.IV_change_profile);

        BTN_editProfile = findViewById(R.id.BTN_editProfile);
        progressBar = findViewById(R.id.progressBar);


    }

    private void fetchUserDetails() {
        UserUtils.fetchUserDetails(this, new UserUtils.OnUserDetailsFetchedListener() {
            @Override
            public void onUserDetailsFetched(Map<String, String> attributes) {
                EDT_fullName.setText(attributes.get("name"));
                EDT_mobileNumber.setText(attributes.get("phone_number"));
                EDT_email.setText(attributes.get("email"));
                EDT_address.setText(attributes.get("address"));
                EDT_dob.setText(attributes.get("birthdate"));
                TV_userId.setText(attributes.get("name"));
                TV_userEmail.setText(attributes.get("email"));


            }

            @Override
            public void onError(Exception exception) {
                // Handle error
            }
        });
    }


}

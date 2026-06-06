package com.cloudnotes.app.activities;

import static com.cloudnotes.app.config.CloudConfig.AWS_REGION;
import static com.cloudnotes.app.config.CloudConfig.S3_BUCKET_NAME;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.cloudnotes.app.config.CloudConfig;
import com.cloudnotes.app.R;
import com.cloudnotes.app.database.DynamoDBHelper;
import com.cloudnotes.app.entities.Note;
import com.cloudnotes.app.utility.AchievementManager;
import com.cloudnotes.app.utility.NoteTags;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.bumptech.glide.Glide;


public class CreateNoteActivity extends AppCompatActivity {

    private EditText inputNoteTitle,inputNoteSubtitle,inputNoteText;
    private TextView textDateTime;
    private View viewSubtitleIndicator;
    private String selectedNoteColor;
    private String selectedTag = "";
    private String selectedImagePath;
    private ImageView imageNote;
    private TextView textWebURL;
    private LinearLayout layoutWebURL;


    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;
    private static final int REQUEST_CODE_SELECT_IMAGE = 2;
    private AlertDialog dialogAddURL;
    private AlertDialog dialogDeleteNote;

    private Note alreadyAvailableNote;

    //////////// DDDDDDDDDDYYYYYYYYYYYYNNNNNNNNNNAAAAAAAAMMMMMMMOOOOOOOOOOOO

    // Previous variables remain the same
    private DynamoDBHelper dbHelper;
    private DynamoDBMapper dynamoDBMapper;


    //////////// DDDDDDDDDDYYYYYYYYYYYYNNNNNNNNNNAAAAAAAAMMMMMMMOOOOOOOOOOOO


    @Override
    protected void onCreate( Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_note);

        //////////// DDDDDDDDDDYYYYYYYYYYYYNNNNNNNNNNAAAAAAAAMMMMMMMOOOOOOOOOOOO

        setupDynamoDB();

        // Rest of your onCreate remains the same
        //////////// DDDDDDDDDDYYYYYYYYYYYYNNNNNNNNNNAAAAAAAAMMMMMMMOOOOOOOOOOOO

        ImageView imageBack = findViewById(R.id.imageBack);
        imageBack.setOnClickListener(v -> onBackPressed());

        inputNoteTitle = findViewById(R.id.inputNoteTitle);
        inputNoteSubtitle = findViewById(R.id.inputNoteSubtitle);
        inputNoteText = findViewById(R.id.inputNote);
        textDateTime = findViewById(R.id.textDateTime);
        viewSubtitleIndicator = findViewById(R.id.viewSubtitleIndicator);
        // adding Image
        imageNote = findViewById(R.id.imageNote);
        // adding URL
        textWebURL = findViewById(R.id.textWebURL);
        layoutWebURL = findViewById(R.id.layoutWebURL);


        textDateTime.setText(new SimpleDateFormat("EEEE,dd MMMM yyyy HH:mm a", Locale.getDefault()).format(new Date()));

        ImageView imageSave = findViewById(R.id.imageSave);
        imageSave.setOnClickListener(v -> saveNote());

        selectedNoteColor = "#333333";
        selectedImagePath = "";

        if(getIntent().getBooleanExtra("isViewOrUpdate",false)){
            alreadyAvailableNote = (Note)getIntent().getSerializableExtra("note");
            setViewOrUpdateNote();
        }

        findViewById(R.id.imageRemoveWebURL).setOnClickListener(v -> {
            textWebURL.setText(null);
            layoutWebURL.setVisibility(View.GONE);
        });

        findViewById(R.id.imageRemoveImage).setOnClickListener(v -> {
            imageNote.setImageBitmap(null);
            imageNote.setVisibility(View.GONE);
            findViewById(R.id.imageRemoveImage).setVisibility(View.GONE);
            selectedImagePath = "";

        });

        if(getIntent().getBooleanExtra("isFromQuickActions",false)){
            String type = getIntent().getStringExtra("quickActionType");
            if(type !=null){
                if(type.equals("image")){
                    selectedImagePath = getIntent().getStringExtra("imagePath");
                    imageNote.setImageBitmap(BitmapFactory.decodeFile(selectedImagePath));
                    imageNote.setVisibility(View.VISIBLE);
                    findViewById(R.id.imageRemoveImage).setVisibility(View.VISIBLE);
                }else if(type.equals("URL")){
                    textWebURL.setText(getIntent().getStringExtra("URL"));
                    layoutWebURL.setVisibility(View.VISIBLE);
                }
            }
        }

        initMiscellaneous();
        setSubtitleIndicatorColor();

    }

    //////////// DDDDDDDDDDYYYYYYYYYYYYNNNNNNNNNNAAAAAAAAMMMMMMMOOOOOOOOOOOO
    private void setupDynamoDB() {
        AmazonDynamoDBClient dynamoDBClient = new AmazonDynamoDBClient(AWSMobileClient.getInstance());
        dynamoDBClient.setRegion(com.amazonaws.regions.Region.getRegion(AWS_REGION));

        this.dynamoDBMapper = DynamoDBMapper.builder()
                .dynamoDBClient(dynamoDBClient)
                .build();

        Log.d("DynamoDBHelper_inCreate", "DynamoDBMapper initialized");

        this.dbHelper = new DynamoDBHelper(dynamoDBMapper);

    }

    //////////// DDDDDDDDDDYYYYYYYYYYYYNNNNNNNNNNAAAAAAAAMMMMMMMOOOOOOOOOOOO


    //##################################################################################################################################


    // In CreateNoteActivity's setViewOrUpdateNote method
    private void setViewOrUpdateNote() {
        inputNoteTitle.setText(alreadyAvailableNote.getTitle());
        inputNoteSubtitle.setText(alreadyAvailableNote.getSubtitle());
        inputNoteText.setText(alreadyAvailableNote.getNoteText());
        textDateTime.setText(alreadyAvailableNote.getDateTime());

        if (alreadyAvailableNote.getTags() != null && !alreadyAvailableNote.getTags().trim().isEmpty()) {
            selectedTag = alreadyAvailableNote.getTags().trim();
        }

        if (alreadyAvailableNote.getWebLink() != null &&
                !alreadyAvailableNote.getWebLink().trim().isEmpty()) {
            textWebURL.setText(alreadyAvailableNote.getWebLink());
            layoutWebURL.setVisibility(View.VISIBLE);
        }

        if(alreadyAvailableNote.getImagePath() != null && !alreadyAvailableNote.getImagePath().trim().isEmpty()) {



            if (alreadyAvailableNote.getImagePath().startsWith("http")) {
                // Load S3 URL image
                Glide.with(this)
                        .load(alreadyAvailableNote.getImagePath())
                        .placeholder(R.drawable.error_image)
                        .error(R.drawable.error_image)
                        .into(imageNote);
                imageNote.setVisibility(View.VISIBLE);
                findViewById(R.id.imageRemoveImage).setVisibility(View.VISIBLE);
                // Add download button
                findViewById(R.id.imageDownload).setVisibility(View.VISIBLE);

                // Add download button
                ImageView downloadButton = findViewById(R.id.imageDownload); // Add this button in your layout
                downloadButton.setVisibility(View.VISIBLE);
                downloadButton.setOnClickListener(v -> downloadImage(alreadyAvailableNote.getImagePath()));
            } else {
                // Load local image
                Glide.with(this)
                        .load(new File(alreadyAvailableNote.getImagePath()))
                        .into(imageNote);
                imageNote.setVisibility(View.VISIBLE);
                findViewById(R.id.imageRemoveImage).setVisibility(View.VISIBLE);
            }
        }
    }
    //##################################################################################################################################
    // Add method to download image
    private void downloadImage(String imageUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        REQUEST_CODE_STORAGE_PERMISSION);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE_STORAGE_PERMISSION);
                return;
            }
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Downloading image...");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                // Create directory if it doesn't exist
                File directory = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "Notes");
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                // Create file
                String fileName = "Note_" + System.currentTimeMillis() + ".jpg";
                File file = new File(directory, fileName);

                // Download image
                URL url = new URL(imageUrl);
                URLConnection connection = url.openConnection();
                connection.connect();

                InputStream input = new BufferedInputStream(url.openStream());
                OutputStream output = new FileOutputStream(file);

                byte[] data = new byte[1024];
                int count;
                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                // Notify media scanner
                MediaScannerConnection.scanFile(this,
                        new String[]{file.getAbsolutePath()}, null,
                        (path, uri) -> {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(this,
                                        "Image saved to: " + file.getAbsolutePath(),
                                        Toast.LENGTH_LONG).show();
                            });
                        });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this,
                            "Error downloading image: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }



    // previous one is just create 1 note
    private void saveNote() {
        if (inputNoteTitle.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Note Title can't be Empty!", Toast.LENGTH_SHORT).show();
            return;
        } else if (inputNoteSubtitle.getText().toString().trim().isEmpty()
                && inputNoteText.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Note can't be Empty!", Toast.LENGTH_SHORT).show();
            return;
        }

        // If we have an image to upload
        Log.d( "CreateNoteActivitydynamodb", "selectedImagePath: " + selectedImagePath);

        if (selectedImagePath != null && !selectedImagePath.trim().isEmpty()) {
            Log.d("imagefilel___uri", "selectedImagePath: " + selectedImagePath);

            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setMessage("Uploading image...");
            dialog.setCancelable(false);
            dialog.show();

            // ✅ Use the selectedImagePath directly instead of converting it to a URI first
            File imageFile = new File(selectedImagePath);
            if (!imageFile.exists()) {
                Log.e("imagefilel___uri", "Error: Image file does not exist!");
                dialog.dismiss();
                saveNoteToDatabase(selectedImagePath);
                return;
            }

            // ✅ Generate the image key correctly
            String imageKey = "notes/" + System.currentTimeMillis() + "_" + imageFile.getName();
            Log.d("imagefilel___uri", "imageKey: " + imageKey);

            AmazonS3Client s3Client = new AmazonS3Client(AWSMobileClient.getInstance());
            s3Client.setRegion(Region.getRegion(Regions.fromName(AWS_REGION)));

            TransferUtility transferUtility = TransferUtility.builder()
                    .context(getApplicationContext())
                    .s3Client(s3Client)
                    .build();

            try {
                TransferObserver uploadObserver = transferUtility.upload(
                        S3_BUCKET_NAME,
                        imageKey,                    // S3 Key
                        imageFile                     // Local File
                );

                uploadObserver.setTransferListener(new TransferListener() {
                    @Override
                    public void onStateChanged(int id, TransferState state) {
                        if (state == TransferState.COMPLETED) {
                            String s3ImageUrl = "https://" + S3_BUCKET_NAME +
                                    ".s3." + AWS_REGION + ".amazonaws.com/" + imageKey;
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                saveNoteToDatabase(s3ImageUrl);
                            });
                        } else if (state == TransferState.FAILED || state == TransferState.CANCELED) {
                            Log.e("SaveNote", "Upload failed");
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                Toast.makeText(CreateNoteActivity.this,
                                        "Failed to upload image. Saving with local path.",
                                        Toast.LENGTH_SHORT).show();
                                saveNoteToDatabase(selectedImagePath);
                            });
                        }
                    }

                    @Override
                    public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                        int progress = (int) ((bytesCurrent * 100) / bytesTotal);
                        Log.d("UploadProgress", "Progress: " + progress + "%");
                    }

                    @Override
                    public void onError(int id, Exception ex) {
                        Log.e("SaveNote", "Upload error", ex);
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            Toast.makeText(CreateNoteActivity.this,
                                    "Upload error. Saving with local path.",
                                    Toast.LENGTH_SHORT).show();
                            saveNoteToDatabase(selectedImagePath);
                        });
                    }
                });

            } catch (Exception e) {
                Log.e("SaveNote", "Error initiating upload", e);
                dialog.dismiss();
                saveNoteToDatabase(selectedImagePath);
            }
        } else {
            // No image to upload
            saveNoteToDatabase(selectedImagePath);
        }

    }

    private void saveNoteToDatabase(String imagePath) {
        final Note note = new Note();
        note.setTitle(inputNoteTitle.getText().toString());
        note.setSubtitle(inputNoteSubtitle.getText().toString());
        note.setNoteText(inputNoteText.getText().toString());
        note.setDateTime(textDateTime.getText().toString());
        note.setColor(selectedNoteColor);
        note.setTags(selectedTag != null && !selectedTag.trim().isEmpty() ? selectedTag.trim() : null);
        //note.setImagePath(imagePath);

        // Debug logs
        Log.d("SaveNote", "Incoming imagePath: " + imagePath);
        Log.d("SaveNote", "selectedImagePath: " + selectedImagePath);
        if (alreadyAvailableNote != null) {
            Log.d("SaveNote", "Existing note image path: " + alreadyAvailableNote.getImagePath());
        }

        // Image path logic
        if (alreadyAvailableNote != null) {
            if ((imagePath == null || imagePath.isEmpty()) &&
                    (selectedImagePath == null || selectedImagePath.isEmpty())) {
                // No new image selected, keep existing image
                note.setImagePath(alreadyAvailableNote.getImagePath());
                Log.d("SaveNote", "Keeping existing image: " + alreadyAvailableNote.getImagePath());
            } else if (imagePath != null && !imagePath.isEmpty()) {
                // New S3 image
                note.setImagePath(imagePath);
                Log.d("SaveNote", "Setting new S3 image: " + imagePath);
            } else if (selectedImagePath != null && !selectedImagePath.isEmpty()) {
                // New local image
                note.setImagePath(selectedImagePath);
                Log.d("SaveNote", "Setting new local image: " + selectedImagePath);
            }
        } else {
            // New note
            note.setImagePath(imagePath != null && !imagePath.isEmpty() ? imagePath : selectedImagePath);
            Log.d("SaveNote", "Setting image for new note: " + note.getImagePath());
        }


        if (layoutWebURL.getVisibility() == View.VISIBLE && textWebURL.getText() != null) {
            String webLink = textWebURL.getText().toString();
            note.setWebLink(webLink);
            Log.d("CreateNote", "Setting web link: " + webLink);
        }



        /*if (layoutWebURL.getVisibility() == View.VISIBLE) {
            note.setWebLink(textWebURL.getText().toString());
            Log.d("CreateNote", "Web link: " + note.getWebLink());
        }*/

        // Save note in background thread
        new Thread(() -> {
            try {
                String userId = AWSMobileClient.getInstance().getIdentityId();

                if (alreadyAvailableNote == null) {
                    // This is a new note
                    int nextId = dbHelper.getNextId(userId);
                    note.setId(nextId);
                    note.setUserId(userId);
                    dbHelper.insertNote(note, userId);
                    Log.d("CreateNote", "Creating new note with ID: " + nextId);
                } else {
                    // This is an update to existing note
                    note.setId(alreadyAvailableNote.getId());
                    note.setUserId(alreadyAvailableNote.getUserId());
                    dbHelper.updateNote(note);
                    Log.d("CreateNote", "Updating existing note with ID: " + note.getId());
                }

                runOnUiThread(() -> {
                    AchievementManager.syncAndNotify(CreateNoteActivity.this);
                    Intent intent = new Intent();
                    setResult(RESULT_OK, intent);
                    finish();
                });
            } catch (Exception e) {
                Log.e("CreateNote", "Error saving note: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(CreateNoteActivity.this,
                            "Error saving note: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    //##################################################################################################################################

    private void initMiscellaneous(){

        final LinearLayout layoutMiscellaneous = findViewById(R.id.layoutMiscellaneous);
        final BottomSheetBehavior<LinearLayout> bottomSheetBehavior = BottomSheetBehavior.from(layoutMiscellaneous);

        layoutMiscellaneous.findViewById(R.id.textMiscellaneous).setOnClickListener(v -> {
            if(bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED){
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        setupTagSelection(layoutMiscellaneous);

        final ImageView imageColor1 = layoutMiscellaneous.findViewById(R.id.imageColor1);
        final ImageView imageColor2 = layoutMiscellaneous.findViewById(R.id.imageColor2);
        final ImageView imageColor3 = layoutMiscellaneous.findViewById(R.id.imageColor3);
        final ImageView imageColor4 = layoutMiscellaneous.findViewById(R.id.imageColor4);
        final ImageView imageColor5 = layoutMiscellaneous.findViewById(R.id.imageColor5);

        layoutMiscellaneous.findViewById(R.id.viewColor1).setOnClickListener(v -> {
            selectedNoteColor = "#333333";
            imageColor1.setImageResource(R.drawable.ic_done);
            imageColor2.setImageResource(0);
            imageColor3.setImageResource(0);
            imageColor4.setImageResource(0);
            imageColor5.setImageResource(0);
            setSubtitleIndicatorColor();

        });

        layoutMiscellaneous.findViewById(R.id.viewColor2).setOnClickListener(v -> {
            selectedNoteColor = "#FDBE3B";
            imageColor1.setImageResource(0);
            imageColor2.setImageResource(R.drawable.ic_done);
            imageColor3.setImageResource(0);
            imageColor4.setImageResource(0);
            imageColor5.setImageResource(0);
            setSubtitleIndicatorColor();

        });

        layoutMiscellaneous.findViewById(R.id.viewColor3).setOnClickListener(v -> {
            selectedNoteColor = "#FF4842";
            imageColor1.setImageResource(0);
            imageColor2.setImageResource(0);
            imageColor3.setImageResource(R.drawable.ic_done);
            imageColor4.setImageResource(0);
            imageColor5.setImageResource(0);
            setSubtitleIndicatorColor();

        });

        layoutMiscellaneous.findViewById(R.id.viewColor4).setOnClickListener(v -> {
            selectedNoteColor = "#3A52FC";
            imageColor1.setImageResource(0);
            imageColor2.setImageResource(0);
            imageColor3.setImageResource(0);
            imageColor4.setImageResource(R.drawable.ic_done);
            imageColor5.setImageResource(0);
            setSubtitleIndicatorColor();

        });

        layoutMiscellaneous.findViewById(R.id.viewColor5).setOnClickListener(v -> {
            selectedNoteColor = "#000000";
            imageColor1.setImageResource(0);
            imageColor2.setImageResource(0);
            imageColor3.setImageResource(0);
            imageColor4.setImageResource(0);
            imageColor5.setImageResource(R.drawable.ic_done);
            setSubtitleIndicatorColor();
        });


        if(alreadyAvailableNote != null && alreadyAvailableNote.getColor() != null && ! alreadyAvailableNote.getColor().trim().isEmpty()){
            switch (alreadyAvailableNote.getColor()){
                case "#FDBE3B" :
                    layoutMiscellaneous.findViewById(R.id.viewColor2).performClick();
                    break;
                case "#FF4842" :
                    layoutMiscellaneous.findViewById(R.id.viewColor3).performClick();
                    break;
                case "#3A52FC" :
                    layoutMiscellaneous.findViewById(R.id.viewColor4).performClick();
                    break;
                case "#000000" :
                    layoutMiscellaneous.findViewById(R.id.viewColor5).performClick();
                    break;
            }
        }

        layoutMiscellaneous.findViewById(R.id.layoutAddImage).setOnClickListener(v -> {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            /*if(ContextCompat.checkSelfPermission(
                    getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(
                        CreateNoteActivity.this,
                        new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_CODE_STORAGE_PERMISSION
                );
            }else {
                selectImage();
            }*/

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                            REQUEST_CODE_STORAGE_PERMISSION);
                } else {
                    selectImage();
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            REQUEST_CODE_STORAGE_PERMISSION);
                } else {
                    selectImage();
                }
            }


        });

        layoutMiscellaneous.findViewById(R.id.layoutAddUrl).setOnClickListener(v -> {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            showAddURLDialog();
        });

        if(alreadyAvailableNote !=null){
            layoutMiscellaneous.findViewById(R.id.layoutDeleteNote).setVisibility(View.VISIBLE);
            layoutMiscellaneous.findViewById(R.id.layoutDeleteNote).setOnClickListener(v -> {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                showDeleteNoteDialog();
            });
        }
    }

    private void setupTagSelection(LinearLayout layoutMiscellaneous) {
        ChipGroup chipGroup = layoutMiscellaneous.findViewById(R.id.chipGroupNoteTags);
        if (chipGroup == null) {
            return;
        }

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedTag = "";
            } else {
                Chip chip = group.findViewById(checkedIds.get(0));
                selectedTag = chip != null && chip.getTag() != null ? chip.getTag().toString() : "";
            }
        });

        if (selectedTag != null && !selectedTag.isEmpty()) {
            int chipId = getChipIdForTag(selectedTag);
            if (chipId != View.NO_ID) {
                chipGroup.check(chipId);
            }
        }
    }

    private int getChipIdForTag(String tag) {
        switch (tag.toLowerCase()) {
            case NoteTags.WORK:
                return R.id.chipTagWork;
            case NoteTags.STUDY:
                return R.id.chipTagStudy;
            case NoteTags.PERSONAL:
                return R.id.chipTagPersonal;
            case NoteTags.IDEAS:
                return R.id.chipTagIdeas;
            default:
                return View.NO_ID;
        }
    }

    private void showDeleteNoteDialog() {
        if (dialogDeleteNote == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(CreateNoteActivity.this);
            View view = LayoutInflater.from(this).inflate(
                    R.layout.layout_delete_note,
                    (ViewGroup) findViewById(R.id.layoutDeleteNoteContainer)
            );
            builder.setView(view);
            dialogDeleteNote = builder.create();
            if (dialogDeleteNote.getWindow() != null) {
                dialogDeleteNote.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            }
            view.findViewById(R.id.textDeleteNote).setOnClickListener(v -> {
                new Thread(() -> {
                    try {
                        dbHelper.deleteNote(alreadyAvailableNote);
                        runOnUiThread(() -> {
                            Intent intent = new Intent();
                            intent.putExtra("isNoteDeleted", true);
                            setResult(RESULT_OK, intent);
                            finish();
                        });
                    } catch (Exception e) {
                        Log.e("DeleteNote", "Error deleting note: " + e.getMessage(), e);
                        runOnUiThread(() -> {
                            Toast.makeText(CreateNoteActivity.this,
                                    "Error deleting note: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            });

            view.findViewById(R.id.textCancel).setOnClickListener(v -> dialogDeleteNote.dismiss());
        }
        dialogDeleteNote.show();
    }


    //////////// DDDDDDDDDDYYYYYYYYYYYYNNNNNNNNNNAAAAAAAAMMMMMMMOOOOOOOOOOOO


    private void setSubtitleIndicatorColor(){

        GradientDrawable gradientDrawable  = (GradientDrawable) viewSubtitleIndicator.getBackground();
        gradientDrawable.setColor(Color.parseColor(selectedNoteColor));
    }

    @SuppressLint({"QueryPermissionsNeeded", "IntentReset"})

    private void selectImage() {
        /*Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGE);*/

        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGE);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectImage();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //####################################################################################################

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_IMAGE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri imageUri = data.getData();

                Log.d("ImageURI", "Selected Image URI: " + imageUri.toString());
                try {
                    String filePath = getRealPathFromURI(imageUri);
                    selectedImagePath = filePath;
                    Glide.with(this)
                            .load(imageUri)
                            .into(imageNote);
                    imageNote.setVisibility(View.VISIBLE);
                    findViewById(R.id.imageRemoveImage).setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    //################################################################################################

    ///////////////////////////////////////////////////
    private String getRealPathFromURI(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor == null) return uri.getPath();

        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        String path = cursor.getString(columnIndex);
        cursor.close();
        return path;
    }

   /* private String getPathFromUri(Uri contentUri){
        String filePath;
        Cursor cursor = getContentResolver()
                .query(contentUri,null,null,null,null);
        if(cursor == null){
            filePath = contentUri.getPath();

        }else {
            cursor.moveToFirst();
            int index = cursor.getColumnIndex("_data");
            filePath =cursor.getString(index);
            cursor.close();
        }
        return filePath;
    }
*/

    private void showAddURLDialog(){
        if(dialogAddURL == null){
            AlertDialog.Builder builder = new AlertDialog.Builder(CreateNoteActivity.this);
            View view = LayoutInflater.from(this).inflate(
                    R.layout.layout_add_url,
                    (ViewGroup) findViewById(R.id.layoutAddUrlContainer)
            );
            builder.setView(view);

            dialogAddURL = builder.create();
            if(dialogAddURL.getWindow() !=null){
                dialogAddURL.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            }

            final EditText inputURL = view.findViewById(R.id.inputURL);
            inputURL.requestFocus();

            view.findViewById(R.id.textAdd).setOnClickListener(v -> {
                if(inputURL.getText().toString().trim().isEmpty()){
                    Toast.makeText(CreateNoteActivity.this,"Enter URL",Toast.LENGTH_SHORT).show();

                }else if(!Patterns.WEB_URL.matcher(inputURL.getText().toString()).matches()){
                    Toast.makeText(CreateNoteActivity.this,"Enter Valid URL",Toast.LENGTH_SHORT).show();
                }else {
                    textWebURL .setText(inputURL.getText().toString());
                    layoutWebURL .setVisibility(View.VISIBLE);
                    dialogAddURL.dismiss();
                }
            });

            view.findViewById(R.id.textCancel).setOnClickListener(v -> dialogAddURL.dismiss());

        }
        dialogAddURL.show();

    }
}
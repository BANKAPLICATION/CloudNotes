CloudNotes with Fitness Tracking

Description

CloudNotes is a full-stack Android application that combines secure cloud-backed note-taking with fitness tracking. Built for the Mobile Application Cloud Computing course, it uses AWS (Cognito, DynamoDB, S3) for authentication, data storage, and file uploads. Users can register, log in, manage notes with tags and images, track daily steps, edit their profile, and unlock achievements — all synced to the cloud in real time.

Package: com.cloudnotes.app
Region: eu-north-1 (configurable in CloudConfig.java)

Features

Authentication and User Account
- Splash screen with automatic session check (signed in → home, signed out → login)
- User registration with AWS Cognito (email + password)
- Email OTP verification after sign up
- Resend OTP if code not received
- Login with username and password
- Forgot password flow (email code + new password)
- Change password from settings
- Secure logout
- User profile screen with editable fields (name, phone, address, date of birth)
- Profile photo upload to AWS S3
- User data stored in Cognito user attributes

Notes Management
- Create, edit, and delete notes
- Notes stored in AWS DynamoDB (UserNotesPro table)
- Tag system: Work, Study, Personal, Ideas with filter chips on main screen
- Real-time search by title and subtitle
- Color-coding for each note
- Image attachments uploaded to AWS S3
- Web link attachments inside notes
- Paginated note loading on scroll (infinite scroll)
- Cross-device synchronization
- Single-column list layout with welcome header
- Quick actions: add note, add image, add URL from floating action dock

Fitness Tracking
- Daily step counter using device step sensor (TYPE_STEP_COUNTER)
- Background fitness service with foreground notification (FitnessUploadService)
- Step and distance data saved to DynamoDB (FitnessData table)
- Interactive line charts for steps and distance (MPAndroidChart)
- Daily step goal (10,000 steps) with progress bar
- Demo Fitness Data mode for emulators without hardware step sensor
- Activity history loaded from cloud on screen open

Navigation and App UI
- Navigation drawer: Home, Profile, Settings, Fitness, Achievements, Share App, Logout
- Drawer header with user profile preview
- Modern CloudNotes dark theme (Material Design 3)
- Gradient toolbar, tag chips, search bar, floating action dock
- Dedicated screens: Login, Register, Create Note, Profile, Fitness, Achievements

Gamification
- Achievements and badges system (13 unlockable goals)
- Progress tracking for notes, fitness, and profile activity
- Badge screen with locked / unlocked states

Sharing
- Share app link via system share sheet from drawer menu

Technical Stack

Cloud Services
- AWS Cognito User Pool (sign up, login, OTP, forgot password)
- AWS Cognito Identity Pool (temporary AWS credentials)
- AWS DynamoDB (notes + fitness records)
- AWS S3 (note images + profile photos)
- AWS Amplify Android SDK + AWS Mobile Client

Frontend
- Android SDK 34, Java 11
- Material Design 3
- MPAndroidChart for fitness graphs
- Glide for image loading
- Lottie animations
- SDP/SSP responsive sizing

Project Structure

app/src/main/java/com/cloudnotes/app/
├── CloudNotesApplication.java
├── config/CloudConfig.java
├── activities/
│   ├── SplashActivity.java
│   ├── LoginActivity.java
│   ├── RegisterActivity.java
│   ├── ForgotPasswordActivity.java
│   ├── ChangePasswordActivity.java
│   ├── MainActivity.java
│   ├── CreateNoteActivity.java
│   ├── ProfileActivity.java
│   ├── FitnessActivity.java
│   └── AchievementsActivity.java
├── adapters/
│   ├── NotesAdapter.java
│   └── AchievementsAdapter.java
├── database/
│   └── DynamoDBHelper.java
├── entities/
│   ├── Note.java
│   └── FitnessRecord.java
├── services/
│   └── FitnessUploadService.java
├── listeners/
│   └── NotesListener.java
└── utility/
    ├── UserUtils.java
    ├── ShareUtils.java
    ├── NoteTags.java
    ├── AchievementManager.java
    ├── Achievement.java
    └── AchievementStats.java

App Flow

1. SplashActivity checks Cognito session
2. LoginActivity or RegisterActivity (with OTP confirmation)
3. MainActivity — notes list, search, tags, drawer navigation
4. CreateNoteActivity — create or edit note (color, tag, image, URL)
5. ProfileActivity — view and edit user info, upload photo
6. FitnessActivity — steps, charts, daily goal, demo data
7. AchievementsActivity — badges and progress
8. ForgotPasswordActivity / ChangePasswordActivity — password management

Setup

1. Clone or download the repository
2. Copy AWS config templates (secrets are not included in the repo):

cp config/awsconfiguration.json.example app/src/main/res/raw/awsconfiguration.json
cp config/amplifyconfiguration.json.example app/src/main/res/raw/amplifyconfiguration.json

3. Replace YOUR_* placeholders with your AWS Cognito, Identity Pool, and S3 bucket IDs
4. Set your S3 bucket in CloudConfig.java:

public static final String S3_BUCKET_NAME = "your-bucket-name";

5. Open the project in Android Studio, sync Gradle, and run on emulator or device (API 24+)

For full AWS setup steps see AWS_SETUP.md

AWS Resources Required
- Cognito User Pool with email verification (OTP)
- Cognito Identity Pool linked to User Pool
- DynamoDB table UserNotesPro (partition key: userId, sort key: id)
- DynamoDB table FitnessData (partition key: userId, sort key: timestamp)
- S3 bucket for images with appropriate IAM permissions

Dependencies

Main dependencies are defined in app/build.gradle and gradle/libs.versions.toml:
- AWS Amplify, AWS Mobile Client, Cognito Auth, S3 Transfer Utility, DynamoDB Mapper
- Material Design, AppCompat, ConstraintLayout, DrawerLayout
- Glide, MPAndroidChart, Lottie
- SDP/SSP scaling libraries

Features in Development
- Offline support
- Weekly fitness dashboard
- Note export and sharing
- Push notifications for step goals

License

MIT License

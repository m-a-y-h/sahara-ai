# project-sahara_pk

S.A.H.A.R.A. (Substance Abuse, Harm Assessment, Response & Assistance) - A comprehensive ML-based mobile application for detecting and reporting early phases of substance abuse (specifically drugs).

## Status: 🛠 In Development 🛠
This project is currently under active development. Features are being implemented and are subject to change. Contributions are welcome!

## Project Overview
Drug abuse is a growing crisis in Pakistan, affecting over 4 million people, primarily youth. This Android app focuses on early detection using AI to analyze facial expressions, voice, behavior, and text inputs, achieving an expected 85% accuracy. It provides 24/7 emotional support via a multilingual chatbot (English, Urdu script, Roman Urdu), recovery tracking through gamification, and connections to counselors/NGOs. Privacy is ensured with anonymous usage and AES-256 encryption.

Key technologies include Android Studio, Hugging Face Transformers for ML models, Firebase for data storage, and public datasets like FER-2013 and RAVDESS for training.

## Development Phase Setup

Make sure you have the following installed:
- [Git](https://git-scm.com/downloads) | `git --version`
- [Android Studio](https://developer.android.com/studio) | Check via Help > About
- [Python 3.x](https://www.python.org/downloads/) (for ML model training) | `python -V` and `pip -V`
> **Note:** Python is used for prototyping and training ML models (e.g., with Hugging Face). The app itself is in Kotlin/Java.

## 1. Configuring Git
Configure Git globally if you haven't done so already; carefully edit and run the following commands in your terminal (one-by-one):
```bash
git config --global user.email "your-email@example.com"
git config --global user.name "Your Name or Username"
git config --global core.editor "code --wait"  # If using VS Code; adjust if needed
```

## 2. Clone the Repository
Make sure that your terminal's path is set to your Desktop or a preferred workspace:
```bash
git clone https://codeberg.org/solarmane/project-sahara_pk.git
```

## 3. Open and Set Up the Project
1. Open the cloned repository in Android Studio.
2. Sync the project with Gradle files (File > Sync Project with Gradle Files).
3. Install required dependencies: Android Studio will prompt for any missing SDKs or tools.
4. Set up Firebase:
   - Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com).
      - Download `google-services.json` and place it in the `app/` directory.
      5. Configure Google Maps API:
         - Obtain an API key from [Google Cloud Console](https://console.cloud.google.com).
            - Add it to `AndroidManifest.xml` under `<meta-data android:name="com.google.android.geo.API_KEY">`.
            6. For ML components:
               - Install Python libraries: `pip install torch transformers datasets`.
                  - Training scripts (if added) can be run from the `ml/` directory (create if needed).
                  7. Build and run the app on an emulator or device (Run > Run 'app').

                  > **Note:** Replace placeholder API keys and configurations with your own. For Firebase, update database rules for security. ML models may require fine-tuning on datasets like FER-2013; scripts will be added soon.

                  ## 4. Final Steps
                  - Test the app: Grant camera/mic permissions and interact with the chatbot.
                  - Train ML models: Use public datasets for emotion detection; domain adaptation techniques (e.g., DANN) for multilingual support.
                  - Contribute: After making changes in your branch, commit, push, and submit a pull request for review.

                  For issues or suggestions, open an issue on Codeberg.
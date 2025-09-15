# project-sahara_pk

S.A.H.A.R.A. (Substance Abuse Harm Assessment, Response & Assistance) - An innovative AI-driven Android application designed to detect and prevent early-stage prescription drug misuse among Pakistani youth, transitioning seamlessly to full abuse prevention through multimodal analysis and empathetic support.

## Status: 🛠 In Development 🛠
This project is currently under active development. Features are being implemented and are subject to change. Contributions are welcome!

## Project Overview
In mid-2025, drug abuse in Pakistan has escalated dramatically. The UNODC World Drug Report 2025 highlights global trends compounding this, with Pakistan facing 6.7-10 million drug users, over 800,000 heroin-dependent, and prescription opioids like benzodiazepines showing 41% misuse rates among youth.

Early intervention is critical, as misuse of prescription drugs (e.g., painkillers, sedatives) often precedes harder substance abuse, exacerbated by peer pressure, mental health issues, and stigma.

With mobile penetration at 75.2% (190 million connections) and 77% of smartphone users aged 21-30, this app leverages high youth accessibility for proactive screening and support.

The app uses AI for 85%+ accurate early risk detection via facial expressions, voice, keystrokes, and culturally adapted text inputs based on DAST-10/WHO-ASSIST. It offers 24/7 multilingual chatbot support (English, Urdu script, Roman Urdu), gamified recovery tracking, and NGO/counselor connections via Google Maps. Privacy is paramount with AES-256 encryption and anonymous usage, addressing Pakistan's cultural barriers to create a stigma-free recovery space.

Key technologies: Android Studio, Hugging Face Transformers for ML (emotion/behavior analysis), Firebase for secure storage, and datasets like FER-2013, RAVDESS for training with domain adaptation for local contexts.

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
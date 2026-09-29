# Exam Maker 0.1.0

Offline Android practice exam. This build contains ten sample general knowledge questions, not CAAP questions or official exam material.

## User flow
Open app → start exam → tap one choice → see correctness and running score → next question → final score and percentage. Closing the app resumes the saved attempt. A new exam reshuffles questions and options and replaces the old attempt.

## MVP acceptance criteria
- Invalid bank fails closed instead of presenting questions.
- Exactly one choice accepted for each question; correct index remains aligned after shuffle.
- Score is displayed after submission; final score appears only after every question is answered.
- Process restart restores the same question and answer order and submitted choices.
- App works without internet or permissions and retains the latest attempt on device.

## Build
Install JDK 17, Android SDK platform 35 and build-tools 35.0.0, and Gradle 8.11.1. Run `python3 tools/validate_bank.py` then `gradle :app:testDebugUnitTest :app:assembleDebug`. APK: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions uploads that APK from successful workflow runs.

## Bank format
Edit `app/src/main/assets/questions.json`. Each question needs a stable unique `id`, nonempty `prompt` and `explanation`, 2–6 distinct options, and a zero-based `correct` index. Increment `bankId` when editing published questions, so saved attempts are discarded safely. Content is bundled in APK, not fetched from a server. Question provenance and subject review must precede a CAAP-focused bank.

## Installation and upgrade
For private testing, download the successful `exam-maker-v0.1.0-debug-apk` CI artifact, extract `app-debug.apk`, and install it on Android 8 or later. Upgrade using a new APK with the same application ID and signing key and a higher versionCode. GitHub Actions debug signing keys are ephemeral across runners, so a later CI debug artifact may require uninstalling the earlier app, which deletes its saved attempt. Use a stable private signing key for continuous upgrades. For rollback, reinstall an earlier same-key APK after removing the newer version; Android normally rejects version downgrades in place. Save any wanted results before uninstalling.

## Known limits and next milestone
No verified CAAP question bank, topic selection, review history, import UI, timed mode, remote database, or stable production signing yet. Next: review licensed question provenance and add a versioned subject bank, then set up durable signing and test on a physical Android device.

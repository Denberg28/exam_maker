# Exam Maker 0.4.0

Portrait Android practice exam that works offline. This build contains ten sample general knowledge questions, not CAAP questions or official exam material.

## User flow
Open app → start exam → tap one choice → see the locked selection and current score → next question → final score and percentage. Closing the app resumes the saved attempt. A new exam reshuffles questions and options and replaces the old attempt.

## Admin and results
On first use, open Admin and create a local password (at least 8 characters). Admin can add, rename, or delete test sets; add, edit, or delete four-option questions; and export completed attempts to an actual `.xlsx` workbook using the Android document picker. The bundled sample set is imported once. Admin access and all data remain on this device. The password is salted and derived with PBKDF2; there is no online recovery. Removing app data deletes the sets, results, and admin account. Results are not encrypted at rest beyond Android device storage protection.

Before a new attempt, the examiner enters a name and ID. The attempt captures a snapshot of its questions so admin edits cannot change it mid-exam. Completed results are saved once per attempt, even after reopening the result screen. Export includes UTC date, examiner name/ID, set name, score, maximum, and percentage. Handle exported workbooks as personal data.

## MVP acceptance criteria
- Invalid bank fails closed instead of presenting questions.
- Exactly one choice accepted for each question; correct index remains aligned after shuffle.
- Score starts at the question count and drops once for each incorrect answer; no correct answer or explanation appears on submission. Final score appears after every question is answered.
- Process restart restores the same question and answer order and submitted choices.
- App works without internet or permissions and retains the latest attempt on device.
- Admin edits four-option sets locally; completed results export to XLSX.

## Build
Install JDK 17, Android SDK platform 35 and build-tools 35.0.0, and Gradle 8.11.1. Run `python3 tools/validate_bank.py` then `gradle :app:testDebugUnitTest :app:assembleDebug`. APK: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions uploads that APK from successful workflow runs.

## Bank format
Edit `app/src/main/assets/questions.json`. Each question needs a stable unique `id`, nonempty `prompt` and `explanation`, exactly 4 distinct options, and a zero-based `correct` index. Increment `bankId` when editing published questions, so saved attempts are discarded safely. Content is bundled in APK, not fetched from a server. Question provenance and subject review must precede a CAAP-focused bank.

## Installation and upgrade
For private testing, download the successful `exam-maker-v0.4.0-debug-apk` CI artifact, extract `app-debug.apk`, and install it on Android 8 or later. Upgrade using a new APK with the same application ID and signing key and a higher versionCode. GitHub Actions debug signing keys are ephemeral across runners, so a later CI debug artifact may require uninstalling the earlier app, which deletes its saved attempt. Use a stable private signing key for continuous upgrades. For rollback, reinstall an earlier same-key APK after removing the newer version; Android normally rejects version downgrades in place. Save any wanted results before uninstalling.

## Known limits and next milestone
No verified CAAP question bank, multi-admin roles, cloud sync, timed mode, remote database, password recovery, or stable production signing yet. Next: review licensed question provenance and add a versioned subject bank, then set up durable signing and test on a physical Android device.

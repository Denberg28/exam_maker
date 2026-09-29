# Exam Maker 0.7.0

Portrait Android practice exam that works offline. This build contains ten sample general knowledge questions, not CAAP questions or official exam material.

## User flow
Open app → start exam → tap one choice → see the locked selection and current score → next question → final score and percentage. Closing the app resumes the saved attempt. A new exam reshuffles questions and options and replaces the old attempt.

## Admin and results
On first use, open Admin and create a local password (at least 8 characters). Admin can add, rename, or delete test sets; add, edit, or delete four-option questions; and export completed attempts to an actual `.xlsx` workbook using the Android document picker. The bundled sample set is imported once. Admin access and all data remain on this device. The password is salted and derived with PBKDF2; there is no online recovery. Removing app data deletes the sets, results, and admin account. Results are not encrypted at rest beyond Android device storage protection.

Before a new attempt, the examiner enters a name and ID. The attempt captures a snapshot of its questions so admin edits cannot change it mid-exam. Completed results are saved once per attempt, even after reopening the result screen. Export includes UTC date, examiner name/ID, set name, score, maximum, and percentage. Handle exported workbooks as personal data.

## Question template and admin import

Admin can add one question directly from a test set. For bulk entry, tap **Download blank question template (.csv)** in Android Admin and save the file. Open it in Excel or a text editor, add one question per row, then save as **CSV UTF-8**. The columns are `question,option_a,option_b,option_c,option_d,correct_option,explanation`. Put `A`, `B`, `C`, or `D` in `correct_option`. Explanations may be blank and are never shown after submission. Keep the header unchanged. Quoted commas, quotes, and line breaks are supported.

Tap **Import completed question template (.csv)**, review the validated question count and sample prompts, then choose **Create new test set** and enter a name or choose an existing set. Import accepts up to 500 questions and 2 MB per file. It rejects incomplete, duplicate, or malformed rows; database changes are atomic. Existing attempts retain their question snapshots. Sets can contain at most 500 questions. The template CSV is for editing questions; the XLSX export is for results.

## Web QR sessions (self-hosted)

For the GitHub Streamlit Community Cloud deployment settings and data-retention limits, see [web/DEPLOY.md](web/DEPLOY.md).

Android home includes **Join an online exam**, which opens the hosted Streamlit start page in a browser. Android Admin includes **Open web Admin portal**. Export a set from its Android set page and upload the JSON there to create an online session. The web Admin password is separate from the local Android Admin password. The hosted portal is `https://exammaker.streamlit.app/`; it needs internet and a browser. Community Cloud storage is temporary, so do not use it for real examiner records without a durable backend.

The Android admin can export a prepared set as JSON. On a reachable computer/server:

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r web/requirements.txt
export EXAM_ADMIN_PASSWORD='choose-a-long-unique-password'
export EXAM_PUBLIC_URL='http://YOUR-LAN-IP:8501'
export EXAM_DB_PATH='web/data/exams.sqlite3'
streamlit run web/app.py --server.address 0.0.0.0 --server.port 8501
```

Open `EXAM_PUBLIC_URL/?admin=1`, sign in, upload the JSON, name a session, and create it. The portal displays its QR code and link. Open the same URL from an Android or desktop browser on the reachable network. Repeat for as many sessions as needed; each session has an independent code and can be closed to new entrants. Candidate attempts, random order, and results persist in the server's SQLite file. Closing a session does not interrupt existing attempts. Admin can export per-session or all results to XLSX.

`EXAM_PUBLIC_URL` must be reachable by candidate devices. Use an HTTPS endpoint and a reverse proxy with login rate limiting before Internet exposure. Restrict filesystem access to `EXAM_DB_PATH` and back it up; it contains names, IDs, questions, answers, and results. QR links grant entry to a test set, so share them only with intended participants. The Android-local results and web results are separate; the exported set JSON is the bridge. Streamlit session state alone resets with a WebSocket reconnect, so web attempts also use server SQLite and an opaque attempt ID in the browser URL. Keep that URL private. This first iteration does not provide managed hosting, device-level QA, or automatic Android-to-server synchronization.

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
For private testing, download the successful `exam-maker-v0.7.0-debug-apk` CI artifact, extract `app-debug.apk`, and install it on Android 8 or later. Upgrade using a new APK with the same application ID and signing key and a higher versionCode. GitHub Actions debug signing keys are ephemeral across runners, so a later CI debug artifact may require uninstalling the earlier app, which deletes its saved attempt. Use a stable private signing key for continuous upgrades. For rollback, reinstall an earlier same-key APK after removing the newer version; Android normally rejects version downgrades in place. Save any wanted results before uninstalling.

## Known limits and next milestone
No verified CAAP question bank, multi-admin roles, automatic Android/web sync, timed mode, password recovery, or stable production signing yet. Physical device testing of file pickers, accessibility, and import remains pending. Next: review licensed question provenance and add a versioned subject bank, then set up durable signing and test on a physical Android device.

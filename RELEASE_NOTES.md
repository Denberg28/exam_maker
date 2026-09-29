# Exam Maker v0.7.0 private testing build

## New in 0.7.0
Android home opens the hosted exam start page in a browser. Android Admin opens the web Admin portal, where an exported test-set JSON can be uploaded to create QR sessions. The Streamlit landing page shows a QR code to the open-session list. Online and offline results remain separate.

Portrait offline sample practice exam with four choices per question. Ten general knowledge questions, four options each. Current score starts at ten and decreases for incorrect answers; correct options are not revealed after submission. This is not a CAAP question bank or official examination app.

## Install
Download `app-debug.apk` below and install on Android 8 or newer. The APK uses the GitHub runner's debug signing key. Android may request permission to install from your browser or file manager.

## Upgrade and rollback
The debug signing key changes between CI runners. Upgrading from v0.6.0 may require uninstalling it first, which erases local sets, admin account, and results unless exported. For rollback, export results, uninstall and reinstall the earlier APK. Do not use this debug build for production distribution.

## Limits
No physical device test yet. Answers are stored only on-device. No timer, categories, remotely hosted bank, or history. Next milestone: review a sourced CAAP-oriented practice bank and establish stable private signing.

## Admin and results
Create a local admin password at first use, manage test sets and questions, and export results as an XLSX file. Each examiner enters a name and ID before starting. Results stay on-device until exported. Admin password recovery and cloud backup are not available.

## Web QR sessions
The final score is centered in the APK. Android admin can export a prepared set to JSON, then upload it to the self-hosted Streamlit portal. The portal creates independent sessions and QR links for Android or desktop browsers. Configure the reachable URL and admin password before sharing links. The server and Android device maintain separate databases.

## Admin question template
Download the blank CSV template from Android Admin. Fill one row per question in Excel (save as CSV UTF-8), import it, review the preview, and choose a new or existing test set. Incorrect rows reject the entire import. The Admin question list is paged; full result export remains available. Starting another exam now confirms before replacing unfinished progress.

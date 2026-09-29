# Exam Maker v0.9.1 private testing build

## New in 0.9.1
Examiner options now appear as direct answer buttons without A–D prefixes. A tap saves the answer and immediately shows the next question or the final result. Web results center the score and percentage. Existing unfinished Android attempts resume past an answer already submitted in an older build.

## Previously in 0.9.0
The bundled practice bank has 50 general science and introductory aviation questions. Android Admin can set how many random questions an examiner receives from each test set. Web Admin can select the same sample bank or upload CSV/JSON, then set the random question count for a published session. The count is saved with each attempt/session, and earlier Android data and web sessions remain usable. These sample questions are for software testing and have not been reviewed as CAAP exam content.

## Previously in 0.8.0
Web Admin now creates drafts that require explicit publication. Results remain exportable after a session closes. Database access was reviewed for concurrent attempts and restart/resume; a hosting data-retention warning appears in Admin. Android still opens the web portal while keeping offline exams and results separate.

Portrait offline sample practice exam with four choices per question. Fifty practice questions, four options each. Current score starts at the configured exam length and decreases for incorrect answers; correct options are not revealed after submission. This is not a CAAP question bank or official examination app.

## Install
Download `app-debug.apk` below and install on Android 8 or newer. The APK uses the GitHub runner's debug signing key. Android may request permission to install from your browser or file manager.

## Upgrade and rollback
The debug signing key changes between CI runners. Upgrading from an earlier CI debug APK may require uninstalling it first, which erases local sets, admin account, and results unless exported. For rollback, export results, uninstall and reinstall the earlier APK. Do not use this debug build for production distribution.

## Limits
No physical device test yet. Answers are stored only on-device. No timer, categories, remotely hosted bank, or history. Next milestone: review a sourced CAAP-oriented practice bank and establish stable private signing.

## Admin and results
Create a local admin password at first use, manage test sets and questions, and export results as an XLSX file. Each examiner enters a name and ID before starting. Results stay on-device until exported. Admin password recovery and cloud backup are not available.

## Web QR sessions
The final score is centered in the APK. Android admin can export a prepared set to JSON, then upload it to the self-hosted Streamlit portal. The portal creates independent sessions and QR links for Android or desktop browsers. Configure the reachable URL and admin password before sharing links. The server and Android device maintain separate databases.

## Admin question template
Download the blank CSV template from Android Admin. Fill one row per question in Excel (save as CSV UTF-8), import it, review the preview, and choose a new or existing test set. Incorrect rows reject the entire import. The Admin question list is paged; full result export remains available. Starting another exam now confirms before replacing unfinished progress.

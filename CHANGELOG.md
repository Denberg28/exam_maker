# Changelog

## 0.8.0 — 2026-09-29
- Reviewed APK set export, local recording, web draft/publish, concurrent attempts and result XLSX export.
- Set web SQLite WAL mode during initialization instead of on each request and tested restart/resume plus duplicate submission.
- Showed the web Admin an explicit warning about ephemeral Community Cloud results.

## 0.7.0 — 2026-09-29
- Added Android browser handoff to the hosted exam start and web Admin pages, with no new app permissions.
- Added a landing QR to the Streamlit open-session list and a direct session QR for each prepared set.
- Kept offline attempts and online sessions separate; documented the hosted SQLite retention limit.
## 0.6.0 — 2026-09-29
- Download CSV question template; bulk import to a named new set or existing set with preview and atomic validation.
- Page Android question/result lists, improve choice accessibility and button sizing, confirm replacement of unfinished attempts.
- Render only the selected web session and prepare exports on demand.

## 0.5.0 — 2026-09-29
- Center final score on Android.
- Export prepared Android test sets to JSON for the Streamlit portal.
- Streamlit admin creates multiple independent QR sessions; candidates take exams in Android and desktop browsers with server-persisted attempts and XLSX result export.

## 0.4.0 — 2026-09-29
- Local admin account and editable named test sets with four-choice question CRUD.
- Examiner name/ID collection, persistent completed results, and XLSX export.
- Resumable attempts use question snapshots so admin changes do not alter an active exam.

## 0.3.0 — 2026-09-29
- Lock the app to portrait while retaining the four-choice minimal exam screen.

## 0.2.0 — 2026-09-29
- Modern minimal exam screen with padded cards and buttons.
- Four options required; no answer reveal after selection; fixed score deducts one for each incorrect answer.

## 0.1.0 — 2026-09-29
- Initial offline practice exam with shuffled questions and options, immediate score, resumable attempt, and final result.
- Bundled ten sample questions and bank validation.

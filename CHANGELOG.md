# Changelog
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

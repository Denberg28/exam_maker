# Streamlit deployment

## GitHub entry point

In Streamlit Community Cloud, create an app with:

| Setting | Value |
| --- | --- |
| Repository | `Denberg28/exam_maker` |
| Branch | `main` |
| Main file path | `web/app.py` |
| Python version | `3.12` |
| Dependencies | `web/requirements.txt` (detected alongside the entry point) |

The administrator opens `https://YOUR-APP.streamlit.app/?admin=1`. Examiner QR links use the public URL configured below. Upload a prepared test-set JSON exported from Android Admin, give its session a name, and create it.

In **Advanced settings → Secrets**, set unique values (never commit them):

```toml
EXAM_ADMIN_PASSWORD = "replace-with-a-long-unique-secret"
EXAM_PUBLIC_URL = "https://YOUR-APP.streamlit.app"
```

Set `EXAM_PUBLIC_URL` to the actual assigned URL, without a trailing slash, then restart the app and verify a QR link from a separate browser/device. If it is omitted, the portal uses the administrator's current browser URL when generating links. Rotate the password if it is exposed. The default SQLite file is `web/data/exams.sqlite3` and is excluded from git.

The landing page shows a QR code to the exam start page. Scanning it opens the list of published sessions; an examiner selects a session and enters name and ID. Admin can still create a separate QR code for one specific session. Select **Open Admin**, sign in, export a prepared set JSON from Android Admin, upload it, enter a session name, and select **Create session**. A new session is a draft; review its title, then press **Publish session**. Its QR code appears and the start page lists it. **Close session** prevents new attempts but lets existing attempts finish and keeps their results exportable.

If Admin reports that it is not configured, open the Streamlit Community Cloud app dashboard, choose this app, open **Settings → Secrets**, enter `EXAM_ADMIN_PASSWORD` as shown above, save and reboot. The local Android Admin password does not configure the server. Do not send or commit the password. A password is required before web creation, publication, or result export can be used.

## Data retention and intended use

**Community Cloud local storage is ephemeral.** A restart, redeploy, or platform maintenance can erase the SQLite file, including sessions, examiner names and IDs, attempt progress, and results. The present backend has no external database adapter. Use a Community Cloud app only with disposable sample data; export results immediately and expect links and active attempts to stop working after data loss. Do not conduct a real exam or collect real examiner information there.

For real examinations, run the same entry point on a server with a persistent volume at `EXAM_DB_PATH`, backups, HTTPS, and a rate-limiting reverse proxy, or implement and verify a durable hosted database first. Restrict access to the DB and exported workbooks. A new deployment using an empty database cannot recover prior examiner records from QR links.

## Smoke check

After deployment, open the landing page and admin sign-in, import a disposable four-option set, scan the session QR code from another browser, complete an attempt, and export the XLSX. Restart the app and confirm the expected retention behavior for the selected host. GitHub Actions runs the web unit tests and Android build on pushes to `main`; version tags trigger an APK prerelease.

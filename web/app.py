"""Run with: streamlit run web/app.py --server.address 0.0.0.0"""
import io
import json
import os
from pathlib import Path
from urllib.parse import urlencode

import qrcode
import streamlit as st
from core import (initialize, create_session, list_sessions, get_session, set_session_active,
                  start_attempt, attempt_view, submit_answer, results, export_xlsx,
                  bank_from_csv, question_template)

DB = os.environ.get("EXAM_DB_PATH", "web/data/exams.sqlite3")
PUBLIC_URL = os.environ.get("EXAM_PUBLIC_URL", "").rstrip("/")
ADMIN_PASSWORD = os.environ.get("EXAM_ADMIN_PASSWORD", "")
initialize(DB)
st.set_page_config(page_title="Exam Maker", page_icon="📝", layout="centered")
st.markdown("""<style>.block-container{max-width:700px;padding-top:2rem}div.stButton button,div.stDownloadButton button{border-radius:12px;min-height:48px;width:100%}</style>""", unsafe_allow_html=True)


def qr_png(url):
    image = qrcode.make(url)
    out = io.BytesIO()
    image.save(out, format="PNG")
    return out.getvalue()


def session_base_url():
    """Use the configured canonical URL, or the current browser URL for small demos."""
    if PUBLIC_URL:
        return PUBLIC_URL
    return st.context.url.rstrip("/")


def join_page():
    st.title("Choose an exam")
    open_sessions = [s for s in list_sessions(DB) if s["active"]]
    if not open_sessions:
        st.info("No exams are open yet. Ask your administrator to prepare a session.")
        return
    for session in open_sessions:
        st.subheader(session["title"])
        st.caption(session["label"])
        if st.button("Start this exam", key="join_" + session["code"]):
            st.query_params.clear()
            st.query_params["session"] = session["code"]
            st.rerun()


def admin_page():
    st.title("Exam Maker • Admin")
    if not ADMIN_PASSWORD:
        st.error("Web Admin is not configured. Add EXAM_ADMIN_PASSWORD in the Streamlit app's Settings → Secrets, then reboot the app. The Android Admin password is separate.")
        return
    if not st.session_state.get("admin_ok"):
        with st.form("admin_login"):
            password = st.text_input("Admin password", type="password")
            if st.form_submit_button("Sign in"):
                import hmac
                if hmac.compare_digest(password, ADMIN_PASSWORD):
                    st.session_state.admin_ok = True
                    st.rerun()
                else:
                    st.error("Incorrect password")
        return
    if st.button("Lock admin"):
        st.session_state.admin_ok = False
        st.rerun()
    st.warning("This deployment stores sessions and results in a local file. On Streamlit Community Cloud, that file can disappear after a restart or redeploy. Export results promptly; use a durable database before real exams.")
    st.subheader("Create session from prepared test set")
    st.write("Download the CSV, add one question per row, and enter A, B, C, or D under correct_option. The explanation column may be blank. Save as CSV UTF-8, then upload it here.")
    st.download_button("Download question template (.csv)", question_template(),
                       file_name="question-template.csv", mime="text/csv")
    sample = st.checkbox("Use bundled 50 question sample bank (practice only)")
    uploaded = st.file_uploader("Upload completed CSV or a bank JSON exported from Android", type=["csv", "json"])
    title = st.text_input("Test set name", placeholder="Aircraft Structures") if uploaded and uploaded.name.lower().endswith(".csv") and not sample else ""
    bank = None
    if sample:
        bank = json.loads((Path(__file__).resolve().parents[1] / "app/src/main/assets/questions.json").read_text(encoding="utf-8"))
        st.info("Practice sample selected: 50 general questions. It is not official CAAP content.")
    elif uploaded:
        try:
            data = uploaded.getvalue()
            if len(data) > 2_000_000:
                raise ValueError("Upload must be under 2 MB")
            bank = (bank_from_csv(data, title) if uploaded.name.lower().endswith(".csv")
                    else json.loads(data.decode("utf-8-sig")))
            if uploaded.name.lower().endswith(".json"):
                from core import validate_bank
                bank = validate_bank(bank)
            st.success(f"Ready: {len(bank['questions'])} questions in {bank['title']}")
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            st.error(str(exc))
    label = st.text_input("Session name", placeholder="Morning session, Group A")
    count = st.number_input("Random questions per examiner", min_value=1,
                            max_value=len(bank["questions"]) if bank else 500,
                            value=len(bank["questions"]) if bank else 1,
                            help="Each examiner receives this many questions selected independently from the uploaded bank.")
    if st.button("Create session", disabled=bank is None):
        try:
            code = create_session(DB, bank, label, int(count))
            st.success("Draft session created. Review it below, then select Publish session to make it available to examiners.")
            st.session_state.managed_session = code
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            st.error(str(exc))
    sessions = list_sessions(DB)
    if sessions:
        codes = [s["code"] for s in sessions]
        if st.session_state.get("managed_session") not in codes:
            st.session_state.managed_session = codes[0]
        selected_code = st.selectbox("Manage session", codes, key="managed_session",
                                     format_func=lambda code: next(s["label"] + " • " + s["title"] for s in sessions if s["code"] == code))
        session = next(s for s in sessions if s["code"] == selected_code)
        st.caption(f"{session['question_count'] or 'All'} random questions per examiner • {session['completed']} completed • {'published' if session['active'] else 'draft / closed'}")
        if st.button("Close session" if session["active"] else "Publish session", key="toggle_" + session["code"], type="primary" if not session["active"] else "secondary"):
            set_session_active(DB, session["code"], not session["active"])
            st.rerun()
        base_url = session_base_url()
        if base_url and session["active"]:
            link = base_url + "/?" + urlencode({"session": session["code"]})
            st.subheader("Examiner QR code")
            st.code(link)
            st.image(qr_png(link), caption="Scan with an Android phone or desktop browser", width=240)
            if not PUBLIC_URL:
                st.caption("Link uses this browser's address. Set EXAM_PUBLIC_URL if examiners need a different public address.")
        elif not base_url:
            st.warning("Unable to determine this app's URL. Set EXAM_PUBLIC_URL to display the session QR code.")
        else:
            st.info("Publish this session to display its examiner QR code and list it on the start page.")
        if st.checkbox("Prepare this session's Excel export"):
            rows = results(DB, session["code"])
            st.download_button("Export session results (.xlsx)", export_xlsx(rows), file_name="results-" + session["code"] + ".xlsx", mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    if st.checkbox("Prepare all-results Excel export"):
        st.download_button("Export all results (.xlsx)", export_xlsx(results(DB)), file_name="exam-results-all.xlsx", mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")


def candidate_page(code):
    session = get_session(DB, code)
    if not session:
        st.error("This exam link is unavailable.")
        return
    st.title(session["title"])
    st.caption(session["label"])
    attempt_id = st.query_params.get("attempt", "")
    view = attempt_view(DB, attempt_id) if attempt_id else None
    if view and view["session_code"] != code:
        view = None
    if not view:
        if not session["active"]:
            st.info("This session is closed to new examiners.")
            return
        with st.form("join"):
            name = st.text_input("Examiner name", max_chars=200)
            identifier = st.text_input("Examiner ID", max_chars=200)
            if st.form_submit_button("Start exam"):
                try:
                    attempt_id = start_attempt(DB, code, name, identifier)
                    st.query_params["attempt"] = attempt_id
                    st.rerun()
                except ValueError as exc:
                    st.error(str(exc))
        return
    if view["completed"]:
        st.markdown("<div style='text-align:center;padding:50px 0 20px'><h2>Exam complete</h2></div>", unsafe_allow_html=True)
        st.metric("Final score", f"{view['score']} / {view['total']}")
        st.caption("Your result has been saved. The administrator can export it.")
        return
    st.caption(f"Question {view['index'] + 1} of {view['total']}")
    st.metric("Current score", f"{view['score']} / {view['total']}")
    if st.session_state.get("just_answered") == attempt_id:
        st.success("Answer submitted. Your current score is shown above.")
        if st.button("Next question"):
            st.session_state.just_answered = None
            st.rerun()
        return
    question = view["question"]
    st.subheader(question["prompt"])
    with st.form("question_" + str(view["index"])):
        selected = st.radio("Select one answer", range(4), format_func=lambda i: "ABCD"[i] + "  " + question["options"][i], index=None)
        if st.form_submit_button("Submit choice"):
            if selected is None:
                st.warning("Choose an option first.")
            else:
                try:
                    next_view = submit_answer(DB, attempt_id, view["index"], selected)
                    st.session_state.just_answered = None if next_view["completed"] else attempt_id
                    st.rerun()
                except ValueError as exc:
                    st.error(str(exc))

if st.query_params.get("admin") == "1":
    admin_page()
elif st.query_params.get("session"):
    candidate_page(st.query_params["session"])
elif st.query_params.get("join") == "1":
    join_page()
else:
    st.title("Exam Maker")
    st.write("Scan to open the exam start page on your phone.")
    base_url = session_base_url()
    if base_url:
        join_url = base_url + "/?join=1"
        st.image(qr_png(join_url), caption="Exam Maker • start page", width=240)
        st.code(join_url)
        if st.button("Continue on this device"):
            st.query_params["join"] = "1"
            st.rerun()
    else:
        st.warning("Unable to determine this app's URL. Set EXAM_PUBLIC_URL to display the QR code.")
    if st.button("Open Admin", type="primary"):
        st.query_params["admin"] = "1"
        st.rerun()

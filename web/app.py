"""Run with: streamlit run web/app.py --server.address 0.0.0.0"""
import io
import json
import os
from urllib.parse import urlencode

import qrcode
import streamlit as st
from core import (initialize, create_session, list_sessions, get_session, set_session_active,
                  start_attempt, attempt_view, submit_answer, results, export_xlsx)

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


def admin_page():
    st.title("Exam Maker • Admin")
    if not ADMIN_PASSWORD:
        st.error("Set EXAM_ADMIN_PASSWORD on the server before using Admin.")
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
    st.subheader("Create session from prepared test set")
    uploaded = st.file_uploader("Upload a bank JSON exported from the Android admin", type=["json"])
    label = st.text_input("Session name", placeholder="Morning session, Group A")
    if st.button("Create session"):
        try:
            if uploaded is None or len(uploaded.getvalue()) > 2_000_000:
                raise ValueError("Choose a bank JSON under 2 MB")
            code = create_session(DB, json.loads(uploaded.getvalue()), label)
            st.success("Session created")
            st.session_state.created_code = code
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            st.error(str(exc))
    if not PUBLIC_URL:
        st.warning("Set EXAM_PUBLIC_URL to the reachable HTTPS or LAN URL before sharing QR links.")
    sessions = list_sessions(DB)
    if sessions:
        selected_code = st.selectbox("Manage session", [s["code"] for s in sessions],
                                     format_func=lambda code: next(s["label"] + " • " + s["title"] for s in sessions if s["code"] == code))
        session = next(s for s in sessions if s["code"] == selected_code)
        st.caption(f"{session['completed']} completed • {'open' if session['active'] else 'closed'}")
        if PUBLIC_URL:
            link = PUBLIC_URL + "/?" + urlencode({"session": session["code"]})
            st.code(link)
            st.image(qr_png(link), caption="Scan with an Android phone or desktop browser", width=240)
        if st.button("Close session" if session["active"] else "Reopen session", key="toggle_" + session["code"]):
            set_session_active(DB, session["code"], not session["active"])
            st.rerun()
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
else:
    st.title("Exam Maker")
    st.info("Open a session QR link to start an exam. Administrators: add ?admin=1 to the portal URL.")

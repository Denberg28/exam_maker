"""Persistent exam sessions for the Streamlit portal. No Streamlit dependency here."""
import io
import csv
import json
import secrets
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path
from random import SystemRandom
from threading import Lock

from openpyxl import Workbook

_random = SystemRandom()
_write_lock = Lock()
CSV_HEADER = ["question", "option_a", "option_b", "option_c", "option_d", "correct_option", "explanation"]


def question_template():
    """Same seven-column CSV contract as the Android admin."""
    out = io.StringIO(newline="")
    csv.writer(out).writerow(CSV_HEADER)
    return out.getvalue().encode("utf-8-sig")


def sample_bank_csv(payload):
    """Export the bundled practice bank using the same importable CSV format."""
    bank = validate_bank(payload)
    out = io.StringIO(newline="")
    writer = csv.writer(out)
    writer.writerow(CSV_HEADER)
    for q in bank["questions"]:
        writer.writerow([q["prompt"], *q["options"], "ABCD"[q["correct"]], q.get("explanation", "")])
    return out.getvalue().encode("utf-8-sig")


def bank_from_csv(data, title):
    if len(data) > 2_000_000:
        raise ValueError("Template exceeds 2 MB")
    try:
        content = data.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise ValueError("Save the template as UTF-8 CSV") from exc
    try:
        rows = list(csv.reader(io.StringIO(content, newline=""), strict=True))
    except csv.Error as exc:
        raise ValueError(f"Invalid CSV: {exc}") from exc
    if not rows or rows[0] != CSV_HEADER:
        raise ValueError("Incorrect CSV headings. Download a fresh template.")
    questions, prompts = [], set()
    for row_number, row in enumerate(rows[1:], 2):
        if not any(cell.strip() for cell in row):
            continue
        if len(row) != 7:
            raise ValueError(f"Row {row_number}: expected seven columns")
        prompt, *rest = [cell.strip() for cell in row]
        options, answer, explanation = rest[:4], rest[4].upper(), rest[5]
        if (not prompt or len(prompt) > 2000 or any(not o or len(o) > 500 for o in options)
                or len(set(options)) != 4 or answer not in "ABCD" or len(answer) != 1
                or len(explanation) > 2000):
            raise ValueError(f"Row {row_number}: enter a question, four distinct options, and correct_option A-D")
        if prompt.casefold() in prompts:
            raise ValueError(f"Row {row_number}: duplicate question")
        prompts.add(prompt.casefold())
        questions.append({"id": str(len(questions) + 1), "prompt": prompt,
                          "options": options, "correct": "ABCD".index(answer)})
        if len(questions) > 500:
            raise ValueError("Template exceeds 500 questions")
    return validate_bank({"schema": 1, "title": title, "questions": questions})


def utc_now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def connection(path):
    db = sqlite3.connect(str(path), timeout=15, isolation_level=None)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA busy_timeout=15000")
    db.execute("PRAGMA foreign_keys=ON")
    return db


def initialize(path):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with connection(path) as db:
        db.execute("PRAGMA journal_mode=WAL")
        db.executescript("""
        CREATE TABLE IF NOT EXISTS sessions (
            code TEXT PRIMARY KEY, label TEXT NOT NULL, title TEXT NOT NULL,
            bank_json TEXT NOT NULL, created_at TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE IF NOT EXISTS attempts (
            id TEXT PRIMARY KEY, session_code TEXT NOT NULL REFERENCES sessions(code),
            examiner_name TEXT NOT NULL, examiner_id TEXT NOT NULL,
            ordered_json TEXT NOT NULL, answers_json TEXT NOT NULL,
            created_at TEXT NOT NULL, completed_at TEXT
        );
        CREATE INDEX IF NOT EXISTS idx_attempt_session ON attempts(session_code);
        """)
        if "question_count" not in {row[1] for row in db.execute("PRAGMA table_info(sessions)")}:
            db.execute("ALTER TABLE sessions ADD COLUMN question_count INTEGER")


def validate_bank(payload):
    if not isinstance(payload, dict) or payload.get("schema") != 1:
        raise ValueError("Unsupported bank schema")
    title = payload.get("title")
    questions = payload.get("questions")
    if not isinstance(title, str) or not title.strip() or len(title) > 200:
        raise ValueError("A test set title is required")
    if not isinstance(questions, list) or not 1 <= len(questions) <= 500:
        raise ValueError("A set needs 1 to 500 questions")
    ids = set()
    for q in questions:
        if not isinstance(q, dict):
            raise ValueError("Invalid question")
        identifier, prompt, options, correct = (q.get(k) for k in ("id", "prompt", "options", "correct"))
        if not isinstance(identifier, str) or not identifier or identifier in ids:
            raise ValueError("Question IDs must be unique")
        ids.add(identifier)
        if not isinstance(prompt, str) or not prompt.strip() or len(prompt) > 2000:
            raise ValueError("Invalid question text")
        if not isinstance(options, list) or len(options) != 4 or any(not isinstance(o, str) or not o.strip() or len(o) > 500 for o in options):
            raise ValueError("Every question needs four options")
        if len(set(o.strip() for o in options)) != 4 or type(correct) is not int or not 0 <= correct < 4:
            raise ValueError("Invalid options or correct index")
    return {"schema": 1, "title": title.strip(), "questions": questions}


def create_session(path, payload, label, question_count=None):
    bank = validate_bank(payload)
    if question_count is None:
        question_count = len(bank["questions"])
    if type(question_count) is not int or not 1 <= question_count <= len(bank["questions"]):
        raise ValueError("Question count must be between 1 and the bank size")
    label = label.strip()
    if not label or len(label) > 200:
        raise ValueError("Session name is required (max 200 characters)")
    code = secrets.token_urlsafe(18)
    with _write_lock, connection(path) as db:
        db.execute("INSERT INTO sessions (code,label,title,bank_json,created_at,active,question_count) VALUES (?,?,?,?,?,0,?)", (code, label, bank["title"], json.dumps(bank, ensure_ascii=False), utc_now(), question_count))
    return code


def list_sessions(path):
    with connection(path) as db:
        return [dict(row) for row in db.execute("SELECT code,label,title,created_at,active,question_count,(SELECT COUNT(*) FROM attempts a WHERE a.session_code=s.code AND a.completed_at IS NOT NULL) completed FROM sessions s ORDER BY created_at DESC")]


def get_session(path, code):
    with connection(path) as db:
        row = db.execute("SELECT code,label,title,active FROM sessions WHERE code=?", (code,)).fetchone()
        return dict(row) if row else None


def set_session_active(path, code, active):
    with _write_lock, connection(path) as db:
        db.execute("UPDATE sessions SET active=? WHERE code=?", (int(active), code))


def start_attempt(path, code, name, identifier):
    name, identifier = name.strip(), identifier.strip()
    if not name or not identifier or len(name) > 200 or len(identifier) > 200:
        raise ValueError("Enter examiner name and ID (max 200 characters)")
    with _write_lock, connection(path) as db:
        db.execute("BEGIN IMMEDIATE")
        row = db.execute("SELECT bank_json,question_count FROM sessions WHERE code=? AND active=1", (code,)).fetchone()
        if row is None:
            db.rollback()
            raise ValueError("Session is closed or unavailable")
        questions = json.loads(row[0])["questions"]
        shuffled = list(questions)
        _random.shuffle(shuffled)
        shuffled = shuffled[:row["question_count"] or len(shuffled)]
        ordered = []
        for q in shuffled:
            indices = list(range(4))
            _random.shuffle(indices)
            ordered.append({"id": q["id"], "prompt": q["prompt"], "options": [q["options"][i] for i in indices], "correct": indices.index(q["correct"])})
        attempt_id = str(uuid.uuid4())
        db.execute("INSERT INTO attempts VALUES (?,?,?,?,?,?,?,NULL)", (attempt_id, code, name, identifier, json.dumps(ordered, ensure_ascii=False), json.dumps([-1] * len(ordered)), utc_now()))
        db.commit()
    return attempt_id


def attempt_view(path, attempt_id):
    with connection(path) as db:
        row = db.execute("SELECT session_code,examiner_name,ordered_json,answers_json,completed_at FROM attempts WHERE id=?", (attempt_id,)).fetchone()
        if row is None:
            return None
        ordered, answers = json.loads(row["ordered_json"]), json.loads(row["answers_json"])
        score = sum(a < 0 or a == q["correct"] for a, q in zip(answers, ordered))
        index = next((i for i, a in enumerate(answers) if a < 0), len(answers))
        q = ordered[index] if index < len(ordered) else None
        return {"session_code": row["session_code"], "examiner_name": row["examiner_name"], "score": score, "total": len(ordered), "index": index, "completed": bool(row["completed_at"]), "question": {"prompt": q["prompt"], "options": q["options"]} if q else None}


def submit_answer(path, attempt_id, index, selected):
    if type(index) is not int or type(selected) is not int or not 0 <= selected < 4:
        raise ValueError("Invalid selection")
    with _write_lock, connection(path) as db:
        db.execute("BEGIN IMMEDIATE")
        row = db.execute("SELECT ordered_json,answers_json,completed_at FROM attempts WHERE id=?", (attempt_id,)).fetchone()
        if row is None or row["completed_at"]:
            db.rollback()
            raise ValueError("Attempt unavailable or already complete")
        ordered, answers = json.loads(row["ordered_json"]), json.loads(row["answers_json"])
        first = next((i for i, a in enumerate(answers) if a < 0), len(answers))
        if first != index:
            db.rollback()
            raise ValueError("This question was already answered")
        answers[index] = selected
        completed = utc_now() if index + 1 == len(ordered) else None
        db.execute("UPDATE attempts SET answers_json=?,completed_at=? WHERE id=?", (json.dumps(answers), completed, attempt_id))
        db.commit()
    return attempt_view(path, attempt_id)


def results(path, code=None):
    sql = "SELECT s.label,a.examiner_name,a.examiner_id,a.created_at,a.completed_at,a.ordered_json,a.answers_json FROM attempts a JOIN sessions s ON s.code=a.session_code WHERE a.completed_at IS NOT NULL"
    args = ()
    if code:
        sql += " AND a.session_code=?"
        args = (code,)
    with connection(path) as db:
        rows = db.execute(sql + " ORDER BY a.completed_at DESC", args).fetchall()
    output = []
    for r in rows:
        ordered, answers = json.loads(r["ordered_json"]), json.loads(r["answers_json"])
        score = sum(a == q["correct"] for a, q in zip(answers, ordered))
        output.append((r["completed_at"], r["examiner_name"], r["examiner_id"], r["label"], score, len(answers), round(score * 100 / len(answers))))
    return output


def export_xlsx(rows):
    def safe(value):
        if isinstance(value, str) and value.startswith(("=", "+", "-", "@")):
            return "'" + value
        return value
    wb = Workbook()
    ws = wb.active
    ws.title = "Results"
    ws.append(["Completed UTC", "Examiner name", "Examiner ID", "Session", "Score", "Maximum", "Percent"])
    for row in rows:
        ws.append([safe(v) for v in row])
    for col, width in {"A": 28, "B": 25, "C": 22, "D": 25, "E": 12, "F": 12, "G": 12}.items():
        ws.column_dimensions[col].width = width
    out = io.BytesIO()
    wb.save(out)
    return out.getvalue()

# Project continuity
- Android Java, offline MVP; applicationId com.denberg28.exammaker, version 0.2.0 (code 2).
- Bank is bundled JSON at app/src/main/assets/questions.json; sample general knowledge only, not CAAP content.
- Validate bank with python3 tools/validate_bank.py before changing or releasing it.
- Correct options are original indices. ExamEngine shuffles questions and visible options using a saved seed; persistence stores the seed and visible selection indices. Preserve bank order and content under a bankId, or increment bankId to invalidate a saved attempt.
- Exactly four choices. One choice locks immediately; no correct answer is revealed. Score begins at the exam length and drops for each wrong answer; final result after all questions. Starting a new attempt replaces saved state.
- No network, runtime permissions, analytics, credentials, or release signing. Debug APK is private testing only.
- CI builds an unsigned-for-distribution debug APK. Production signing and publication remain a separate milestone.

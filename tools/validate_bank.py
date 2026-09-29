import json
from pathlib import Path
p = Path('app/src/main/assets/questions.json')
bank = json.loads(p.read_text())
assert bank['schema'] == 1 and bank['bankId'].strip() and bank['title'].strip()
assert len(bank['questions']) >= 1
ids = set()
for q in bank['questions']:
    assert q['id'].strip() and q['id'] not in ids
    ids.add(q['id'])
    assert q['prompt'].strip() and q['explanation'].strip()
    assert len(q['options']) == 4
    assert all(isinstance(x, str) and x.strip() for x in q['options'])
    assert len(set(q['options'])) == len(q['options'])
    assert type(q['correct']) is int and 0 <= q['correct'] < len(q['options'])
print(f"Validated {len(ids)} questions in {bank['bankId']}")

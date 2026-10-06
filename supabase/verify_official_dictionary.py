import json
import sqlite3
import sys
from pathlib import Path

folder = Path(sys.argv[1])
db = sqlite3.connect("file:" + (folder / "official_dictionary.sqlite").as_posix() + "?mode=ro", uri=True)
assert db.execute("pragma integrity_check").fetchone()[0] == "ok"
assert not db.execute("pragma foreign_key_check").fetchall()
assert db.execute("select count(*) from examples where translation is not null").fetchone()[0] == 0
samples = db.execute("""
select e.word,s.chinese_lemma,s.chinese_definition
from entries e join senses s on s.entry_id=e.id
where e.level='초급' and s.chinese_definition<>'' limit 4
""").fetchall()
print(json.dumps(samples, ensure_ascii=False))
missing = db.execute("select count(*) from senses where chinese_lemma='' and chinese_definition=''").fetchone()[0]
print("Unique senses without Chinese:", missing)
files = sorted(folder.glob("import_beginner_*.sql"))
assert len(files) == 13
for file in files:
    sql = file.read_text(encoding="utf-8")
    assert "on conflict (id) do nothing" in sql
    assert "false," in sql
    assert "CC BY-SA 2.0 KR" in sql
# One runnable bundle includes the non-destructive metadata migration.
schema = (Path(__file__).parent / "003_dictionary_provenance.sql").read_text(encoding="utf-8")
schema += "\n" + (Path(__file__).parent / "004_difficulty_tags.sql").read_text(encoding="utf-8")
(folder / "IMPORT_BEGINNER_ALL.sql").write_text(
    schema + "\n" + "\n".join(f.read_text(encoding="utf-8") for f in files), encoding="utf-8")
print("Verified database, foreign keys, source metadata and 13 draft-only batches.")
db.close()

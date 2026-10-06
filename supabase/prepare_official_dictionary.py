"""Normalize an official JSON ZIP; never uses credentials or connects to a server."""
import argparse
import hashlib
import html
import json
import sqlite3
import zipfile
from pathlib import Path

SOURCE = "韩国国立国语院《韩国语基础词典》"
LICENSE = "CC BY-SA 2.0 KR"
ATTRIBUTION = "한국어기초사전 - 국립국어원 제공"
LEVELS = {"초급": "beginner", "중급": "intermediate", "고급": "advanced"}

def difficulty_tags(level, version):
    return [{"framework": "krdict", "level": LEVELS[level], "source": SOURCE,
             "confidence": 1.0, "version": version}] if level in LEVELS else []

def items(value):
    if value is None:
        return []
    return value if isinstance(value, list) else [value]

def features(obj, name):
    return [html.unescape(str(x.get("val", ""))).strip()
            for part in items(obj) for x in items(part.get("feat"))
            if x.get("att") == name]

def first(obj, name):
    return next(iter(features(obj, name)), "")

def quote(value):
    return "'" + str(value).replace("'", "''") + "'"

def dictionary_senses(db, level=None):
    return db.execute("""
      select e.id,e.word,e.part_of_speech,e.pronunciation,e.source_url,
             s.sense_id,s.chinese_lemma,s.chinese_definition,e.level
      from entries e join senses s on s.entry_id=e.id
      where (? is null or e.level=?) and (s.chinese_lemma<>'' or s.chinese_definition<>'')
      order by cast(e.id as integer), cast(s.sense_id as integer)
    """, (level, level)).fetchall()

def beginner_senses(db):
    return dictionary_senses(db, "초급")

def chinese_meaning(lemma, definition):
    return "；".join(dict.fromkeys(x for x in (lemma, definition) if x))

def prepare(archive_path, output, level="초급", version=None):
    output.mkdir(parents=True, exist_ok=True)
    db_path = output / "official_dictionary.sqlite"
    # Refuse to overwrite previously generated artifacts.
    if db_path.exists():
        raise ValueError("Output database exists; choose a new output directory.")
    db = sqlite3.connect(db_path)
    db.executescript("""
    create table entries (
      id text primary key, word text not null, part_of_speech text,
      level text, pronunciation text, homonym_number text, source_url text
    );
    create table senses (
      id text primary key, entry_id text not null references entries(id),
      sense_id text not null, korean_definition text, chinese_lemma text,
      chinese_definition text, unique(entry_id,sense_id)
    );
    create table examples (
      id text primary key, sense_id text not null references senses(id),
      kind text, sentence text not null,
      translation text, translation_source text
    );
    create table metadata (key text primary key, value text);
    """)
    counts = dict(files=0, raw_entries=0, missing_chinese_senses=0,
                  duplicate_entries=0, duplicate_senses=0, duplicate_examples=0)
    with zipfile.ZipFile(archive_path) as archive:
        for member in archive.infolist():
            if not member.filename.endswith(".json"):
                continue
            if member.file_size > 200_000_000:
                raise ValueError("Unexpectedly large archive member.")
            with archive.open(member) as stream:
                document = json.load(stream)
            counts["files"] += 1
            lexical = document["LexicalResource"]["Lexicon"]["LexicalEntry"]
            for entry in items(lexical):
                counts["raw_entries"] += 1
                eid = str(entry["val"])
                word = first(entry["Lemma"], "writtenForm")
                if not word:
                    raise ValueError("Missing headword for " + eid)
                forms = items(entry.get("WordForm"))
                pron = " / ".join(dict.fromkeys(p for f in forms
                                              for p in features(f, "pronunciation")))
                url = "https://krdict.korean.go.kr/kor/dicSearch/SearchView?ParaWordNo=" + eid
                before = db.total_changes
                db.execute("insert or ignore into entries values(?,?,?,?,?,?,?)",
                           (eid, word, first(entry,"partOfSpeech"),
                            first(entry,"vocabularyLevel"),pron,
                            first(entry,"homonym_number"),url))
                counts["duplicate_entries"] += int(db.total_changes == before)
                for sense in items(entry.get("Sense")):
                    sid = str(sense["val"])
                    uid = eid + ":" + sid
                    equivalent = next((x for x in items(sense.get("Equivalent"))
                                       if first(x,"language") == "중국어"), {})
                    lemma = first(equivalent,"lemma")
                    definition = first(equivalent,"definition")
                    if not (lemma or definition):
                        counts["missing_chinese_senses"] += 1
                    before = db.total_changes
                    db.execute("insert or ignore into senses values(?,?,?,?,?,?)",
                               (uid,eid,sid,first(sense,"definition"),lemma,definition))
                    counts["duplicate_senses"] += int(db.total_changes == before)
                    for example in items(sense.get("SenseExample")):
                        kind = first(example,"type")
                        for sentence in features(example,"example"):
                            if not sentence:
                                continue
                            digest = hashlib.sha256((uid+"|"+kind+"|"+sentence).encode()).hexdigest()[:24]
                            before = db.total_changes
                            db.execute("insert or ignore into examples values(?,?,?,?,?,?)",
                                       (uid+":"+digest,uid,kind,sentence,None,None))
                            counts["duplicate_examples"] += int(db.total_changes == before)
            db.commit()
            print("Processed " + member.filename, flush=True)
            del document, lexical
    for table in ("entries","senses","examples"):
        counts[table] = db.execute("select count(*) from " + table).fetchone()[0]
    counts["examples_with_chinese_translation"] = 0
    counts["archive_sha256"] = hashlib.file_digest(open(archive_path,"rb"),"sha256").hexdigest()
    for key,value in {"source":SOURCE,"license":LICENSE,"attribution":ATTRIBUTION,
                      "archive":Path(archive_path).name}.items():
        db.execute("insert into metadata values(?,?)",(key,value))
    db.commit()
    assert db.execute("pragma integrity_check").fetchone()[0] == "ok"
    # Beginner senses with genuine Chinese definitions; unpublished staging only.
    # Separate IDs preserve polysemy and do not overwrite ko-01 ... ko-08.
    version = version or Path(archive_path).stem
    rows = dictionary_senses(db, None if level=="all" else level)
    counts["beginner_senses_prepared"] = sum(row[8]=="초급" for row in rows)
    counts["selected_senses_prepared"] = len(rows)
    columns = ("id","language_code","word","meaning","pronunciation","part_of_speech",
               "published","source_name","source_url","source_entry_id","source_sense_id",
               "license","attribution","difficulty_tags")
    for offset in range(0,len(rows),500):
        batch = rows[offset:offset+500]
        values = []
        for eid,word,pos,pron,url,sid,lemma,definition,word_level in batch:
            meaning = chinese_meaning(lemma, definition)
            fields = ["krdict-"+eid+"-s"+sid,"ko",word,meaning,pron,pos,
                      SOURCE,url,eid,sid,LICENSE,ATTRIBUTION]
            encoded = [quote(x) for x in fields]
            encoded.insert(6,"false")
            encoded.append(quote(json.dumps(difficulty_tags(word_level,version),ensure_ascii=False))+"::jsonb")
            values.append("(" + ",".join(encoded) + ")")
        sql = ("-- Official beginner senses. DRAFT: no public publication.\n"
               "-- Execute 003_dictionary_provenance.sql and 004_difficulty_tags.sql first.\n"
               "begin;\ninsert into public.vocabulary ("+",".join(columns)+") values\n"
               +",\n".join(values)+"\non conflict (id) do nothing;\ncommit;\n"
               "select count(*) as imported_official_senses from public.vocabulary "
               "where source_name="+quote(SOURCE)+";\n")
        prefix="import_beginner" if level=="초급" else "import_dictionary"
        (output / (prefix+"_%02d.sql" % (offset//500+1))).write_text(sql,encoding="utf-8")
    (output/"report.json").write_text(json.dumps(counts,ensure_ascii=False,indent=2),encoding="utf-8")
    db.close()
    print(json.dumps(counts,ensure_ascii=False),flush=True)

if __name__ == "__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("archive")
    parser.add_argument("output")
    parser.add_argument("--level", choices=["초급","중급","고급","all"], default="초급")
    parser.add_argument("--version")
    args=parser.parse_args()
    prepare(args.archive,Path(args.output),args.level,args.version)

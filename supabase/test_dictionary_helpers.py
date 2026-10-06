"""Small offline regression check for both dictionary export paths."""
import sqlite3
import json
import tempfile
import zipfile
from pathlib import Path
from prepare_official_dictionary import beginner_senses, dictionary_senses, chinese_meaning, difficulty_tags, prepare
from export_android_dictionary import export
from export_android_dictionary import beginner_senses as export_senses

def test_shared_dictionary_helpers():
    with sqlite3.connect(":memory:") as db:
        db.executescript("""
            create table entries(id,word,part_of_speech,pronunciation,source_url,level);
            create table senses(entry_id,sense_id,chinese_lemma,chinese_definition);
            insert into entries values('10','학교','명사','','','초급'),('2','친구','명사','','','초급'),
                ('3','고급','','','','고급');
            insert into senses values('10','2','学校','学校'),('10','10','','校园'),
                ('2','1','朋友','友人'),('2','2','',''),('3','1','高级','');
        """)
        rows = beginner_senses(db)
        assert [(row[0], row[5]) for row in rows] == [('2','1'),('10','2'),('10','10')]
        assert export_senses is beginner_senses
        assert [chinese_meaning(row[6], row[7]) for row in rows] == ['朋友；友人','学校','校园']
        assert chinese_meaning('', '') == ''
        assert len(dictionary_senses(db)) == 4
        assert difficulty_tags("초급","test")[0]["level"] == "beginner"
        assert difficulty_tags("중급","test")[0]["level"] == "intermediate"
        assert difficulty_tags("고급","test")[0]["level"] == "advanced"
        assert difficulty_tags("","test") == []
        assert difficulty_tags("TOPIK 1","test") == []

def test_import_export_levels():
    with tempfile.TemporaryDirectory(prefix="lingua-difficulty-") as temp:
        root=Path(temp)
        def feature(name,value):
            return {"att":name,"val":value}
        entries=[]
        for i,level in enumerate(["초급","중급","고급","없음"],1):
            entries.append({"val":str(i),"Lemma":{"feat":[feature("writtenForm","word"+str(i))]},
                "feat":[feature("vocabularyLevel",level)],
                "Sense":{"val":"1","Equivalent":{"feat":[feature("language","중국어"),feature("lemma","meaning"+str(i))]}}})
        archive=root/"fixture.zip"
        with zipfile.ZipFile(archive,"w") as z:
            z.writestr("fixture.json",json.dumps({"LexicalResource":{"Lexicon":{"LexicalEntry":entries}}},ensure_ascii=False))
        prepare(archive,root/"prepared",level="all",version="fixture-v1")
        export(root/"prepared"/"official_dictionary.sqlite",root/"content.json",level="all",version="fixture-v1",tags_sql=root/"levels.sql")
        rows=json.loads((root/"content.json").read_text(encoding="utf-8"))
        assert [r["difficulty_tags"][0]["level"] if r["difficulty_tags"] else None for r in rows] == ["beginner","intermediate","advanced",None]
        assert all(r["difficulty_tags"][0]["version"]=="fixture-v1" for r in rows[:3])
        sql=(root/"prepared"/"import_dictionary_01.sql").read_text(encoding="utf-8")
        assert "difficulty_tags" in sql and "::jsonb" in sql and "false," in sql and "topik" not in sql.lower()

if __name__ == "__main__":
    test_shared_dictionary_helpers()
    test_import_export_levels()
    print("Shared dictionary query and meaning checks passed.")

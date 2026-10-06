"""Generate an offline Android content asset from the verified dictionary (no network)."""
import argparse
import json
import sqlite3
from pathlib import Path
from prepare_official_dictionary import beginner_senses, dictionary_senses, chinese_meaning, difficulty_tags, SOURCE, LICENSE, ATTRIBUTION

def export(database, target, level="초급", version="official-json-20260919", tags_sql=None):
    db = sqlite3.connect(f"file:{Path(database).resolve().as_posix()}?mode=ro", uri=True)
    rows = dictionary_senses(db, None if level=="all" else level)
    content = [dict(id=f"krdict-{eid}-s{sid}", language_code="ko", word=word,
        meaning=chinese_meaning(lemma, definition),
        pronunciation=pron, part_of_speech=pos, source_entry_id=eid, source_sense_id=sid,
        source_name=SOURCE, source_url=url, license=LICENSE, attribution=ATTRIBUTION,
        difficulty_tags=difficulty_tags(word_level,version))
        for eid,word,pos,pron,url,sid,lemma,definition,word_level in rows]
    assert content and len({x['id'] for x in content}) == len(content)
    Path(target).write_text(json.dumps(content, ensure_ascii=False, separators=(',', ':')), encoding="utf-8")
    if tags_sql:
        from prepare_official_dictionary import quote
        # Only fill absent dictionary labels on existing senses, preserving any other systems.
        changes = []
        for word in content:
            if word["difficulty_tags"]:
                tags = quote(json.dumps(word["difficulty_tags"],ensure_ascii=False))
                changes.append("update public.vocabulary set difficulty_tags=difficulty_tags || "+tags+
                    "::jsonb where id="+quote(word["id"])+
                    " and not exists (select 1 from jsonb_array_elements(difficulty_tags) t where t->>'framework'='krdict');")
        Path(tags_sql).write_text("-- Run 004_difficulty_tags.sql first. No publication or progress changes.\nbegin;\n"+
            "\n".join(changes)+"\ncommit;\n",encoding="utf-8")
    db.close()
    print(f"Exported {len(content)} official senses, without inventing translated examples.")

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('database'); parser.add_argument('target')
    parser.add_argument('--level',choices=["초급","중급","고급","all"],default="초급")
    parser.add_argument('--version',default="official-json-20260919")
    parser.add_argument('--tags-sql',help='Optional metadata-only SQL; generated, never executed')
    args = parser.parse_args(); export(args.database, args.target,args.level,args.version,args.tags_sql)

-- Add source metadata without changing existing IDs, content or permissions.
-- Official import should remain unpublished until reviewed.
begin;

alter table public.vocabulary
  add column if not exists source_name text,
  add column if not exists source_url text,
  add column if not exists source_entry_id text,
  add column if not exists source_sense_id text,
  add column if not exists license text,
  add column if not exists attribution text,
  add column if not exists imported_at timestamptz;

alter table public.example_sentences
  add column if not exists source_name text,
  add column if not exists source_url text,
  add column if not exists source_entry_id text,
  add column if not exists source_sense_id text,
  add column if not exists license text,
  add column if not exists attribution text,
  add column if not exists translation_source text,
  add column if not exists imported_at timestamptz;

create unique index if not exists vocabulary_source_sense_unique
  on public.vocabulary (source_name, source_entry_id, source_sense_id)
  where source_name is not null
    and source_entry_id is not null
    and source_sense_id is not null;

commit;

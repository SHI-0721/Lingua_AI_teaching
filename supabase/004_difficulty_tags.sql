-- Additive only. Run manually after confirmation; this file is NOT executed by the app.
-- difficulty_tags is public content metadata, never the user's enrollment/progress.
begin;
alter table public.vocabulary
  add column if not exists difficulty_tags jsonb not null default '[]'::jsonb;
-- Each sense may carry multiple systems: {framework,level,source,confidence,version}.
-- krdict beginner/intermediate/advanced are NOT TOPIK 1-6.
-- No existing word is assigned a level merely from its spelling or ID.
commit;

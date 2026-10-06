-- 现有本地词库导入：只插入缺失 ID，不覆盖云端已编辑内容。
begin;

insert into public.vocabulary
(id, language_code, word, meaning, pronunciation, part_of_speech, collocations, similar_words, antonyms, published)
values
('ko-01', 'ko', '안녕하세요', '你好', 'an-nyeong-ha-se-yo', '问候表达', ARRAY['안녕하세요, 선생님']::text[], ARRAY['안녕（非正式）']::text[], '{}'::text[], true),
('ko-02', 'ko', '감사합니다', '谢谢', 'gam-sa-ham-ni-da', '感谢表达', ARRAY['정말 감사합니다']::text[], ARRAY['고맙습니다']::text[], '{}'::text[], true),
('ko-03', 'ko', '학교', '学校', 'hak-kkyo', '名词', ARRAY['학교 생활']::text[], ARRAY['대학교（大学）']::text[], '{}'::text[], true),
('ko-04', 'ko', '친구', '朋友', 'chin-gu', '名词', ARRAY['좋은 친구']::text[], ARRAY['동료（同事）']::text[], '{}'::text[], true),
('ko-05', 'ko', '먹다', '吃', 'meok-tta', '动词', ARRAY['밥을 먹다']::text[], ARRAY['마시다（喝）']::text[], '{}'::text[], true),
('ko-06', 'ko', '가다', '去', 'ga-da', '动词', ARRAY['학교에 가다']::text[], '{}'::text[], ARRAY['오다（来）']::text[], true),
('ko-07', 'ko', '보다', '看', 'bo-da', '动词', ARRAY['영화를 보다']::text[], ARRAY['바라보다（望着）']::text[], '{}'::text[], true),
('ko-08', 'ko', '약속', '约定', 'yak-ssok', '名词', ARRAY['약속 시간']::text[], ARRAY['예약（预约）']::text[], '{}'::text[], true)
on conflict (id) do nothing;

insert into public.example_sentences
(id, vocabulary_id, language_code, sentence, translation, keywords, grammar_notes, published)
values
('ko-01-a', 'ko-01', 'ko', '안녕하세요, 저는 학생입니다.', '你好，我是学生。', '{"안녕하세요":"你好","학생":"学生"}'::jsonb, '저는：我的主题形式；입니다：正式礼貌陈述', true),
('ko-02-a', 'ko-02', 'ko', '도와주셔서 감사합니다.', '谢谢你的帮助。', '{"도와주다":"帮助","감사합니다":"谢谢"}'::jsonb, '-아/어서：表示原因', true),
('ko-03-a', 'ko-03', 'ko', '학교에 가요.', '我去学校。', '{"학교":"学校","가다":"去"}'::jsonb, '에：目的地助词；가요：가다 的礼貌形式', true),
('ko-03-b', 'ko-03', 'ko', '학교에서 공부해요.', '我在学校学习。', '{"학교":"学校","공부하다":"学习"}'::jsonb, '에서：动作发生的地点', true),
('ko-04-a', 'ko-04', 'ko', '오늘 친구를 만나요.', '我今天和朋友见面。', '{"오늘":"今天","친구":"朋友","만나다":"见面"}'::jsonb, '를：宾语助词；만나요：礼貌形式', true),
('ko-04-b', 'ko-04', 'ko', '친구와 함께 공부해요.', '我和朋友一起学习。', '{"친구":"朋友","함께":"一起","공부하다":"学习"}'::jsonb, '와：和', true),
('ko-05-a', 'ko-05', 'ko', '밥을 먹어요.', '我吃饭。', '{"밥":"饭","먹다":"吃"}'::jsonb, '을：宾语助词；먹어요：먹다 的礼貌形式', true),
('ko-05-b', 'ko-05', 'ko', '친구와 점심을 먹어요.', '我和朋友吃午饭。', '{"친구":"朋友","점심":"午饭","먹다":"吃"}'::jsonb, '와：和；을：宾语助词', true),
('ko-06-a', 'ko-06', 'ko', '집에 가요.', '我回家。', '{"집":"家","가다":"去"}'::jsonb, '에：目的地；主语常可省略', true),
('ko-06-b', 'ko-06', 'ko', '내일 학교에 가요.', '我明天去学校。', '{"내일":"明天","학교":"学校","가다":"去"}'::jsonb, '韩语常采用主语—宾语—谓语顺序', true),
('ko-07-a', 'ko-07', 'ko', '영화를 봐요.', '我看电影。', '{"영화":"电影","보다":"看"}'::jsonb, '를：宾语助词；봐요：보다 的缩约礼貌形式', true),
('ko-07-b', 'ko-07', 'ko', '친구와 영화를 봐요.', '我和朋友看电影。', '{"친구":"朋友","영화":"电影","보다":"看"}'::jsonb, '와：和；를：宾语助词', true),
('ko-08-a', 'ko-08', 'ko', '친구와 약속이 있어요.', '我和朋友有约。', '{"친구":"朋友","약속":"约定","있다":"有"}'::jsonb, '이：主语助词；있어요：存在或拥有', true),
('ko-08-b', 'ko-08', 'ko', '약속을 지켜 주세요.', '请遵守约定。', '{"약속":"约定","지키다":"遵守"}'::jsonb, '-아/어 주세요：请求别人做某事', true),
('ko-08-c', 'ko-08', 'ko', '내일 약속이 있어요.', '我明天有约。', '{"내일":"明天","약속":"约定","있다":"有"}'::jsonb, '이：主语助词；있어요：有', true)
on conflict (id) do nothing;

commit;

-- 导入后核对数量：首次导入预计 8 个单词、15 个例句。
select 'vocabulary' as table_name, count(*) as total
from public.vocabulary where language_code = 'ko'
union all
select 'example_sentences', count(*)
from public.example_sentences where language_code = 'ko';

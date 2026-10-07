-- 판을 만들 때 고른 학교 (없으면 설정 파일의 기본 학교). 시드와 함께 재현에 쓴다.
alter table runs add column school_id integer;

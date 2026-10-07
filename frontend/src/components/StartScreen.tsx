import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useCreateRun, useMeta, useSchools } from '../queries';
import { useUiStore } from '../store';
import Masthead from './Masthead';

export default function StartScreen() {
  const meta = useMeta().data;
  const seedText = useUiStore((s) => s.startSeed);
  const setSeedText = useUiStore((s) => s.setStartSeed);
  const schoolId = useUiStore((s) => s.startSchoolId);
  const setSchoolId = useUiStore((s) => s.setStartSchoolId);
  const create = useCreateRun();
  const queryClient = useQueryClient();

  const trimmed = seedText.trim();
  const parsed = trimmed === '' ? undefined : Number(trimmed);
  const invalid = parsed !== undefined && (!Number.isSafeInteger(parsed) || parsed < 0);
  const schools = useSchools(invalid ? undefined : parsed);
  const list = schools.data;

  // 목록이 바뀌어도 고른 학교 번호는 유지한다 (번호·유형·권역은 시드와 상관없이 같다)
  useEffect(() => {
    if (list && schoolId === null) setSchoolId(list.defaultSchoolId);
  }, [list, schoolId, setSchoolId]);

  const chosen = list?.types.flatMap((t) => t.schools.map((s) => ({ ...s, type: t }))).find((s) => s.id === schoolId);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!list || invalid) return;
    // 기본 학교면 학교를 지정하지 않은 것과 같게 보낸다
    create.mutate(
      { seed: list.seed, schoolId: schoolId === list.defaultSchoolId ? null : schoolId },
      // 다음에 시드를 비우고 시작하면 새 무작위 시드를 받는다
      { onSuccess: () => queryClient.removeQueries({ queryKey: ['schools', 'random'] }) },
    );
  };

  return (
    <>
      <Masthead />
      <main className="start">
        <section>
          <div className="eyebrow">First season</div>
          <h1>새벽 훈련부터,<br />마지막 휘슬까지.</h1>
          <p className="intro">
            새벽 운동, 교실에서의 하루, 그리고 주말의 경기.
            {meta && <><br />{meta.startLabel}부터 {meta.endLabel}까지 {meta.weeksPerYear}주 동안 한 명의 학생 선수를 키워 보세요.</>}
          </p>
          <div className="start-note">매일의 선택이 쌓여 한 시즌이 됩니다.</div>
        </section>
        <form className="start-form" onSubmit={submit}>
          <h2>1학년 시즌 시작</h2>
          <label htmlFor="seed">시즌 시드</label>
          <input id="seed" value={seedText} onChange={(e) => setSeedText(e.target.value)} placeholder="비우면 무작위" inputMode="numeric" />
          {invalid && <p className="error">시드는 0 이상의 정수여야 합니다.</p>}
          {list?.randomSeed && <p className="tiny">무작위 시드 {list.seed} (학교 이름은 시드로 정해집니다)</p>}

          <label htmlFor="school">소속 학교</label>
          <select id="school" value={schoolId ?? ''} disabled={!list} onChange={(e) => setSchoolId(Number(e.target.value))}>
            {list?.types.map((t) => (
              <optgroup key={t.key} label={`${t.name} · 전력 ${t.strength}`}>
                {t.schools.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} · {s.region}{s.id === list.defaultSchoolId ? ' (기본)' : ''}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
          {chosen && (
            <div className="school-pick">
              <b>{chosen.name}</b>
              {chosen.type.name} · 팀 전력 {chosen.type.strength} · {chosen.region}
            </div>
          )}

          {meta && (
            <p className="muted">
              포지션 {meta.position} / 유형 {meta.archetype}
              <br />주말리그 {meta.leagueMatches}경기와 대회 {meta.tournaments.length}개({meta.tournaments.join(' · ')})가 기다립니다.
            </p>
          )}
          <button className="primary" disabled={!list || invalid || create.isPending}>
            {create.isPending ? '시즌 준비 중…' : '운동부에 입부하기 →'}
          </button>
          {(schools.error || create.error) && <p className="error" role="alert">{(schools.error ?? create.error)?.message}</p>}
        </form>
      </main>
    </>
  );
}

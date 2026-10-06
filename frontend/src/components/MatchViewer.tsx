import { useEffect } from 'react';
import type { MatchRecord } from '../types';
import { useUiStore } from '../store';
import { f1, resultLabel, roleLabel } from '../format';

const STEP_MS = 650;

/** 경기 결과. 스코어와 평점은 서버가 확정한 값이고, 중계는 순서대로 한 줄씩 보여 준다. */
export default function MatchViewer({ match }: { match: MatchRecord }) {
  const playback = useUiStore((s) => s.playback)!;
  const advance = useUiStore((s) => s.advancePlayback);
  const skip = useUiStore((s) => s.skipPlayback);
  const clear = useUiStore((s) => s.clearPlayback);
  const total = match.timeline.length;
  const done = playback.skipped || playback.shown >= total;
  const shown = done ? total : playback.shown;

  useEffect(() => {
    if (done) return;
    const t = setTimeout(advance, STEP_MS);
    return () => clearTimeout(t);
  }, [done, playback.shown, advance]);

  const current = match.timeline[Math.max(0, shown - 1)];
  return (
    <div className="box match">
      <div className="row between">
        <h2>
          {match.competition === 'CUP' ? '춘계배' : '주말리그'} {match.roundLabel} · {match.home ? '홈' : '원정'} vs {match.opponentName}
        </h2>
        <div>
          {!done && <button onClick={skip}>건너뛰기</button>}{' '}
          <button className="link" onClick={clear}>닫기</button>
        </div>
      </div>
      <div className="scoreboard">
        {done ? `${match.ourScore} : ${match.theirScore}` : `${current?.ourScore ?? 0} : ${current?.theirScore ?? 0}`}
        {done && <span className="result"> {resultLabel(match.result, match.penaltyWin)}</span>}
      </div>
      <div className="muted small">
        {match.dateLabel} · 상대 전력 {match.opponentStrength} · 핵심 수비 {match.opponentDefender} · 출전: {roleLabel[match.role]} (출전 점수 {f1(match.selectionScore)})
      </div>
      <ol className="timeline">
        {match.timeline.slice(0, shown).map((e, i) => (
          <li key={i} className={e.kind}>
            <span className="minute">{e.minute}'</span> {e.text} <span className="muted">({e.ourScore}-{e.theirScore})</span>
          </li>
        ))}
      </ol>
      {done && (
        <div className="match-footer">
          {match.rating !== null ? (
            <>
              평점 <b>{f1(match.rating)}</b> · 골 {match.playerGoals} · 도움 {match.playerAssists}
              {match.pressGoals > 0 && ` · 압박 득점 ${match.pressGoals}`} · 평판 +{f1(match.reputationGained)}
              {match.passiveGrowth && ` · ${match.passiveGrowth} +0.5`}
            </>
          ) : (
            <span className="muted">{roleLabel[match.role]} — 평점 없음</span>
          )}
          <div className="muted small">
            팀 득점 {match.teamGoals} + 내 골 {match.playerGoals} + 도움 {match.playerAssists} + 압박 {match.pressGoals} = {match.ourScore}
          </div>
          {match.scenes.length > 0 && (
            <details>
              <summary>장면 판정 상세</summary>
              <ul className="small">
                {match.scenes.map((s, i) => (
                  <li key={i}>
                    {s.minute}' {s.sceneName}{s.extra ? ' (추가)' : ''} — 성공률 {f1(s.probability)}% → {s.success ? '성공' : '실패'}
                    <span className="muted"> [{s.modifiers.join(', ')}]</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}
    </div>
  );
}

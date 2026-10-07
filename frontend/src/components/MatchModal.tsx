import { useEffect, useRef } from 'react';
import type { ClutchChoiceView, GameView, MatchRecord, TimelineEntry } from '../types';
import { useUiStore } from '../store';
import { f1, resultLabel, roleLabel } from '../format';
import Modal from './Modal';

const MY_SCENE = new Set(['SCENE_SUCCESS', 'SCENE_FAIL', 'CLUTCH']);
const LINE_CLASS: Record<string, string> = {
  KICKOFF: 'marker', HALF_TIME: 'marker', FULL_TIME: 'marker', PENALTIES: 'marker',
  TEAM_GOAL: 'goal-for', OPPONENT_GOAL: 'goal-against',
  SCENE_SUCCESS: 'success', SCENE_FAIL: 'fail', CLUTCH: 'clutch',
};

interface Props {
  view: GameView;
  pending: boolean;
  onClutch: (momentId: string, index: number) => void;
}

/**
 * 경기 중계. '다음 장면' 한 번에 내 장면 하나까지(그 사이 득점·실점·하프타임 포함) 보여 준다.
 * 스코어는 보이는 줄까지만, 최종 스코어·골·도움·평점은 마지막 장면을 넘긴 뒤 공개한다.
 * 진행 중 경기(liveMatch)는 승부처 차례에 선택지를 보여 주고, 고르면 서버가 나머지를 계산한다.
 */
export default function MatchModal({ view, pending, onClutch }: Props) {
  const playback = useUiStore((s) => s.playback)!;
  const setShown = useUiStore((s) => s.setShown);
  const clear = useUiStore((s) => s.clearPlayback);

  const live = playback.target === 'live' ? view.liveMatch : null;
  const record: MatchRecord | undefined = playback.target === 'live' ? undefined : view.matches[playback.target];
  if (!live && !record) return null;

  const entries: TimelineEntry[] = live ? live.timeline : record!.timeline;
  const shown = Math.min(playback.shown, entries.length);
  const visible = entries.slice(0, shown);
  const allShown = shown >= entries.length;
  const awaitingClutch = live !== null && allShown;
  const finished = record !== undefined && allShown;
  const current = visible[visible.length - 1];

  const next = () => {
    let i = shown;
    while (i < entries.length && !MY_SCENE.has(entries[i].kind)) i++;
    // 다음 내 장면까지, 남은 내 장면이 없으면 끝까지
    setShown(i < entries.length ? i + 1 : entries.length);
  };
  const skip = () => setShown(entries.length);

  const competition = live ? `${live.competition} ${live.roundLabel}` : `${record!.competitionName} ${record!.roundLabel}`;
  const role = live ? live.role : roleLabel[record!.role];
  const opponent = live ? live.opponentName : record!.opponentName;
  const meta = live
    ? `${live.dateLabel} · ${live.home ? '홈' : '원정'} · 상대 ${live.opponentType} · 전력 ${live.opponentStrength} · 핵심 수비 ${live.opponentDefender}`
    : `${record!.dateLabel} · ${record!.home ? '홈' : '원정'} · 상대 전력 ${record!.opponentStrength} · 핵심 수비 ${record!.opponentDefender}`;
  const our = finished ? record!.ourScore : current?.ourScore ?? 0;
  const their = finished ? record!.theirScore : current?.theirScore ?? 0;

  return (
    <Modal title={competition}>
      <div className="eyebrow">{competition} · {role}</div>
      <h2>{finished ? '경기 결과' : '오늘의 경기'}</h2>
      <div className="score">
        <span>{view.school.name}</span>
        <strong>{our} : {their}</strong>
        <span>{opponent}</span>
      </div>
      <p className="score-sub">
        {finished ? resultLabel(record!.result, record!.penaltyWin) : shown > 0 ? `${current?.minute}분` : '킥오프 전'} · {meta}
      </p>

      <div className="commentary" aria-live="polite">
        {shown === 0 && <p className="marker">중계를 확인하며 장면을 하나씩 넘겨 보세요.</p>}
        {visible.map((e, i) => (
          <p key={i} className={LINE_CLASS[e.kind] ?? ''}>
            <span className="minute">{e.minute}'</span>{e.text} <span className="tiny">({e.ourScore}-{e.theirScore})</span>
            {e.probability !== null && (
              <small>성공 확률 {f1(e.probability)}%{e.modifiers && ` · ${e.modifiers.join(', ')}`}</small>
            )}
          </p>
        ))}
      </div>

      {awaitingClutch && live && <ClutchPanel live={live} pending={pending} onChoose={onClutch} />}

      {finished && <MatchSummary record={record!} />}

      <div className="modal-actions">
        {!allShown && <button className="secondary" onClick={skip}>중계 건너뛰기</button>}
        {!allShown && <button className="primary" onClick={next}>다음 장면 →</button>}
        {finished && <button className="primary" onClick={clear}>경기 확인 완료</button>}
      </div>
    </Modal>
  );
}

function ClutchPanel({ live, pending, onChoose }: {
  live: NonNullable<GameView['liveMatch']>;
  pending: boolean;
  onChoose: (momentId: string, index: number) => void;
}) {
  const c = live.clutch;
  const ref = useRef<HTMLDivElement>(null);
  // 승부처 차례가 오면 선택지가 보이도록 내려 준다
  useEffect(() => {
    ref.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [c.momentId]);
  return (
    <div className="clutch-panel" ref={ref}>
      <div className="eyebrow">Clutch moment · {c.minute}분</div>
      <h3>{c.title}</h3>
      <p>{c.situation}</p>
      <ol className="event-choices">
        {c.choices.map((o: ClutchChoiceView) => (
          <li key={o.index}>
            <button disabled={pending} onClick={() => onChoose(c.momentId, o.index)}>
              <span className="no">0{o.index + 1}</span>
              <span className={`style-tag ${o.style}`}>{o.styleLabel}</span>
              <span className="choice-text">{o.text}</span>
              {o.trait && <span className="trait-badge">{o.trait} +1</span>}
              <span className="choice-detail">
                성공 확률 <b>{f1(o.probability)}%</b> ({o.stats.join('·')}) · {o.reward} · 실패 시 평점 {o.failRating}
                <br /><span className="tiny">{o.modifiers.join(', ')}</span>
              </span>
            </button>
          </li>
        ))}
      </ol>
    </div>
  );
}

function MatchSummary({ record }: { record: MatchRecord }) {
  return (
    <>
      <div className="match-numbers">
        <span>골<b>{record.playerGoals}</b></span>
        <span>도움<b>{record.playerAssists}</b></span>
        <span>평점<b>{record.rating === null ? '—' : f1(record.rating)}</b></span>
        <span>평판<b>+{f1(record.reputationGained)}</b></span>
      </div>
      <p className="score-sub">
        {record.rating === null && `${roleLabel[record.role]} — 출전하지 않았습니다. `}
        팀 득점 {record.teamGoals} + 내 골 {record.playerGoals} + 도움 {record.playerAssists} + 압박 {record.pressGoals}
        {record.clutchTeamGoals > 0 && ` + 승부처 ${record.clutchTeamGoals}`} = {record.ourScore}
        {record.passiveGrowth && record.passiveGrowthAmount !== null && ` · ${record.passiveGrowth} +${record.passiveGrowthAmount}`}
      </p>
    </>
  );
}

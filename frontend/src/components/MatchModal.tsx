import type { ClutchChoiceView, GameView, MatchRecord, TimelineEntry } from '../types';
import { useUiStore } from '../store';
import { f1, resultLabel, roleLabel } from '../format';
import Modal from './Modal';

const MY_SCENE = new Set(['SCENE_SUCCESS', 'SCENE_FAIL', 'CLUTCH']);

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

  const header = live
    ? `${live.competition} ${live.roundLabel} · ${live.home ? '홈' : '원정'} vs ${live.opponentName}`
    : `${record!.competition === 'CUP' ? view.cup.name : '주말리그'} ${record!.roundLabel} · ${record!.home ? '홈' : '원정'} vs ${record!.opponentName}`;
  const sub = live
    ? `${live.dateLabel} · 상대 전력 ${live.opponentStrength} · 핵심 수비 ${live.opponentDefender} · 출전: ${live.role}`
    : `${record!.dateLabel} · 상대 전력 ${record!.opponentStrength} · 핵심 수비 ${record!.opponentDefender} · 출전: ${roleLabel[record!.role]}`;

  return (
    <Modal title={header}>
      <h2>{header}</h2>
      <div className="muted small">{sub}</div>
      <div className="scoreboard">
        {finished ? `${record!.ourScore} : ${record!.theirScore}` : `${current?.ourScore ?? 0} : ${current?.theirScore ?? 0}`}
        {finished && <span className="result"> {resultLabel(record!.result, record!.penaltyWin)}</span>}
        {!finished && shown > 0 && <span className="muted small"> ({current?.minute}분)</span>}
      </div>
      <ol className="timeline">
        {shown === 0 && <li className="muted">킥오프 전</li>}
        {visible.map((e, i) => (
          <li key={i} className={e.kind}>
            <span className="minute">{e.minute}'</span> {e.text} <span className="muted">({e.ourScore}-{e.theirScore})</span>
            {e.probability !== null && (
              <div className="prob">
                성공 확률 <b>{f1(e.probability)}%</b>
                {e.modifiers && <span className="muted"> · {e.modifiers.join(', ')}</span>}
              </div>
            )}
          </li>
        ))}
      </ol>

      {awaitingClutch && live && <ClutchPanel live={live} pending={pending} onChoose={onClutch} />}

      {!allShown && (
        <div className="row">
          <button className="primary" onClick={next}>다음 장면</button>
          <button onClick={skip}>건너뛰기</button>
        </div>
      )}

      {finished && <MatchSummary record={record!} />}
      {finished && <button className="primary" onClick={clear}>닫기</button>}
    </Modal>
  );
}

function ClutchPanel({ live, pending, onChoose }: {
  live: NonNullable<GameView['liveMatch']>;
  pending: boolean;
  onChoose: (momentId: string, index: number) => void;
}) {
  const c = live.clutch;
  return (
    <div className="clutch">
      <div className="clutch-title">승부처 · {c.minute}분 · {c.title}</div>
      <p>{c.situation}</p>
      <ol className="choices">
        {c.choices.map((o: ClutchChoiceView) => (
          <li key={o.index}>
            <button disabled={pending} onClick={() => onChoose(c.momentId, o.index)}>
              <span className="choice-no">{o.index + 1}.</span> <span className={`style-tag ${o.style}`}>{o.styleLabel}</span> {o.text}
              {o.trait && <span className="trait-badge">{o.trait} +1</span>}
              <div className="small">
                성공 확률 <b>{f1(o.probability)}%</b> ({o.stats.join('·')}) · {o.reward} · 실패 시 평점 {o.failRating}
              </div>
              <div className="muted tiny">{o.modifiers.join(', ')}</div>
            </button>
          </li>
        ))}
      </ol>
    </div>
  );
}

function MatchSummary({ record }: { record: MatchRecord }) {
  return (
    <div className="match-footer">
      {record.rating !== null ? (
        <>
          평점 <b>{f1(record.rating)}</b> · 골 {record.playerGoals} · 도움 {record.playerAssists}
          {record.pressGoals > 0 && ` · 압박 득점 ${record.pressGoals}`}
          {record.clutchTeamGoals > 0 && ` · 승부처 팀 득점 ${record.clutchTeamGoals}`} · 평판 +{f1(record.reputationGained)}
          {record.passiveGrowth && record.passiveGrowthAmount !== null && ` · ${record.passiveGrowth} +${record.passiveGrowthAmount}`}
        </>
      ) : (
        <span className="muted">{roleLabel[record.role]} — 평점 없음</span>
      )}
      <div className="muted small">
        팀 득점 {record.teamGoals} + 내 골 {record.playerGoals} + 도움 {record.playerAssists} + 압박 {record.pressGoals}
        {record.clutchTeamGoals > 0 && ` + 승부처 ${record.clutchTeamGoals}`} = {record.ourScore}
      </div>
    </div>
  );
}

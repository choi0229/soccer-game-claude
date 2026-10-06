import type { ActionRequest, GameView } from '../types';

interface Props {
  view: GameView;
  pending: boolean;
  onSubmit: (a: ActionRequest) => void;
}

export default function SaturdayPanel({ view, pending, onSubmit }: Props) {
  const m = view.matchToday;
  return (
    <div className="box">
      <h2>토요일 — 경기일</h2>
      {m ? (
        <p className="match-today">
          {m.competition} {m.round} {m.home ? '홈' : '원정'} vs {m.opponentName} ({m.opponentType}, 전력 {m.opponentStrength}, 핵심 수비 {m.opponentDefender})
          <br />
          <span className="muted small">예상 출전: {view.resources.expectedRole}</span>
        </p>
      ) : (
        <p>오늘은 경기가 없습니다.</p>
      )}
      <button className="primary" disabled={pending} onClick={() => onSubmit({ type: 'DAY' })}>
        {pending ? '진행 중…' : m ? '경기 시작' : '다음 날로'}
      </button>
    </div>
  );
}

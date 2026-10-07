import { useRecentOutcomes } from '../queries';
import type { ActionOutcome } from '../types';

function Day({ outcome }: { outcome: ActionOutcome }) {
  return (
    <div className="journal-day">
      <div className="journal-date">{outcome.dateLabel}</div>
      <ul>
        {outcome.log.map((e, i) => (
          <li key={i} className={e.slot === '알림' ? 'alert-line' : ''}>
            <span className="slot-tag">{e.slot}</span>
            <span>{e.text}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** 하루 기록: 가장 최근 결과는 펼쳐 보이고, 그 전 기록은 접어 둔다 */
export default function Journal({ runId }: { runId: string }) {
  const { data = [] } = useRecentOutcomes(runId);
  return (
    <section className="panel journal">
      <div className="panel-heading">
        <h2>하루 기록</h2>
        <span className="muted">최근 진행 결과</span>
      </div>
      {data.length === 0 && <p className="muted">아직 기록이 없습니다. 첫날을 시작해 보세요.</p>}
      {data[0] && <Day outcome={data[0].outcome} />}
      {data.length > 1 && (
        <details className="journal-older">
          <summary>지난 기록 {data.length - 1}개</summary>
          {data.slice(1).map(({ actionCount, outcome }) => <Day key={actionCount} outcome={outcome} />)}
        </details>
      )}
    </section>
  );
}

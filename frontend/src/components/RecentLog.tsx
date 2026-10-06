import { useRecentOutcomes } from '../queries';

export default function RecentLog({ runId }: { runId: string }) {
  const { data = [] } = useRecentOutcomes(runId);
  if (data.length === 0) return null;
  return (
    <div className="box">
      <h3>최근 기록</h3>
      {data.map(({ actionCount, outcome }) => (
        <div key={actionCount} className="log">
          <div className="log-title">{outcome.dateLabel}</div>
          <ul>
            {outcome.log.map((e, i) => (
              <li key={i}><span className="slot">{e.slot}</span> {e.text}</li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

import type { GameView } from '../types';
import { f1, roleLabel, signed, won } from '../format';
import { useUiStore } from '../store';

export default function SeasonSummaryView({ view }: { view: GameView }) {
  const s = view.summary;
  const setRunId = useUiStore((st) => st.setRunId);
  const roles = Object.entries(s.roles).filter(([, n]) => n > 0).map(([r, n]) => `${roleLabel[r]} ${n}`).join(' · ');
  const cells: [string, string][] = [
    ['출전', `${s.appearances}경기`],
    ['득점', `${s.goals}골`],
    ['도움', `${s.assists}개`],
    ['평균 평점', s.averageRating === null ? '-' : f1(s.averageRating)],
    ['학업 성취', f1(s.academics)],
    ['평판', f1(s.reputation)],
  ];
  return (
    <section className="panel season">
      <div className="eyebrow">Season complete</div>
      <h2>{view.date.weeksPerYear}주, 첫 시즌을 마쳤습니다.</h2>
      <p>{view.school.name} · 주말리그 최종 {s.leagueRank}위 · {s.wins}승 {s.draws}무 {s.losses}패 · {roles}</p>
      <div className="summary-grid">
        {cells.map(([label, value]) => (
          <div key={label}><span>{label}</span><strong>{value}</strong></div>
        ))}
      </div>

      <h3>대회별 성적</h3>
      <ul className="summary-list">
        <li><span>주말리그</span><b>최종 {s.leagueRank}위</b></li>
        {s.tournaments.map((t) => (
          <li key={t.key}><span>{t.name} <span className="tiny">{t.period}</span></span><b>{t.text}{t.matches > 0 && ` · ${t.matches}경기`}</b></li>
        ))}
      </ul>

      <h3>학교생활과 몸</h3>
      <ul className="summary-list">
        <li><span>나머지 공부</span><b>{s.makeupWeeks}주 (실제 {s.makeupDays}일)</b></li>
        <li><span>돈</span><b>{won(s.money)}</b></li>
        <li><span>부상 · 훈련 제외 · 이벤트</span><b>{s.injuries}회 · {s.exclusions}회 · {s.events}편</b></li>
      </ul>

      <h3>능력치 변화</h3>
      <div className="table-wrap">
        <table>
          <thead><tr><th>능력치</th><th>시작</th><th>종료</th><th>변화</th><th>등급</th></tr></thead>
          <tbody>
            {s.stats.map((st) => (
              <tr key={st.key} className={st.primary ? 'my-school' : ''}>
                <td>{st.name}{st.primary ? ' ★' : ''}{st.passive && <span className="tiny"> 패시브</span>}</td>
                <td>{f1(st.start)}</td>
                <td>{f1(st.end)}</td>
                <td>{signed(st.end - st.start)}</td>
                <td>{st.startGrade} → <b>{st.endGrade}</b></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button className="secondary" onClick={() => setRunId(null)}>새 시즌 시작</button>
    </section>
  );
}

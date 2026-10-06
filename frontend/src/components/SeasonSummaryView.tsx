import type { GameView } from '../types';
import { f1, roleLabel, signed, won } from '../format';
import { useUiStore } from '../store';

export default function SeasonSummaryView({ view }: { view: GameView }) {
  const s = view.summary;
  const setRunId = useUiStore((st) => st.setRunId);
  return (
    <div className="box summary">
      <h2>시즌 요약 — 1학년 1년 종료</h2>
      <div className="summary-grid">
        <div>
          <h3>경기 기록</h3>
          <ul>
            <li>{s.wins}승 {s.draws}무 {s.losses}패 (플레이어 학교 경기 {s.wins + s.draws + s.losses}경기)</li>
            <li>
              {Object.entries(s.roles).filter(([, n]) => n > 0).map(([r, n]) => `${roleLabel[r]} ${n}`).join(' · ')}
            </li>
            <li>출전 {s.appearances}경기 · 골 {s.goals} · 도움 {s.assists} · 평균 평점 {s.averageRating === null ? '-' : f1(s.averageRating)}</li>
            <li>주말리그 최종 {s.leagueRank}위 · 춘계배 {s.cupResult}</li>
          </ul>
        </div>
        <div>
          <h3>학업과 평판</h3>
          <ul>
            <li>학업 성취 {f1(s.academics)}</li>
            <li>나머지 공부 {s.makeupWeeks}주 (실제 {s.makeupDays}일)</li>
            <li>평판 {f1(s.reputation)} · 돈 {won(s.money)}</li>
            <li>부상 {s.injuries}회 · 훈련 제외 {s.exclusions}회 · 이벤트 {s.events}편</li>
          </ul>
        </div>
      </div>
      <h3>능력치 변화</h3>
      <table className="stats wide">
        <thead><tr><th>능력치</th><th>시작</th><th>종료</th><th>변화</th><th>등급</th></tr></thead>
        <tbody>
          {s.stats.map((st) => (
            <tr key={st.key} className={st.passive ? 'passive' : ''}>
              <td>{st.name}{st.primary && ' ★'}{st.passive && <span className="muted small"> 패시브</span>}</td>
              <td className="num">{f1(st.start)}</td>
              <td className="num">{f1(st.end)}</td>
              <td className="num">{signed(st.end - st.start)}</td>
              <td>{st.startGrade} → <b>{st.endGrade}</b></td>
            </tr>
          ))}
        </tbody>
      </table>
      <button className="primary" onClick={() => setRunId(null)}>새 판 시작하기</button>
    </div>
  );
}

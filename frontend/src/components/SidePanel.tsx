import type { GameView, StatView } from '../types';
import { useUiStore } from '../store';
import { f1, resultLabel, roleLabel, signed } from '../format';

export default function SidePanel({ view }: { view: GameView }) {
  const tab = useUiStore((s) => s.sideTab);
  const setTab = useUiStore((s) => s.setSideTab);
  return (
    <div className="box">
      <div className="tabs">
        <button className={tab === 'stats' ? 'active' : ''} onClick={() => setTab('stats')}>능력치</button>
        <button className={tab === 'league' ? 'active' : ''} onClick={() => setTab('league')}>리그 순위</button>
        <button className={tab === 'matches' ? 'active' : ''} onClick={() => setTab('matches')}>경기 기록</button>
      </div>
      {tab === 'stats' && <StatsPanel stats={view.stats} />}
      {tab === 'league' && <LeagueTable view={view} />}
      {tab === 'matches' && <MatchList view={view} />}
    </div>
  );
}

function StatsPanel({ stats }: { stats: StatView[] }) {
  const groups: [string, StatView[]][] = [
    ['공격 능력', stats.filter((s) => s.group === 'attack')],
    ['연계 능력', stats.filter((s) => s.group === 'link')],
    ['패시브', stats.filter((s) => s.passive)],
  ];
  return (
    <>
      {groups.map(([title, list]) => (
        <table key={title} className="stats">
          <caption>{title}</caption>
          <tbody>
            {list.map((s) => (
              <tr key={s.key}>
                <td>{s.name}{s.primary && <span className="primary-mark" title="주력"> ★</span>}</td>
                <td className="num">{f1(s.value)}</td>
                <td className={`grade g${s.grade}`}>{s.grade}</td>
                <td className="num muted small">{signed(s.value - s.initial)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ))}
    </>
  );
}

function LeagueTable({ view }: { view: GameView }) {
  return (
    <table className="league">
      <thead>
        <tr><th>#</th><th>학교</th><th>경기</th><th>승</th><th>무</th><th>패</th><th>득</th><th>실</th><th>차</th><th>승점</th></tr>
      </thead>
      <tbody>
        {view.league.map((r) => (
          <tr key={r.schoolId} className={r.player ? 'me' : ''}>
            <td>{r.rank}</td>
            <td title={`${r.typeName} · 전력 ${r.strength}`}>{r.name} <span className="muted small">{r.typeName}</span></td>
            <td>{r.played}</td><td>{r.won}</td><td>{r.drawn}</td><td>{r.lost}</td>
            <td>{r.goalsFor}</td><td>{r.goalsAgainst}</td><td>{r.goalDiff}</td><td><b>{r.points}</b></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function MatchList({ view }: { view: GameView }) {
  const showMatch = useUiStore((s) => s.showMatch);
  if (view.matches.length === 0) return <p className="muted">아직 치른 경기가 없습니다.</p>;
  return (
    <table className="league">
      <thead><tr><th>날짜</th><th>대회</th><th>상대</th><th>스코어</th><th>출전</th><th>평점</th><th>골/도움</th></tr></thead>
      <tbody>
        {view.matches.map((m, i) => (
          <tr key={i} className="clickable" onClick={() => showMatch(i)}>
            <td className="small">{m.dateLabel}</td>
            <td className="small">{m.competition === 'CUP' ? view.cup.name : '리그'} {m.roundLabel}</td>
            <td>{m.opponentName}</td>
            <td>{m.ourScore}:{m.theirScore} {resultLabel(m.result, m.penaltyWin)}</td>
            <td>{roleLabel[m.role]}</td>
            <td>{m.rating === null ? '-' : f1(m.rating)}</td>
            <td>{m.playerGoals}/{m.playerAssists}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

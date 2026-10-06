import type { GameView, StatView } from '../types';
import { useUiStore } from '../store';
import { f0, f1, resultLabel, roleLabel, signed } from '../format';

export default function SidePanel({ view }: { view: GameView }) {
  const tab = useUiStore((s) => s.sideTab);
  const setTab = useUiStore((s) => s.setSideTab);
  return (
    <div className="box">
      <div className="tabs">
        <button className={tab === 'stats' ? 'active' : ''} onClick={() => setTab('stats')}>능력치</button>
        <button className={tab === 'growth' ? 'active' : ''} onClick={() => setTab('growth')}>특성·인연</button>
        <button className={tab === 'league' ? 'active' : ''} onClick={() => setTab('league')}>리그 순위</button>
        <button className={tab === 'matches' ? 'active' : ''} onClick={() => setTab('matches')}>경기 기록</button>
      </div>
      {tab === 'stats' && <StatsPanel stats={view.stats} />}
      {tab === 'growth' && <GrowthPanel view={view} />}
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

function GrowthPanel({ view }: { view: GameView }) {
  return (
    <>
      <table className="stats">
        <caption>특성</caption>
        <thead><tr><th>특성</th><th className="num">점수</th><th>단계</th><th>다음 단계</th></tr></thead>
        <tbody>
          {view.traits.map((t) => (
            <tr key={t.key} title={t.tierEffects.map((e, i) => `${i + 1}단계: ${e}`).join('\n')}>
              <td>{t.name}<div className="muted tiny">{t.effect}</div></td>
              <td className="num">{t.score}</td>
              <td>{t.tier}단계</td>
              <td className="small">{t.remaining === null ? '최고 단계' : `${t.remaining}점 남음 (${t.nextAt}점)`}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <table className="stats">
        <caption>인연</caption>
        <thead><tr><th>축</th><th className="num">관계도</th><th>단계</th><th>다음 단계</th></tr></thead>
        <tbody>
          {view.bonds.map((b) => (
            <tr key={b.axis} className={b.locked ? 'locked' : ''} title={b.tierEffects.map((e, i) => `${i + 1}단계: ${e}`).join('\n')}>
              <td>{b.label}<div className="muted tiny">{b.effect}</div></td>
              <td className="num">{b.locked || b.affinity === null ? '-' : f0(b.affinity)}</td>
              <td>{b.locked ? '잠김' : `${b.tier}단계`}</td>
              <td className="small">
                {b.locked ? '이벤트로 열림' : b.nextAt === null ? '최고 단계' : `관계도 ${b.nextAt}`}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="muted tiny">특성 보너스와 인연 보너스를 합친 훈련 성장 보너스에는 상한이 있습니다. 항목에 마우스를 올리면 단계별 효과가 보입니다.</p>
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

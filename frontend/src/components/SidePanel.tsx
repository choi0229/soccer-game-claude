import type { GameView, StatView } from '../types';
import { useUiStore } from '../store';
import { f0, f1, resultLabel, roleLabel, signed } from '../format';

const TABS = [
  ['stats', '능력치'],
  ['growth', '특성·인연'],
  ['schedule', '일정'],
  ['league', '리그'],
  ['matches', '경기'],
] as const;

export default function SidePanel({ view }: { view: GameView }) {
  const tab = useUiStore((s) => s.sideTab);
  const setTab = useUiStore((s) => s.setSideTab);
  return (
    <aside className="panel records">
      <nav className="tabs" aria-label="선수 정보">
        {TABS.map(([key, label]) => (
          <button key={key} className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>{label}</button>
        ))}
      </nav>
      {tab === 'stats' && <StatsTab view={view} />}
      {tab === 'growth' && <GrowthTab view={view} />}
      {tab === 'schedule' && <ScheduleTab view={view} />}
      {tab === 'league' && <LeagueTab view={view} />}
      {tab === 'matches' && <MatchesTab view={view} />}
    </aside>
  );
}

function StatsTab({ view }: { view: GameView }) {
  const primary = view.stats.filter((s) => s.primary).map((s) => s.name).join(' / ');
  const groups: [string, StatView[]][] = [
    ['공격 능력', view.stats.filter((s) => s.group === 'attack')],
    ['연계 능력', view.stats.filter((s) => s.group === 'link')],
    ['패시브', view.stats.filter((s) => s.passive)],
  ];
  return (
    <>
      <div className="stats-intro">
        <span>{view.school.name}</span>
        <small>주력 · {primary} · 출전 점수 {f1(view.resources.selectionScore)} → {view.resources.expectedRole}</small>
      </div>
      {groups.map(([title, list]) => (
        <div className="stat-group" key={title}>
          <h3>{title}</h3>
          {list.map((s) => (
            <div className={s.primary ? 'stat-row primary-stat' : 'stat-row'} key={s.key}>
              <span>{s.name}{s.primary && <small>주력</small>}</span>
              <span className="growth">{signed(s.value - s.initial)}</span>
              <b>{f1(s.value)}</b>
              <span className="grade">{s.grade}</span>
            </div>
          ))}
        </div>
      ))}
    </>
  );
}

function GrowthTab({ view }: { view: GameView }) {
  return (
    <>
      <div className="stats-intro">
        <span>특성과 인연</span>
        <small>특성은 이벤트·승부처 선택으로, 인연은 관계도로 단계가 오릅니다. 훈련 보너스에는 상한이 있습니다.</small>
      </div>
      <div className="stat-group">
        <h3>특성</h3>
        {view.traits.map((t) => (
          <div className="growth-row" key={t.key} title={t.tierEffects.map((e, i) => `${i + 1}단계: ${e}`).join('\n')}>
            <span><b>{t.name}</b> · {t.score}점</span>
            <span className="tier">{t.tier}단계</span>
            {t.nextAt !== null && <progress value={t.score} max={t.nextAt} />}
            <small>{t.effect} · {t.remaining === null ? '최고 단계' : `다음 단계까지 ${t.remaining}점 (${t.nextAt}점)`}</small>
          </div>
        ))}
      </div>
      <div className="stat-group">
        <h3>인연</h3>
        {view.bonds.map((b) => (
          <div className={b.locked ? 'growth-row locked' : 'growth-row'} key={b.axis}
            title={b.tierEffects.map((e, i) => `${i + 1}단계: ${e}`).join('\n')}>
            <span><b>{b.label}</b> · {b.locked || b.affinity === null ? '잠김' : `관계도 ${f0(b.affinity)}`}</span>
            <span className="tier">{b.locked ? '잠김' : `${b.tier}단계`}</span>
            <small>{b.effect}{!b.locked && (b.nextAt === null ? ' · 최고 단계' : ` · 다음 단계 관계도 ${b.nextAt}`)}</small>
          </div>
        ))}
      </div>
    </>
  );
}

function ScheduleTab({ view }: { view: GameView }) {
  return (
    <>
      <div className="stats-intro">
        <span>시즌 일정</span>
        <small>{view.nextMatch ? `다음 경기 · ${view.nextMatch.dateLabel} (${view.nextMatch.competition})` : '남은 경기가 정해지지 않았습니다.'}</small>
      </div>
      <div className="table-wrap">
        <table className="schedule">
          <thead><tr><th>대회</th><th>기간</th><th>상태</th><th>성적</th></tr></thead>
          <tbody>
            {view.schedule.map((s) => (
              <tr key={s.key} className={s.status === '진행 중' ? 'live' : ''}>
                <td><b>{s.name}</b>{s.nextMatch && <span className="tiny">다음 · {s.nextMatch}</span>}</td>
                <td>{s.period}</td>
                <td>{s.status}</td>
                <td>{s.result}{s.matchesPlayed > 0 && <span className="tiny"> · {s.matchesPlayed}경기</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function LeagueTab({ view }: { view: GameView }) {
  return (
    <>
      <div className="stats-intro">
        <span>주말리그 · {view.school.region}</span>
        <small>{view.league.length}개교 · 승점 → 골득실 → 다득점 → 학교 번호 순</small>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>순위 / 학교</th><th>경기</th><th className="hide-narrow">승</th><th className="hide-narrow">무</th><th className="hide-narrow">패</th><th>득실</th><th>승점</th></tr>
          </thead>
          <tbody>
            {view.league.map((r) => (
              <tr key={r.schoolId} className={r.player ? 'my-school' : ''}>
                <td title={`${r.typeName} · 전력 ${r.strength}`}><span className="rank">{r.rank}</span>{r.name}</td>
                <td>{r.played}</td>
                <td className="hide-narrow">{r.won}</td><td className="hide-narrow">{r.drawn}</td><td className="hide-narrow">{r.lost}</td>
                <td>{r.goalDiff > 0 ? `+${r.goalDiff}` : r.goalDiff}</td>
                <td><b>{r.points}</b></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function MatchesTab({ view }: { view: GameView }) {
  const showMatch = useUiStore((s) => s.showMatch);
  const s = view.summary;
  return (
    <>
      <div className="stats-intro">
        <span>{s.appearances}경기 출전 · {s.goals}골 · {s.assists}도움</span>
        <small>평균 평점 {s.averageRating === null ? '-' : f1(s.averageRating)} · {s.wins}승 {s.draws}무 {s.losses}패</small>
      </div>
      <div className="match-history">
        {view.matches.length === 0 && <p className="muted">첫 경기의 휘슬을 기다리고 있습니다.</p>}
        {[...view.matches].map((m, i) => ({ m, i })).reverse().map(({ m, i }) => (
          <details key={i}>
            <summary>
              <span>{m.competitionName} {m.roundLabel} · {m.opponentName}</span>
              <b>{m.ourScore} : {m.theirScore}</b>
            </summary>
            <p className="history-line">{m.dateLabel} · {resultLabel(m.result, m.penaltyWin)} · {roleLabel[m.role]}
              {m.rating !== null && ` · 평점 ${f1(m.rating)} · 골 ${m.playerGoals} · 도움 ${m.playerAssists}`}</p>
            {m.clutch && <p className="history-line">승부처 · {m.clutch.title}: {m.clutch.choiceText} → {m.clutch.success ? '성공' : '실패'}</p>}
            <button className="link" onClick={() => showMatch(i)}>중계 다시 보기</button>
          </details>
        ))}
      </div>
    </>
  );
}

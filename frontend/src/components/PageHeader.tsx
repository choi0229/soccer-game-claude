import type { GameView } from '../types';
import { f0, f1, won } from '../format';

/** 날짜·학기/방학·진행 주차와 자원 바, 경고 */
export default function PageHeader({ view }: { view: GameView }) {
  const r = view.resources;
  const d = view.date;
  const next = view.nextMatch;
  return (
    <>
      <section className="page-title">
        <div>
          <div className="eyebrow">{view.school.name} / {view.school.typeName} · 전력 {view.school.strength} · {view.school.region}</div>
          <h1>{d.label}</h1>
          <p>
            <span className={d.vacation ? 'term vacation' : 'term'}>{d.term}</span>
            {d.week}/{d.weeksPerYear}주{view.matchToday ? ` · 오늘 ${view.matchToday.competition} 경기` : ''}
          </p>
        </div>
        <div className="season-progress">
          <span>첫 시즌</span>
          <strong>{d.week}<small> / {d.weeksPerYear}주</small></strong>
          <progress value={d.week} max={d.weeksPerYear} />
        </div>
      </section>

      <section className="resource-bar" aria-label="자원">
        <div className="stamina" title={r.staminaInfo.maxFormula}>
          <span>체력 {r.injured && <em>부상 · 복귀 {r.injuredUntil}</em>}</span>
          <strong>{f0(r.stamina)}<small> / {f0(r.maxStamina)}</small></strong>
          <progress className={r.stamina < r.staminaInfo.lowBelow ? 'low' : ''} value={r.stamina} max={r.maxStamina} />
          <span className="formula">{r.staminaInfo.maxFormula}</span>
        </div>
        <div><span>컨디션</span><strong>{r.conditionName}</strong></div>
        <div><span>학업 성취</span><strong>{f1(r.academics)}</strong></div>
        <div><span>돈</span><strong>{won(r.money)}</strong></div>
        <div><span>평판</span><strong>{f1(r.reputation)}</strong></div>
        <div title={next?.dateLabel}>
          <span>다음 경기</span>
          <strong>{next ? (next.days === 0 ? '오늘' : `${next.days}일 뒤`) : '없음'}</strong>
          {next && <small>{next.competition}</small>}
        </div>
      </section>

      {r.academicsInfo.warning && (
        <div className="alerts">
          <div className="notice" role="status">⚠ {r.academicsInfo.warning}</div>
        </div>
      )}
    </>
  );
}

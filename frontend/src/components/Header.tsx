import type { GameView } from '../types';
import { f0, f1, won } from '../format';

export default function Header({ view, onQuit }: { view: GameView; onQuit: () => void }) {
  const r = view.resources;
  const staminaPct = Math.max(0, Math.min(100, (r.stamina / r.maxStamina) * 100));
  return (
    <header className="header">
      <div className="row between">
        <div>
          <strong className="date">{view.date.label}</strong>{' '}
          <span className={view.date.vacation ? 'tag vacation' : 'tag'}>{view.date.term}</span>{' '}
          <span className="muted">
            {view.date.week}주차 / {view.date.weeksPerYear} · {view.school.name} ({view.school.typeName}, 전력 {view.school.strength}) · {view.school.region}
          </span>
        </div>
        <div className="muted small">
          시드 {view.seed} · 행동 {view.actionCount} · 지문 {view.fingerprint}{' '}
          <button className="link" onClick={onQuit}>나가기</button>
        </div>
      </div>
      <div className="resources">
        <div className="stamina" title={r.staminaInfo.maxFormula}>
          체력 {f0(r.stamina)} / {f0(r.maxStamina)} <span className="help">ⓘ</span>
          <div className="bar"><div style={{ width: `${staminaPct}%` }} className={r.stamina < r.staminaInfo.lowBelow ? 'low' : ''} /></div>
          <div className="muted tiny">{r.staminaInfo.maxFormula}</div>
        </div>
        <div>컨디션 <b>{r.conditionName}</b></div>
        <div>돈 <b>{won(r.money)}</b></div>
        <div>학업 성취 <b>{f1(r.academics)}</b></div>
        <div>평판 <b>{f1(r.reputation)}</b></div>
        {view.bonds.filter((b) => !b.locked && b.affinity !== null).map((b) => (
          <div key={b.axis} title={`인연 ${b.tier}단계: ${b.effect}`}>{b.label} <b>{f0(b.affinity!)}</b></div>
        ))}
        <div title="감독 관계도와 훈련 능력치 상위 평균으로 계산 (가중치는 설정 파일)">
          출전 점수 <b>{f1(r.selectionScore)}</b> → {r.expectedRole}
        </div>
        {view.nextMatch && (
          <div title={view.nextMatch.dateLabel}>
            다음 경기 <b>{view.nextMatch.days === 0 ? '오늘' : `${view.nextMatch.days}일 뒤`}</b>
            <span className="muted small"> ({view.nextMatch.competition})</span>
          </div>
        )}
        {r.injured && <div className="error">부상 (복귀: {r.injuredUntil})</div>}
      </div>
      {r.academicsInfo.warning && <div className="warning">⚠ {r.academicsInfo.warning}</div>}
    </header>
  );
}

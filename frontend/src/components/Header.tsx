import type { GameView } from '../types';
import { affinityLabel, f0, f1, won } from '../format';

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
            {view.date.week}주차 / 48 · {view.school.name} ({view.school.typeName}, 전력 {view.school.strength}) · {view.school.region}
          </span>
        </div>
        <div className="muted small">
          시드 {view.seed} · 행동 {view.actionCount} · 지문 {view.fingerprint}{' '}
          <button className="link" onClick={onQuit}>나가기</button>
        </div>
      </div>
      <div className="resources">
        <div className="stamina">
          체력 {f0(r.stamina)} / {f0(r.maxStamina)}
          <div className="bar"><div style={{ width: `${staminaPct}%` }} className={staminaPct < 30 ? 'low' : ''} /></div>
        </div>
        <div>컨디션 <b>{r.conditionName}</b></div>
        <div>돈 <b>{won(r.money)}</b></div>
        <div>학업 성취 <b>{f1(r.academics)}</b></div>
        <div>평판 <b>{f1(r.reputation)}</b></div>
        {Object.entries(r.affinity).map(([k, v]) => (
          <div key={k}>{affinityLabel[k] ?? k} <b>{f0(v)}</b></div>
        ))}
        <div title="감독 관계도 × 0.5 + 훈련 능력치 평균 × 0.5">
          출전 점수 <b>{f1(r.selectionScore)}</b> → {r.expectedRole}
        </div>
        {r.injured && <div className="error">부상 (복귀: {r.injuredUntil})</div>}
      </div>
    </header>
  );
}

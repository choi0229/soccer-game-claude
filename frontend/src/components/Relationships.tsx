import type { GameView } from '../types';
import { f0 } from '../format';

/** 함께하는 사람들: 관계도와 인연 단계 (잠긴 축은 잠김) */
export default function Relationships({ view }: { view: GameView }) {
  return (
    <section className="panel relationships">
      <h2>함께하는 사람들</h2>
      <div className="people">
        {view.bonds.map((b) => (
          <div key={b.axis} className={b.locked ? 'person locked' : 'person'} title={b.effect}>
            <span>{b.label}</span>
            <b>{b.locked || b.affinity === null ? '잠김' : f0(b.affinity)}</b>
            <progress value={b.affinity ?? 0} max={100} />
            <small>{b.locked ? '이벤트로 열림' : `인연 ${b.tier}단계`}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

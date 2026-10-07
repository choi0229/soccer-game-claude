import { useMeta } from '../queries';

export default function Masthead() {
  const meta = useMeta().data;
  return (
    <header className="masthead">
      <div className="brand">
        <span className="brand-mark">1</span>
        <div>
          1학년 시즌
          <span className="brand-sub">고교 축구선수 육성 기록</span>
        </div>
      </div>
      {meta && <span className="edition">고1 · {meta.archetype} {meta.position}</span>}
    </header>
  );
}

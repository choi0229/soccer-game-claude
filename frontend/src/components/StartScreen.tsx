import { useState } from 'react';
import { useCreateRun, useMeta } from '../queries';

export default function StartScreen() {
  const [seed, setSeed] = useState('');
  const create = useCreateRun();
  const meta = useMeta().data;
  const parsed = seed.trim() === '' ? undefined : Number(seed);
  const invalid = parsed !== undefined && (!Number.isSafeInteger(parsed) || parsed < 0);

  return (
    <div className="start">
      <h1>고교 축구 육성 시뮬레이션</h1>
      {meta && (
        <p>
          고1 축구부 {meta.position}({meta.archetype})가 되어 {meta.startLabel}부터 {meta.endLabel}까지 {meta.weeksPerYear}주를 보냅니다.
        </p>
      )}
      <label>
        시드 (비우면 무작위)
        <input value={seed} onChange={(e) => setSeed(e.target.value)} placeholder="예: 42" inputMode="numeric" />
      </label>
      {invalid && <p className="error">시드는 0 이상의 정수여야 합니다.</p>}
      <button className="primary" disabled={invalid || create.isPending} onClick={() => create.mutate(parsed)}>
        새 판 시작
      </button>
      {create.isError && <p className="error">{create.error.message}</p>}
    </div>
  );
}

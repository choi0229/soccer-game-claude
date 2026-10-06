export const f1 = (v: number) => (Math.round(v * 10) / 10).toFixed(1);
export const f0 = (v: number) => Math.round(v).toString();
export const won = (v: number) => `${v.toLocaleString('ko-KR')}원`;
export const signed = (v: number) => (v >= 0 ? `+${f1(v)}` : f1(v));

export const roleLabel: Record<string, string> = {
  starter: '선발',
  sub: '교체',
  bench: '벤치',
  absent: '부상 결장',
};

export const resultLabel = (r: 'W' | 'D' | 'L', pk: boolean | null) =>
  pk === null ? { W: '승', D: '무', L: '패' }[r] : pk ? '무 (승부차기 승)' : '무 (승부차기 패)';

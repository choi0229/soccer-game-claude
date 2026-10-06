// 같은 시드와 같은 선택 순서로 API 를 통해 1년을 두 번 진행하고 결과 지문을 비교한다.
// 사용: node scripts/replay-check.mjs [baseUrl] [seed]
const base = process.argv[2] ?? 'http://localhost:5173';
const seed = Number(process.argv[3] ?? 2024);

async function call(path, body) {
  const res = await fetch(base + path, {
    method: body ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${path} ${res.status} ${await res.text()}`);
  return res.json();
}

// 결정적인 선택 순서: 행동 순번으로만 정한다
function choose(view, step) {
  switch (view.phase) {
    case 'EVENT':
      return { type: 'EVENT_CHOICE', eventId: view.pendingEvent.eventId, choiceIndex: step % view.pendingEvent.choices.length };
    case 'SUNDAY':
      return step % 4 === 0
        ? { type: 'SUNDAY', activity: 'MEET', meetTarget: ['coach', 'teammate', 'family'][step % 3] }
        : { type: 'SUNDAY', activity: step % 4 === 1 ? 'PART_TIME' : 'REST' };
    default: {
      const menus = ['shooting', 'power', 'aerial', 'breakthrough', 'link', 'pressing', 'fitness'];
      if (step % 3 === 0) {
        return {
          type: 'DAY',
          dawn: step % 2 ? 'EXERCISE' : 'SLEEP',
          classAttitude: ['FOCUS', 'DOZE', 'QUESTION', 'FRIENDS'][step % 4],
          menus: { MORNING: menus[step % 7], AFTERNOON: menus[(step + 1) % 7], NIGHT: menus[(step + 2) % 7] },
        };
      }
      return { type: 'DAY' };
    }
  }
}

async function playYear() {
  let view = await call('/api/runs', { seed });
  let step = 0;
  while (view.phase !== 'FINISHED') {
    view = (await call(`/api/runs/${view.runId}/actions`, choose(view, step))).view;
    step++;
  }
  // 다시 불러오기(시드 + 기록 재생)도 같은 결과여야 한다
  const reloaded = await call(`/api/runs/${view.runId}`);
  if (reloaded.fingerprint !== view.fingerprint) throw new Error('재생 결과가 다릅니다');
  return view;
}

const a = await playYear();
const b = await playYear();
const s = a.summary;
console.log(`run A ${a.runId} 행동 ${a.actionCount} 지문 ${a.fingerprint}`);
console.log(`run B ${b.runId} 행동 ${b.actionCount} 지문 ${b.fingerprint}`);
console.log(`요약: ${s.wins}승 ${s.draws}무 ${s.losses}패, 골 ${s.goals}, 도움 ${s.assists}, 리그 ${s.leagueRank}위, 춘계배 ${s.cupResult}, 학업 ${s.academics.toFixed(1)}, 평판 ${s.reputation}`);
if (a.fingerprint !== b.fingerprint || JSON.stringify(a.summary) !== JSON.stringify(b.summary)) {
  console.error('불일치!');
  process.exit(1);
}
console.log('같은 시드 + 같은 선택 순서 → 같은 결과 ✔');

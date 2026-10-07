import type { ActionOutcome, ActionRequest, GameView, Meta, SchoolList } from './types';

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      message = body.detail ?? body.message ?? message;
    } catch {
      // 본문이 JSON 이 아니면 상태 문구를 쓴다
    }
    throw new ApiError(res.status, message);
  }
  return res.json() as Promise<T>;
}

export const api = {
  meta: () => request<Meta>('/api/meta'),
  /** 학교 이름은 시드로 정해진다. seed 가 없으면 서버가 무작위 시드를 정해 함께 돌려준다 */
  schools: (seed?: number) => request<SchoolList>(seed === undefined ? '/api/schools' : `/api/schools?seed=${seed}`),
  /** schoolId 가 null 이면 설정 파일의 기본 학교 */
  createRun: (seed: number, schoolId: number | null) =>
    request<GameView>('/api/runs', { method: 'POST', body: JSON.stringify({ seed, schoolId }) }),
  getRun: (runId: string) => request<GameView>(`/api/runs/${runId}`),
  act: (runId: string, action: ActionRequest) =>
    request<{ view: GameView; outcome: ActionOutcome }>(`/api/runs/${runId}/actions`, {
      method: 'POST',
      body: JSON.stringify(action),
    }),
};

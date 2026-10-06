import type { ActionOutcome, ActionRequest, GameView, Meta } from './types';

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
  createRun: (seed?: number) =>
    request<GameView>('/api/runs', { method: 'POST', body: JSON.stringify(seed === undefined ? {} : { seed }) }),
  getRun: (runId: string) => request<GameView>(`/api/runs/${runId}`),
  act: (runId: string, action: ActionRequest) =>
    request<{ view: GameView; outcome: ActionOutcome }>(`/api/runs/${runId}/actions`, {
      method: 'POST',
      body: JSON.stringify(action),
    }),
};

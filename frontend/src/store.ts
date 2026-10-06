import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { TrainingSlot } from './types';

// 화면 상태만 둔다. 게임 값(체력, 능력치, 결과 등)은 여기 두지 않는다.

export interface DayDraft {
  dawn?: string;
  classAttitude?: string;
  menus: Partial<Record<TrainingSlot, string>>;
}

interface UiState {
  /** 이어 할 판 */
  runId: string | null;
  setRunId: (id: string | null) => void;

  /** 아직 보내지 않은 오늘의 선택 변경 */
  draft: DayDraft;
  setDraftDawn: (v: string) => void;
  setDraftClass: (v: string) => void;
  setDraftMenu: (slot: TrainingSlot, id: string) => void;
  resetDraft: () => void;

  /** 일요일 화면에서 고른 행동 */
  sundayActivity: string;
  meetTarget: string;
  setSundayActivity: (v: string) => void;
  setMeetTarget: (v: string) => void;

  /** 경기 중계 재생: 판의 몇 번째 경기를 몇 줄까지 보여 줬는지 */
  playback: { matchIndex: number; shown: number; skipped: boolean } | null;
  startPlayback: (matchIndex: number) => void;
  showMatch: (matchIndex: number) => void;
  advancePlayback: () => void;
  skipPlayback: () => void;
  clearPlayback: () => void;

  /** 오른쪽 패널 탭 */
  sideTab: 'stats' | 'league' | 'matches';
  setSideTab: (t: UiState['sideTab']) => void;
}

const emptyDraft = (): DayDraft => ({ menus: {} });

export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      runId: null,
      setRunId: (runId) => set({ runId, draft: emptyDraft(), playback: null }),

      draft: emptyDraft(),
      setDraftDawn: (dawn) => set((s) => ({ draft: { ...s.draft, dawn } })),
      setDraftClass: (classAttitude) => set((s) => ({ draft: { ...s.draft, classAttitude } })),
      setDraftMenu: (slot, id) => set((s) => ({ draft: { ...s.draft, menus: { ...s.draft.menus, [slot]: id } } })),
      resetDraft: () => set({ draft: emptyDraft() }),

      sundayActivity: 'REST',
      meetTarget: 'coach',
      setSundayActivity: (sundayActivity) => set({ sundayActivity }),
      setMeetTarget: (meetTarget) => set({ meetTarget }),

      playback: null,
      startPlayback: (matchIndex) => set({ playback: { matchIndex, shown: 1, skipped: false } }),
      showMatch: (matchIndex) => set({ playback: { matchIndex, shown: 0, skipped: true } }),
      clearPlayback: () => set({ playback: null }),
      advancePlayback: () => set((s) => (s.playback ? { playback: { ...s.playback, shown: s.playback.shown + 1 } } : {})),
      skipPlayback: () => set((s) => (s.playback ? { playback: { ...s.playback, skipped: true } } : {})),

      sideTab: 'stats',
      setSideTab: (sideTab) => set({ sideTab }),
    }),
    {
      name: 'soccer-game-ui',
      // 판 id 와 탭만 기억한다
      partialize: (s) => ({ runId: s.runId, sideTab: s.sideTab }),
    },
  ),
);

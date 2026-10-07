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

  /**
   * 경기 중계 모달: target 은 진행 중 경기('live') 또는 판의 몇 번째 경기, shown 은 지금까지 보여 준 줄 수.
   */
  playback: { target: 'live' | number; shown: number } | null;
  startLive: () => void;
  startPlayback: (matchIndex: number, shown?: number) => void;
  showMatch: (matchIndex: number) => void;
  setShown: (shown: number) => void;
  clearPlayback: () => void;

  /** 오른쪽 패널 탭 */
  sideTab: 'stats' | 'growth' | 'league' | 'matches';
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
      startLive: () => set({ playback: { target: 'live', shown: 0 } }),
      startPlayback: (matchIndex, shown = 0) => set({ playback: { target: matchIndex, shown } }),
      showMatch: (matchIndex) => set({ playback: { target: matchIndex, shown: Number.MAX_SAFE_INTEGER } }),
      setShown: (shown) => set((s) => (s.playback ? { playback: { ...s.playback, shown } } : {})),
      clearPlayback: () => set({ playback: null }),

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

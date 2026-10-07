// 서버 GameView 와 같은 모양. 값은 모두 서버가 계산한 것이다.

export type Phase = 'MATCH' | 'EVENT' | 'WEEKDAY' | 'SATURDAY' | 'SUNDAY' | 'FINISHED';
export type MatchRole = 'starter' | 'sub' | 'bench' | 'absent';
export type TrainingSlot = 'MORNING' | 'AFTERNOON' | 'NIGHT';

export interface LogEntry { slot: string; text: string }

export interface SceneLog {
  minute: number; sceneId: string; sceneName: string; extra: boolean; probability: number;
  success: boolean; result: string; text: string; modifiers: string[];
}

export interface TimelineEntry {
  minute: number; kind: string; text: string; ourScore: number; theirScore: number;
  probability: number | null; modifiers: string[] | null;
}

export interface ClutchLog {
  momentId: string; title: string; situation: string; minute: number; choiceIndex: number; choiceText: string;
  style: string; probability: number; success: boolean; result: string;
}

export interface MatchRecord {
  competition: 'LEAGUE' | 'CUP'; competitionName: string; tournamentKey: string | null; roundLabel: string; week: number; dateLabel: string;
  opponentId: number; opponentName: string; opponentStrength: number; opponentDefender: string;
  home: boolean; role: MatchRole; selectionScore: number;
  teamGoals: number; opponentGoals: number; playerGoals: number; playerAssists: number; pressGoals: number;
  clutchTeamGoals: number;
  ourScore: number; theirScore: number; penaltyWin: boolean | null; result: 'W' | 'D' | 'L';
  rating: number | null; reputationGained: number; passiveGrowth: string | null; passiveGrowthAmount: number | null;
  scenes: SceneLog[]; timeline: TimelineEntry[]; clutch: ClutchLog | null;
}

export interface ActionOutcome {
  dateLabel: string; log: LogEntry[]; match: MatchRecord | null; newEvents: string[]; partial: boolean;
}

export interface ClutchChoiceView {
  index: number; text: string; style: string; styleLabel: string; stats: string[]; probability: number;
  modifiers: string[]; reward: string; failRating: number; trait: string | null;
}

export interface LiveMatchView {
  competition: string; roundLabel: string; dateLabel: string; opponentName: string; opponentType: string;
  opponentStrength: number; opponentDefender: string; home: boolean; role: string; selectionScore: number;
  timeline: TimelineEntry[];
  clutch: { momentId: string; title: string; situation: string; minute: number; choices: ClutchChoiceView[] };
}

export interface Option { key: string; name: string; description: string | null }
export interface MenuOption { id: string; name: string; stats: string[]; growthMultiplier: number }

export interface SlotView {
  key: string; label: string; kind: 'DAWN' | 'CLASS' | 'TRAINING' | 'FIXED';
  activity: string; menuSlot: TrainingSlot | null; note: string | null;
}

export interface StatView {
  key: string; name: string; group: string; primary: boolean; passive: boolean;
  value: number; initial: number; grade: string;
}

export interface StandingView {
  rank: number; schoolId: number; name: string; typeName: string; strength: number; played: number;
  won: number; drawn: number; lost: number; goalsFor: number; goalsAgainst: number; goalDiff: number;
  points: number; player: boolean;
}

export interface StatChange {
  key: string; name: string; primary: boolean; passive: boolean; start: number; end: number;
  startGrade: string; endGrade: string;
}


export interface SeasonSummary {
  stats: StatChange[]; roles: Record<MatchRole, number>; appearances: number; goals: number; assists: number;
  averageRating: number | null; wins: number; draws: number; losses: number; leagueRank: number;
  tournaments: TournamentResult[]; academics: number; makeupWeeks: number; makeupDays: number;
  reputation: number; money: number; injuries: number; exclusions: number; events: number;
}

export interface GameView {
  runId: string; seed: number; actionCount: number; fingerprint: string; phase: Phase;
  date: { week: number; weeksPerYear: number; month: number; weekOfMonth: number; day: string; dayLabel: string; label: string; term: string; vacation: boolean };
  school: { id: number; name: string; typeName: string; strength: number; region: string };
  resources: {
    stamina: number; maxStamina: number; condition: number; conditionName: string; money: number;
    academics: number; reputation: number; affinity: Record<string, number>; friendMultiplier: number;
    injured: boolean; injuredUntil: string | null; selectionScore: number; expectedRole: string;
    staminaInfo: { nightRecovery: number; sundayExtraRecovery: number; lowBelow: number; maxFormula: string };
    academicsInfo: { makeupBelow: number; warningBelow: number; warning: string | null };
  };
  stats: StatView[];
  selections: { dawn: string; classAttitude: string; menus: Record<TrainingSlot, string> };
  slots: SlotView[];
  matchToday: {
    competition: string; round: string; opponentId: number; opponentName: string; opponentType: string;
    opponentStrength: number; opponentDefender: string; home: boolean;
  } | null;
  options: { dawn: Option[]; classAttitudes: Option[]; menus: MenuOption[]; sundayActivities: Option[]; meetTargets: Option[] };
  league: StandingView[];
  schedule: ScheduleView[];
  nextMatch: { days: number; dateLabel: string; competition: string } | null;
  traits: TraitView[];
  bonds: BondView[];
  liveMatch: LiveMatchView | null;
  pendingEvent: {
    eventId: string; axis: string; title: string; body: string | null; source: string; sourceLabel: string;
    choices: { text: string; trait: string | null }[];
  } | null;
  lastOutcome: ActionOutcome | null;
  matches: MatchRecord[];
  summary: SeasonSummary;
}

export interface TournamentResult {
  key: string; name: string; period: string; stage: string; text: string; matches: number;
}

export interface ScheduleView {
  key: string; name: string; period: string; status: string; result: string; matchesPlayed: number;
  nextMatch: string | null;
}

export interface TraitView {
  key: string; name: string; score: number; tier: number; nextAt: number | null; remaining: number | null;
  effect: string; tierEffects: string[];
}

export interface BondView {
  axis: string; label: string; locked: boolean; affinity: number | null; tier: number; nextAt: number | null;
  effect: string; tierEffects: string[];
}

export interface Meta { position: string; archetype: string; weeksPerYear: number; startLabel: string; endLabel: string }

export type ActionRequest =
  | { type: 'DAY'; dawn?: string; classAttitude?: string; menus?: Partial<Record<TrainingSlot, string>> }
  | { type: 'SUNDAY'; activity: string; meetTarget?: string }
  | { type: 'EVENT_CHOICE'; eventId: string; choiceIndex: number }
  | { type: 'CLUTCH_CHOICE'; momentId: string; choiceIndex: number };

import type { GameView } from '../types';
import { useAct } from '../queries';
import { useUiStore } from '../store';
import Header from './Header';
import DayPanel from './DayPanel';
import SaturdayPanel from './SaturdayPanel';
import SundayPanel from './SundayPanel';
import EventPanel from './EventPanel';
import MatchViewer from './MatchViewer';
import RecentLog from './RecentLog';
import SidePanel from './SidePanel';
import SeasonSummaryView from './SeasonSummaryView';

export default function GameScreen({ view }: { view: GameView }) {
  const act = useAct(view.runId);
  const playback = useUiStore((s) => s.playback);
  const setRunId = useUiStore((s) => s.setRunId);
  const match = playback ? view.matches[playback.matchIndex] : undefined;

  return (
    <div className="layout">
      <Header view={view} onQuit={() => setRunId(null)} />
      <main className="main">
        <section className="col">
          {act.isError && <div className="error box">{act.error.message}</div>}
          {view.phase === 'EVENT' && view.pendingEvent && (
            <EventPanel event={view.pendingEvent} disabled={act.isPending} onChoose={(i) =>
              act.mutate({ type: 'EVENT_CHOICE', eventId: view.pendingEvent!.eventId, choiceIndex: i })} />
          )}
          {view.phase === 'WEEKDAY' && <DayPanel view={view} pending={act.isPending} onSubmit={act.mutate} />}
          {view.phase === 'SATURDAY' && <SaturdayPanel view={view} pending={act.isPending} onSubmit={act.mutate} />}
          {view.phase === 'SUNDAY' && <SundayPanel view={view} pending={act.isPending} onSubmit={act.mutate} />}
          {view.phase === 'FINISHED' && <SeasonSummaryView view={view} />}
          {match && playback && <MatchViewer match={match} cupName={view.cup.name} />}
          <RecentLog runId={view.runId} />
        </section>
        <aside className="col side">
          <SidePanel view={view} />
        </aside>
      </main>
    </div>
  );
}

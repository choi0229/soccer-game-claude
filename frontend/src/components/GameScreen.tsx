import { useEffect } from 'react';
import type { GameView } from '../types';
import { useAct } from '../queries';
import { useUiStore } from '../store';
import Header from './Header';
import DayPanel from './DayPanel';
import SaturdayPanel from './SaturdayPanel';
import SundayPanel from './SundayPanel';
import EventModal from './EventModal';
import MatchModal from './MatchModal';
import RecentLog from './RecentLog';
import SidePanel from './SidePanel';
import SeasonSummaryView from './SeasonSummaryView';

export default function GameScreen({ view }: { view: GameView }) {
  const act = useAct(view.runId);
  const playback = useUiStore((s) => s.playback);
  const startLive = useUiStore((s) => s.startLive);
  const setRunId = useUiStore((s) => s.setRunId);

  // 새로고침 등으로 진행 중 경기를 잃었으면 다시 띄운다
  useEffect(() => {
    if (view.liveMatch && playback?.target !== 'live') {
      startLive();
    }
  }, [view.liveMatch, playback?.target, startLive]);

  const matchOpen = playback !== null && (playback.target !== 'live' || view.liveMatch !== null);
  const eventOpen = !matchOpen && view.phase === 'EVENT' && view.pendingEvent !== null;
  const modalOpen = matchOpen || eventOpen;

  return (
    <div className="layout">
      <div inert={modalOpen}>
        <Header view={view} onQuit={() => setRunId(null)} />
        <main className="main">
          <section className="col">
            {act.isError && !modalOpen && <div className="error box">{act.error.message}</div>}
            {view.phase === 'WEEKDAY' && <DayPanel view={view} pending={act.isPending} onSubmit={act.mutate} />}
            {view.phase === 'SATURDAY' && <SaturdayPanel view={view} pending={act.isPending} onSubmit={act.mutate} />}
            {view.phase === 'SUNDAY' && <SundayPanel view={view} pending={act.isPending} onSubmit={act.mutate} />}
            {(view.phase === 'EVENT' || view.phase === 'MATCH') && (
              <div className="box muted">{view.phase === 'MATCH' ? '경기 진행 중' : '이벤트 선택을 기다리는 중'}</div>
            )}
            {view.phase === 'FINISHED' && <SeasonSummaryView view={view} />}
            <RecentLog runId={view.runId} />
          </section>
          <aside className="col side">
            <SidePanel view={view} />
          </aside>
        </main>
      </div>
      {matchOpen && (
        <MatchModal view={view} pending={act.isPending}
          onClutch={(momentId, choiceIndex) => act.mutate({ type: 'CLUTCH_CHOICE', momentId, choiceIndex })} />
      )}
      {eventOpen && view.pendingEvent && (
        <EventModal event={view.pendingEvent} disabled={act.isPending} onChoose={(i) =>
          act.mutate({ type: 'EVENT_CHOICE', eventId: view.pendingEvent!.eventId, choiceIndex: i })} />
      )}
      {modalOpen && act.isError && <div className="toast error">{act.error.message}</div>}
    </div>
  );
}

import { useEffect } from 'react';
import type { GameView } from '../types';
import { useAct } from '../queries';
import { useUiStore } from '../store';
import Masthead from './Masthead';
import PageHeader from './PageHeader';
import RoutinePanel from './RoutinePanel';
import Journal from './Journal';
import Relationships from './Relationships';
import SidePanel from './SidePanel';
import EventModal from './EventModal';
import MatchModal from './MatchModal';
import SeasonSummaryView from './SeasonSummaryView';

export default function GameScreen({ view }: { view: GameView }) {
  const act = useAct(view.runId);
  const playback = useUiStore((s) => s.playback);
  const startLive = useUiStore((s) => s.startLive);
  const setRunId = useUiStore((s) => s.setRunId);

  // 새로고침 등으로 재생 상태가 비어 있는데 진행 중 경기가 있으면 다시 띄운다.
  // 재생 상태가 있을 때는 건드리지 않는다: 승부처 선택 직후에는 재생 상태가 먼저 바뀌고
  // 화면 데이터가 한 박자 늦게 오므로, 이전 데이터의 liveMatch 를 보고 되돌리면 결과 화면이 닫힌다.
  useEffect(() => {
    if (view.liveMatch && playback === null) {
      startLive();
    }
  }, [view.liveMatch, playback, startLive]);

  const matchOpen = playback !== null && (playback.target !== 'live' || view.liveMatch !== null);
  const eventOpen = !matchOpen && view.phase === 'EVENT' && view.pendingEvent !== null;
  const modalOpen = matchOpen || eventOpen;

  return (
    <>
      <div inert={modalOpen}>
        <Masthead />
        <main className="dashboard">
          <PageHeader view={view} />
          <div className="main-grid">
            <div className="daily-column">
              {view.phase === 'FINISHED'
                ? <SeasonSummaryView view={view} />
                : <RoutinePanel view={view} pending={act.isPending} onSubmit={act.mutate} />}
              {act.isError && !modalOpen && <p className="error" role="alert">{act.error.message}</p>}
              <Journal runId={view.runId} />
              <Relationships view={view} />
            </div>
            <SidePanel view={view} />
          </div>
          <footer className="page-footer">
            <span>{view.school.name} · 고1 · 시드 {view.seed} · 행동 {view.actionCount} · 지문 {view.fingerprint}</span>
            <div><button className="link" onClick={() => setRunId(null)}>시즌 시작 화면</button></div>
          </footer>
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
      {modalOpen && act.isError && <div className="toast error" role="alert">{act.error.message}</div>}
    </>
  );
}

import { useEffect } from 'react';
import { ApiError } from './api';
import { useRun } from './queries';
import { useUiStore } from './store';
import StartScreen from './components/StartScreen';
import GameScreen from './components/GameScreen';

export default function App() {
  const runId = useUiStore((s) => s.runId);
  const setRunId = useUiStore((s) => s.setRunId);
  const run = useRun(runId);

  // 저장해 둔 판이 서버에 없으면 시작 화면으로
  useEffect(() => {
    if (run.error instanceof ApiError && run.error.status === 404) {
      setRunId(null);
    }
  }, [run.error, setRunId]);

  if (!runId) {
    return <StartScreen />;
  }
  if (run.isPending) {
    return <div className="center">불러오는 중…</div>;
  }
  if (run.isError) {
    return (
      <div className="center">
        <p>판을 불러오지 못했습니다: {run.error.message}</p>
        <button onClick={() => setRunId(null)}>시작 화면으로</button>
      </div>
    );
  }
  return <GameScreen view={run.data} />;
}

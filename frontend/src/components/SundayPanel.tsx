import type { ActionRequest, GameView } from '../types';
import { useUiStore } from '../store';
import { f0 } from '../format';

interface Props {
  view: GameView;
  pending: boolean;
  onSubmit: (a: ActionRequest) => void;
}

export default function SundayPanel({ view, pending, onSubmit }: Props) {
  const activity = useUiStore((s) => s.sundayActivity);
  const target = useUiStore((s) => s.meetTarget);
  const setActivity = useUiStore((s) => s.setSundayActivity);
  const setTarget = useUiStore((s) => s.setMeetTarget);

  const submit = () =>
    onSubmit(activity === 'MEET' ? { type: 'SUNDAY', activity, meetTarget: target } : { type: 'SUNDAY', activity });

  return (
    <div className="box">
      <h2>일요일 — 자유 행동</h2>
      {view.options.sundayActivities.map((o) => (
        <label key={o.key} className="radio">
          <input type="radio" name="sunday" checked={activity === o.key} onChange={() => setActivity(o.key)} />
          <b>{o.name}</b> <span className="muted small">{o.description}</span>
          {o.key === 'MEET' && activity === 'MEET' && (
            <select value={target} onChange={(e) => setTarget(e.target.value)}>
              {view.options.meetTargets.map((t) => <option key={t.key} value={t.key}>{t.name}</option>)}
            </select>
          )}
        </label>
      ))}
      <p className="muted small">
        밤에 체력이 자동으로 회복되고(밤 회복 +{f0(view.resources.staminaInfo.nightRecovery)}{view.date.vacation ? ' (방학)' : ''}, 일요일 추가 +{f0(view.resources.staminaInfo.sundayExtraRecovery)}), 이벤트가 생길 수 있습니다.
      </p>
      <button className="primary" disabled={pending} onClick={submit}>
        {pending ? '진행 중…' : '확인 — 다음 주로'}
      </button>
    </div>
  );
}

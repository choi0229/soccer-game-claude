import type { ActionRequest, GameView, SlotView } from '../types';
import { useUiStore } from '../store';

interface Props {
  view: GameView;
  pending: boolean;
  onSubmit: (a: ActionRequest) => void;
}

/** 월~금: 하루 4칸을 한 번에 보여 주고 확인 버튼 하나로 다음 날로 넘어간다. */
export default function DayPanel({ view, pending, onSubmit }: Props) {
  const draft = useUiStore((s) => s.draft);
  const setDawn = useUiStore((s) => s.setDraftDawn);
  const setClass = useUiStore((s) => s.setDraftClass);
  const setMenu = useUiStore((s) => s.setDraftMenu);
  const sel = view.selections;

  const dawn = draft.dawn ?? sel.dawn;
  const classAttitude = draft.classAttitude ?? sel.classAttitude;
  const menuOf = (slot: SlotView) => (slot.menuSlot ? draft.menus[slot.menuSlot] ?? sel.menus[slot.menuSlot] : '');

  const submit = () => {
    // 그날 바꾼 선택만 보낸다
    const action: ActionRequest = { type: 'DAY' };
    if (dawn !== sel.dawn) action.dawn = dawn;
    if (classAttitude !== sel.classAttitude) action.classAttitude = classAttitude;
    const menus = Object.fromEntries(Object.entries(draft.menus).filter(([slot, id]) => sel.menus[slot as keyof typeof sel.menus] !== id));
    if (Object.keys(menus).length > 0) action.menus = menus;
    onSubmit(action);
  };

  const describe = (key: string, list: { key: string; description: string | null }[]) =>
    list.find((o) => o.key === key)?.description;

  return (
    <div className="box">
      <h2>오늘 일과 — {view.date.dayLabel}요일</h2>
      {view.matchToday && (
        <p className="match-today">
          오늘 경기: {view.matchToday.competition} {view.matchToday.round} vs {view.matchToday.opponentName} ({view.matchToday.opponentType}, 전력 {view.matchToday.opponentStrength}, 핵심 수비 {view.matchToday.opponentDefender})
        </p>
      )}
      <table className="slots">
        <tbody>
          {view.slots.map((slot) => (
            <tr key={slot.key}>
              <th>{slot.label}</th>
              <td>
                {slot.kind === 'DAWN' && (
                  <>
                    <select value={dawn} onChange={(e) => setDawn(e.target.value)}>
                      {view.options.dawn.map((o) => <option key={o.key} value={o.key}>{o.name}</option>)}
                    </select>{' '}
                    <span className="muted small">{describe(dawn, view.options.dawn)}</span>
                  </>
                )}
                {slot.kind === 'CLASS' && (
                  <>
                    <select value={classAttitude} onChange={(e) => setClass(e.target.value)}>
                      {view.options.classAttitudes.map((o) => <option key={o.key} value={o.key}>{o.name}</option>)}
                    </select>{' '}
                    <span className="muted small">{describe(classAttitude, view.options.classAttitudes)}</span>
                  </>
                )}
                {slot.kind === 'TRAINING' && slot.menuSlot && (
                  <>
                    <span className="activity">{slot.activity}</span>{' '}
                    <select value={menuOf(slot)} onChange={(e) => setMenu(slot.menuSlot!, e.target.value)}>
                      {view.options.menus.map((m) => (
                        <option key={m.id} value={m.id}>
                          {m.name} ({m.stats.join(', ')}{m.growthMultiplier !== 1 ? ` ×${m.growthMultiplier}` : ''})
                        </option>
                      ))}
                    </select>
                  </>
                )}
                {slot.kind === 'FIXED' && <span className="activity">{slot.activity}</span>}
                {slot.note && <div className="muted small">{slot.note}</div>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button className="primary" disabled={pending} onClick={submit}>
        {pending ? '진행 중…' : '확인 — 다음 날로'}
      </button>
    </div>
  );
}

import type { ActionRequest, GameView, SlotView } from '../types';
import { useUiStore } from '../store';

interface Props {
  view: GameView;
  pending: boolean;
  onSubmit: (a: ActionRequest) => void;
}

/** 오늘의 일과: 평일 4칸, 토요일 경기일, 일요일 자유 행동. 확인 버튼 하나로 진행한다. */
export default function RoutinePanel({ view, pending, onSubmit }: Props) {
  const heading = {
    WEEKDAY: { eyebrow: 'Daily routine', title: `오늘의 일과 · ${view.date.dayLabel}요일`, badge: `${view.slots.length}개 시간대` },
    SATURDAY: { eyebrow: 'Saturday', title: '토요일, 경기일', badge: view.matchToday ? '경기 일정' : '경기 없음' },
    SUNDAY: { eyebrow: 'Sunday', title: '일요일, 나를 위한 시간', badge: '자유 행동 1회' },
    EVENT: { eyebrow: 'Waiting', title: '선택을 기다리는 중', badge: '이벤트' },
    MATCH: { eyebrow: 'Match', title: '경기 진행 중', badge: '승부처' },
    FINISHED: { eyebrow: '', title: '', badge: '' },
  }[view.phase];

  return (
    <section className="panel routine">
      <div className="panel-heading">
        <div>
          <div className="eyebrow">{heading.eyebrow}</div>
          <h2>{heading.title}</h2>
        </div>
        <span className="badge">{heading.badge}</span>
      </div>
      {view.phase === 'WEEKDAY' && <Weekday view={view} pending={pending} onSubmit={onSubmit} />}
      {view.phase === 'SATURDAY' && <Saturday view={view} pending={pending} onSubmit={onSubmit} />}
      {view.phase === 'SUNDAY' && <Sunday view={view} pending={pending} onSubmit={onSubmit} />}
      {(view.phase === 'EVENT' || view.phase === 'MATCH') && (
        <p className="weekend-note">{view.phase === 'MATCH' ? '경기 창에서 승부처를 골라 주세요.' : '이벤트 창에서 선택하면 일과가 이어집니다.'}</p>
      )}
    </section>
  );
}

function MatchPreview({ view }: { view: GameView }) {
  const m = view.matchToday;
  if (!m) return null;
  return (
    <div className="match-preview">
      오늘 경기 · <b>{m.competition} {m.round}</b> {m.home ? '홈' : '원정'} vs <b>{m.opponentName}</b>
      <span className="tiny"> ({m.opponentType} · 전력 {m.opponentStrength} · 핵심 수비 {m.opponentDefender} · 예상 {view.resources.expectedRole})</span>
    </div>
  );
}

function Weekday({ view, pending, onSubmit }: Props) {
  const draft = useUiStore((s) => s.draft);
  const setDawn = useUiStore((s) => s.setDraftDawn);
  const setClass = useUiStore((s) => s.setDraftClass);
  const setMenu = useUiStore((s) => s.setDraftMenu);
  const sel = view.selections;
  const dawn = draft.dawn ?? sel.dawn;
  const classAttitude = draft.classAttitude ?? sel.classAttitude;
  const menuOf = (slot: SlotView) => (slot.menuSlot ? draft.menus[slot.menuSlot] ?? sel.menus[slot.menuSlot] : '');
  const describe = (key: string, list: { key: string; description: string | null }[]) =>
    list.find((o) => o.key === key)?.description;

  const submit = () => {
    // 그날 바꾼 선택만 보낸다
    const action: ActionRequest = { type: 'DAY' };
    if (dawn !== sel.dawn) action.dawn = dawn;
    if (classAttitude !== sel.classAttitude) action.classAttitude = classAttitude;
    const menus = Object.fromEntries(
      Object.entries(draft.menus).filter(([slot, id]) => sel.menus[slot as keyof typeof sel.menus] !== id),
    );
    if (Object.keys(menus).length > 0) action.menus = menus;
    onSubmit(action);
  };

  return (
    <>
      <MatchPreview view={view} />
      <div className="slots">
        {view.slots.map((slot, i) => {
          const id = `slot-${slot.key}`;
          return (
            <div className="slot" key={slot.key}>
              <span className="slot-number">0{i + 1}</span>
              <div className="slot-body">
                {slot.kind === 'DAWN' && (
                  <>
                    <label htmlFor={id}>{slot.label}</label>
                    <select id={id} value={dawn} disabled={pending} onChange={(e) => setDawn(e.target.value)}>
                      {view.options.dawn.map((o) => <option key={o.key} value={o.key}>{o.name}</option>)}
                    </select>
                    <span className="slot-note">{describe(dawn, view.options.dawn)}{slot.note && ` · ${slot.note}`}</span>
                  </>
                )}
                {slot.kind === 'CLASS' && (
                  <>
                    <label htmlFor={id}>{slot.label} · 수업 태도</label>
                    <select id={id} value={classAttitude} disabled={pending} onChange={(e) => setClass(e.target.value)}>
                      {view.options.classAttitudes.map((o) => <option key={o.key} value={o.key}>{o.name}</option>)}
                    </select>
                    <span className="slot-note">{describe(classAttitude, view.options.classAttitudes)}</span>
                  </>
                )}
                {slot.kind === 'TRAINING' && slot.menuSlot && (
                  <>
                    <label htmlFor={id}>{slot.label} · {slot.activity}</label>
                    <select id={id} value={menuOf(slot)} disabled={pending} onChange={(e) => setMenu(slot.menuSlot!, e.target.value)}>
                      {view.options.menus.map((m) => (
                        <option key={m.id} value={m.id}>
                          {m.name} · {m.stats.join(', ')}{m.growthMultiplier !== 1 ? ` ×${m.growthMultiplier}` : ''}
                        </option>
                      ))}
                    </select>
                    {slot.note && <span className="slot-note">{slot.note}</span>}
                  </>
                )}
                {slot.kind === 'FIXED' && (
                  <>
                    <span className="slot-label">{slot.label}</span>
                    <div className={`fixed ${slot.activity === '나머지 공부' ? 'makeup' : slot.activity.includes('경기') ? 'match' : ''}`}>
                      {slot.activity}
                    </div>
                    {slot.note && <span className="slot-note">{slot.note}</span>}
                  </>
                )}
              </div>
            </div>
          );
        })}
      </div>
      <div className="routine-footer">
        <p className="muted">고른 선택은 바꾸기 전까지 다음 날에도 유지됩니다.</p>
        <button className="primary" onClick={submit} disabled={pending}>
          {pending ? '하루를 보내는 중…' : view.matchToday ? '확인하고 경기 진행 →' : '확인하고 하루 보내기 →'}
        </button>
      </div>
    </>
  );
}

function Saturday({ view, pending, onSubmit }: Props) {
  return (
    <>
      <MatchPreview view={view} />
      {!view.matchToday && <p className="weekend-note">이번 토요일에는 예정된 경기가 없습니다.</p>}
      <div className="routine-footer">
        <p className="muted">{view.matchToday ? '출전과 결과가 오늘 정해집니다.' : '조용히 하루를 보냅니다.'}</p>
        <button className="primary" disabled={pending} onClick={() => onSubmit({ type: 'DAY' })}>
          {pending ? '진행 중…' : view.matchToday ? '확인하고 경기 진행 →' : '확인하고 하루 보내기 →'}
        </button>
      </div>
    </>
  );
}

function Sunday({ view, pending, onSubmit }: Props) {
  const activity = useUiStore((s) => s.sundayActivity);
  const target = useUiStore((s) => s.meetTarget);
  const setActivity = useUiStore((s) => s.setSundayActivity);
  const setTarget = useUiStore((s) => s.setMeetTarget);
  const targets = view.options.meetTargets;
  const meetTarget = targets.some((t) => t.key === target) ? target : targets[0]?.key;
  const info = view.resources.staminaInfo;

  const submit = () =>
    onSubmit(activity === 'MEET' ? { type: 'SUNDAY', activity, meetTarget } : { type: 'SUNDAY', activity });

  return (
    <>
      <div className="sunday-options">
        {view.options.sundayActivities.map((o) => (
          <label key={o.key} className={activity === o.key ? 'chosen' : ''}>
            <input type="radio" name="sunday" checked={activity === o.key} onChange={() => setActivity(o.key)} />
            <span>
              <b>{o.name}</b>
              <small>{o.description}</small>
            </span>
            {o.key === 'MEET' && activity === 'MEET' && (
              <select aria-label="만날 사람" value={meetTarget} onChange={(e) => setTarget(e.target.value)}>
                {targets.map((t) => <option key={t.key} value={t.key}>{t.name}</option>)}
              </select>
            )}
          </label>
        ))}
      </div>
      <div className="routine-footer">
        <p className="muted">
          밤에 체력이 회복됩니다 (밤 회복 +{info.nightRecovery}{view.date.vacation ? ', 방학' : ''} · 일요일 추가 +{info.sundayExtraRecovery}). 이벤트가 생길 수 있습니다.
        </p>
        <button className="primary" disabled={pending} onClick={submit}>
          {pending ? '진행 중…' : '확인하고 다음 주로 →'}
        </button>
      </div>
    </>
  );
}

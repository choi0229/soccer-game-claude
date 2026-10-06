import type { GameView } from '../types';

interface Props {
  event: NonNullable<GameView['pendingEvent']>;
  disabled: boolean;
  onChoose: (index: number) => void;
}

const sourceLabel: Record<string, string> = { SUNDAY: '일요일', MEET: '만남', CLASS: '수업 시간' };

export default function EventPanel({ event, disabled, onChoose }: Props) {
  return (
    <div className="box event">
      <div className="muted small">[{event.axis}] {sourceLabel[event.source] ?? event.source}</div>
      <h2>{event.title}</h2>
      {event.body && <p className="body">{event.body}</p>}
      <div className="choices">
        {event.choices.map((c, i) => (
          <button key={i} disabled={disabled} onClick={() => onChoose(i)}>
            {c.text}
            {c.trait && <span className="trait-badge">{c.trait} +1</span>}
          </button>
        ))}
      </div>
      <p className="muted small">선택해야 다음으로 넘어갈 수 있습니다.</p>
    </div>
  );
}

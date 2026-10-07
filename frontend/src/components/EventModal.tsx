import type { GameView } from '../types';
import Modal from './Modal';

interface Props {
  event: NonNullable<GameView['pendingEvent']>;
  disabled: boolean;
  onChoose: (index: number) => void;
}

export default function EventModal({ event, disabled, onChoose }: Props) {
  return (
    <Modal title={event.title}>
      <div className="muted small">[{event.axis}] {event.sourceLabel}</div>
      <h2>{event.title}</h2>
      {event.body && <p className="body">{event.body}</p>}
      <ol className="choices">
        {event.choices.map((c, i) => (
          <li key={i}>
            <button disabled={disabled} onClick={() => onChoose(i)}>
              <span className="choice-no">{i + 1}.</span> {c.text}
              {c.trait && <span className="trait-badge">{c.trait} +1</span>}
            </button>
          </li>
        ))}
      </ol>
      <p className="muted small">선택해야 다음으로 넘어갈 수 있습니다.</p>
    </Modal>
  );
}

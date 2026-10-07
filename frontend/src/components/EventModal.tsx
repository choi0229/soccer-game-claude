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
      <div className="eyebrow">{event.axis} / {event.sourceLabel}</div>
      <h2>{event.title}</h2>
      {event.body && <p className="event-body">{event.body}</p>}
      <ol className="event-choices">
        {event.choices.map((c, i) => (
          <li key={i}>
            <button disabled={disabled} onClick={() => onChoose(i)}>
              <span className="no">0{i + 1}</span>
              <span className="choice-text">{c.text}</span>
              {c.trait && <span className="trait-badge">{c.trait} +1</span>}
            </button>
          </li>
        ))}
      </ol>
      <small className="muted">선택하면 멈춰 있던 일과가 이어집니다.</small>
    </Modal>
  );
}

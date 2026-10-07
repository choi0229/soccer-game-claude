import type { ReactNode } from 'react';

/** 화면 위에 뜨는 창. 떠 있는 동안 뒤 화면은 GameScreen 에서 inert 로 막는다. */
export default function Modal({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="modal-backdrop">
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        {children}
      </div>
    </div>
  );
}

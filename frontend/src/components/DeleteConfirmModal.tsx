import React, { useEffect, useRef } from 'react';
import { AlertTriangle, X } from 'lucide-react';

interface DeleteConfirmModalProps {
  isOpen: boolean;
  title?: string;
  message?: string;
  entryId?: number | string;
  isDeleting?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export const DeleteConfirmModal: React.FC<DeleteConfirmModalProps> = ({
  isOpen,
  title = 'Delete clipboard entry?',
  message,
  entryId,
  isDeleting = false,
  onConfirm,
  onCancel,
}) => {
  const modalRef = useRef<HTMLDivElement>(null);
  const cancelBtnRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!isOpen) return;

    // Focus cancel button by default for safe interaction
    cancelBtnRef.current?.focus();

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isDeleting) {
        onCancel();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, isDeleting, onCancel]);

  if (!isOpen) {
    return null;
  }

  const defaultMessage = entryId
    ? `Are you sure you want to delete clipboard entry #${entryId}?\nThis action cannot be undone.`
    : 'Are you sure you want to delete this clipboard entry?\nThis action cannot be undone.';

  const displayMessage = message || defaultMessage;

  return (
    <div
      className="modal-backdrop"
      data-testid="delete-modal-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget && !isDeleting) {
          onCancel();
        }
      }}
      role="presentation"
    >
      <div
        className="modal-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        aria-describedby="modal-description"
        ref={modalRef}
        data-testid="delete-confirm-modal"
      >
        <div className="modal-header">
          <div className="modal-title-wrap">
            <div className="modal-warning-icon">
              <AlertTriangle size={18} />
            </div>
            <h3 id="modal-title" className="modal-title">
              {title}
            </h3>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onCancel}
            disabled={isDeleting}
            aria-label="Close dialog"
            data-testid="delete-modal-close"
          >
            <X size={16} />
          </button>
        </div>

        <div className="modal-body">
          <p id="modal-description" className="modal-message">
            {displayMessage.split('\n').map((line, idx) => (
              <React.Fragment key={idx}>
                {line}
                {idx < displayMessage.split('\n').length - 1 && <br />}
              </React.Fragment>
            ))}
          </p>
        </div>

        <div className="modal-footer">
          <button
            type="button"
            ref={cancelBtnRef}
            className="btn-modal-cancel"
            onClick={onCancel}
            disabled={isDeleting}
            data-testid="delete-modal-cancel"
          >
            Cancel
          </button>
          <button
            type="button"
            className="btn-modal-delete"
            onClick={onConfirm}
            disabled={isDeleting}
            data-testid="delete-modal-confirm"
          >
            {isDeleting ? 'Deleting...' : 'Delete'}
          </button>
        </div>
      </div>
    </div>
  );
};

import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { X, ZoomIn, ZoomOut, RotateCcw } from 'lucide-react';

interface ImageViewerModalProps {
  imageUrl: string;
  altText?: string;
  onClose: () => void;
}

export const ImageViewerModal: React.FC<ImageViewerModalProps> = ({
  imageUrl,
  altText = 'Clipboard Image Viewer',
  onClose,
}) => {
  const [zoom, setZoom] = useState(1);

  // Close on Escape key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  // Prevent background body scroll while modal is open
  useEffect(() => {
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = originalOverflow;
    };
  }, []);

  const handleZoomIn = (e: React.MouseEvent) => {
    e.stopPropagation();
    setZoom((prev) => Math.min(Number((prev + 0.25).toFixed(2)), 3.0));
  };

  const handleZoomOut = (e: React.MouseEvent) => {
    e.stopPropagation();
    setZoom((prev) => Math.max(Number((prev - 0.25).toFixed(2)), 0.5));
  };

  const handleResetZoom = (e: React.MouseEvent) => {
    e.stopPropagation();
    setZoom(1);
  };

  const modalContent = (
    <div
      className="image-viewer-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
      role="dialog"
      aria-modal="true"
      aria-label="Image Viewer"
    >
      <div className="image-viewer-toolbar" onClick={(e) => e.stopPropagation()}>
        <div className="image-viewer-controls">
          <button
            type="button"
            className="image-viewer-btn"
            onClick={handleZoomOut}
            disabled={zoom <= 0.5}
            title="Zoom out"
            aria-label="Zoom out"
          >
            <ZoomOut size={16} />
          </button>
          <span className="image-viewer-zoom-label">{Math.round(zoom * 100)}%</span>
          <button
            type="button"
            className="image-viewer-btn"
            onClick={handleZoomIn}
            disabled={zoom >= 3.0}
            title="Zoom in"
            aria-label="Zoom in"
          >
            <ZoomIn size={16} />
          </button>
          <button
            type="button"
            className="image-viewer-btn"
            onClick={handleResetZoom}
            title="Reset zoom"
            aria-label="Reset zoom"
          >
            <RotateCcw size={15} />
          </button>
        </div>

        <button
          type="button"
          className="image-viewer-close-btn"
          onClick={(e) => {
            e.stopPropagation();
            onClose();
          }}
          title="Close (Esc)"
          aria-label="Close image viewer"
        >
          <X size={20} />
        </button>
      </div>

      <div
        className="image-viewer-content"
        onClick={(e) => {
          if (e.target === e.currentTarget) {
            onClose();
          }
        }}
      >
        <img
          src={imageUrl}
          alt={altText}
          className="image-viewer-img"
          style={{
            transform: `scale(${zoom})`,
          }}
          onClick={(e) => e.stopPropagation()}
        />
      </div>
    </div>
  );

  if (typeof document !== 'undefined') {
    return createPortal(modalContent, document.body);
  }
  return modalContent;
};


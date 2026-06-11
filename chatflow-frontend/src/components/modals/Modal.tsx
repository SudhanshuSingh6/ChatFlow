import { useEffect, type ReactNode } from "react";
import { FiX } from "react-icons/fi";

interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
}

/** Centered dialog with backdrop + Escape to close. */
export default function Modal({ title, onClose, children }: ModalProps) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="w-full max-w-md rounded-2xl bg-white p-5 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-900">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="flex h-8 w-8 items-center justify-center rounded-full text-gray-400 transition hover:bg-gray-100 hover:text-gray-700 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none"
          >
            <FiX />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

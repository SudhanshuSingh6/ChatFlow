import { useRef, useEffect } from "react";
import Avatar from "../ui/Avatar";
import PresenceDot from "../presence/PresenceDot";
import IconButton from "../ui/IconButton";
import { FiMoreVertical, FiSettings, FiSearch, FiX } from "react-icons/fi";

interface ConversationHeaderProps {
  name: string;
  subtitle?: string;
  online?: boolean;
  isGroup?: boolean;
  onSettingsClick?: () => void;
  searchOpen: boolean;
  onSearchToggle: () => void;
  searchQuery: string;
  onSearchQueryChange: (q: string) => void;
}

export default function ConversationHeader({
  name,
  subtitle,
  online,
  isGroup,
  onSettingsClick,
  searchOpen,
  onSearchToggle,
  searchQuery,
  onSearchQueryChange,
}: ConversationHeaderProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (searchOpen) inputRef.current?.focus();
  }, [searchOpen]);

  return (
    <header className="flex h-16 items-center gap-3 border-b border-outline-variant bg-surface-container-lowest px-4">
      {searchOpen ? (
        <>
          <IconButton aria-label="Close search" onClick={onSearchToggle}>
            <FiX />
          </IconButton>
          <input
            ref={inputRef}
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchQueryChange(e.target.value)}
            placeholder="Search messages…"
            className="flex-1 bg-transparent text-sm text-on-surface placeholder:text-on-surface-variant outline-none"
          />
          {searchQuery && (
            <IconButton
              aria-label="Clear search"
              onClick={() => onSearchQueryChange("")}
            >
              <FiX className="text-sm" />
            </IconButton>
          )}
        </>
      ) : (
        <>
          <div className="relative">
            <Avatar name={name} size={40} />
            {online !== undefined && (
              <PresenceDot
                online={online}
                className="absolute right-0 bottom-0"
              />
            )}
          </div>

          <div className="min-w-0 flex-1 pl-1">
            <p className="truncate text-sm font-semibold text-on-surface">{name}</p>
            {subtitle && (
              <p className="text-xs text-on-surface-variant">{subtitle}</p>
            )}
          </div>

          <IconButton
            aria-label="Search messages"
            onClick={onSearchToggle}
            className="text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
          >
            <FiSearch />
          </IconButton>

          {isGroup && onSettingsClick ? (
            <IconButton
              aria-label="Group settings"
              onClick={onSettingsClick}
              className="text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
            >
              <FiSettings />
            </IconButton>
          ) : (
            <IconButton aria-label="More options" className="text-on-surface-variant hover:bg-surface-container hover:text-on-surface">
              <FiMoreVertical />
            </IconButton>
          )}
        </>
      )}
    </header>
  );
}

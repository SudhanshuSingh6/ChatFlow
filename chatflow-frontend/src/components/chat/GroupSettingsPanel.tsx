import { useState } from "react";
import { FiX, FiTrash2, FiUserMinus, FiShield, FiUser } from "react-icons/fi";
import type { Conversation, Participant, ParticipantRole } from "../../types/domain";
import Avatar from "../ui/Avatar";
import { cn } from "../../lib/utils/cn";
import {
  useDeleteGroup,
  useRemoveParticipant,
  useUpdateRole,
  useTransferOwnership,
} from "../../hooks/useConversations";
import { useAuthStore } from "../../store/authStore";

interface Props {
  conversation: Conversation;
  onClose: () => void;
}

const roleBadge: Record<ParticipantRole, string> = {
  OWNER: "bg-yellow-100 text-yellow-800",
  ADMIN: "bg-primary/10 text-primary",
  MEMBER: "bg-surface-container text-on-surface-variant",
};

function RoleBadge({ role }: { role: ParticipantRole }) {
  return (
    <span className={cn("rounded-full px-2 py-0.5 text-xs font-semibold", roleBadge[role])}>
      {role}
    </span>
  );
}

function MemberRow({
  participant,
  conversationId,
  callerRole,
  currentUserId,
}: {
  participant: Participant;
  conversationId: string;
  callerRole: ParticipantRole;
  currentUserId: string;
}) {
  const { mutate: removeParticipant, isPending: removing } = useRemoveParticipant();
  const { mutate: updateRole, isPending: updatingRole } = useUpdateRole();

  const isSelf = participant.userId === currentUserId;
  const isOwner = participant.role === "OWNER";

  const canRemove =
    !isSelf &&
    !isOwner &&
    (callerRole === "OWNER" || (callerRole === "ADMIN" && participant.role === "MEMBER"));

  const canChangeRole = callerRole === "OWNER" && !isSelf && !isOwner;

  return (
    <div className="flex items-center gap-3 rounded-lg px-3 py-2 hover:bg-surface-container-low">
      <Avatar name={participant.username ?? "?"} size={36} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-on-surface">
          {participant.username ?? "Unknown"}
          {isSelf && <span className="ml-1 text-xs font-normal text-on-surface-variant">(you)</span>}
        </p>
      </div>

      <div className="flex items-center gap-2 shrink-0">
        {canChangeRole && (
          <button
            type="button"
            disabled={updatingRole}
            onClick={() =>
              updateRole({
                conversationId,
                userId: participant.userId,
                role: participant.role === "ADMIN" ? "MEMBER" : "ADMIN",
              })
            }
            title={participant.role === "ADMIN" ? "Demote to Member" : "Promote to Admin"}
            className="rounded p-1 text-on-surface-variant hover:bg-surface-container hover:text-primary disabled:opacity-40"
          >
            {participant.role === "ADMIN" ? <FiUser className="text-sm" /> : <FiShield className="text-sm" />}
          </button>
        )}

        <RoleBadge role={participant.role} />

        {canRemove && (
          <button
            type="button"
            disabled={removing}
            onClick={() => removeParticipant({ conversationId, userId: participant.userId })}
            title="Remove member"
            className="rounded p-1 text-on-surface-variant hover:bg-error-container/20 hover:text-error disabled:opacity-40"
          >
            <FiUserMinus className="text-sm" />
          </button>
        )}
      </div>
    </div>
  );
}

export default function GroupSettingsPanel({ conversation, onClose }: Props) {
  const currentUserId = useAuthStore((s) => s.user?.userId ?? "");
  const callerRole = conversation.callerRole;
  const participants = conversation.participants ?? [];

  const { mutate: deleteGroup, isPending: deleting } = useDeleteGroup();
  const { mutate: transferOwnership, isPending: transferring } = useTransferOwnership();

  const [confirmDelete, setConfirmDelete] = useState(false);
  const [transferTarget, setTransferTarget] = useState<string | null>(null);

  const nonOwnerMembers = participants.filter((p) => p.role !== "OWNER");

  return (
    <div className="absolute inset-y-0 right-0 z-20 flex w-72 flex-col border-l border-outline-variant bg-surface-container-lowest shadow-lg">
      {/* Header */}
      <div className="flex h-16 shrink-0 items-center justify-between border-b border-outline-variant px-4">
        <span className="font-semibold text-on-surface">Group Settings</span>
        <button
          type="button"
          onClick={onClose}
          className="rounded-full p-1.5 text-on-surface-variant hover:bg-surface-container"
        >
          <FiX />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {/* Group info */}
        <div className="flex flex-col items-center gap-2 py-2">
          <Avatar name={conversation.name ?? "Group"} size={56} />
          <p className="text-base font-bold text-on-surface">{conversation.name}</p>
          <p className="text-xs text-on-surface-variant">{conversation.memberCount} members</p>
        </div>

        {/* Members */}
        <div>
          <p className="mb-2 px-1 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
            Members
          </p>
          <div className="space-y-0.5">
            {participants.map((p) => (
              <MemberRow
                key={p.userId}
                participant={p}
                conversationId={conversation.id}
                callerRole={callerRole}
                currentUserId={currentUserId}
              />
            ))}
          </div>
        </div>

        {/* Danger zone — OWNER only */}
        {callerRole === "OWNER" && (
          <div className="space-y-3 rounded-lg border border-error-container p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-error">
              Danger Zone
            </p>

            {/* Transfer ownership */}
            {!confirmDelete && (
              <div>
                {transferTarget === null ? (
                  <button
                    type="button"
                    onClick={() => setTransferTarget("")}
                    className="w-full rounded-lg border border-outline-variant px-3 py-2 text-left text-sm text-on-surface-variant transition-colors hover:bg-surface-container"
                  >
                    Transfer Ownership…
                  </button>
                ) : (
                  <div className="space-y-2">
                    <p className="text-xs text-on-surface-variant">Select new owner:</p>
                    <select
                      value={transferTarget}
                      onChange={(e) => setTransferTarget(e.target.value)}
                      className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 text-sm text-on-surface outline-none focus:border-primary"
                    >
                      <option value="">— choose member —</option>
                      {nonOwnerMembers.map((p) => (
                        <option key={p.userId} value={p.userId}>
                          {p.username ?? p.userId}
                        </option>
                      ))}
                    </select>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        disabled={!transferTarget || transferring}
                        onClick={() =>
                          transferOwnership(
                            { conversationId: conversation.id, newOwnerId: transferTarget! },
                            { onSuccess: onClose },
                          )
                        }
                        className="flex-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-on-primary disabled:opacity-40 hover:bg-primary-container"
                      >
                        {transferring ? "Transferring…" : "Confirm"}
                      </button>
                      <button
                        type="button"
                        onClick={() => setTransferTarget(null)}
                        className="rounded-lg border border-outline-variant px-3 py-1.5 text-xs text-on-surface-variant hover:bg-surface-container"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Delete group */}
            {transferTarget === null && (
              <div>
                {!confirmDelete ? (
                  <button
                    type="button"
                    onClick={() => setConfirmDelete(true)}
                    className="flex w-full items-center gap-2 rounded-lg border border-error/30 px-3 py-2 text-left text-sm text-error transition-colors hover:bg-error-container/20"
                  >
                    <FiTrash2 className="shrink-0" />
                    Delete Group
                  </button>
                ) : (
                  <div className="space-y-2 rounded-lg bg-error-container/20 p-3">
                    <p className="text-sm font-semibold text-error">Delete this group?</p>
                    <p className="text-xs text-on-surface-variant">
                      This will permanently delete all messages and cannot be undone.
                    </p>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        disabled={deleting}
                        onClick={() => deleteGroup(conversation.id)}
                        className="flex-1 rounded-lg bg-error px-3 py-1.5 text-xs font-semibold text-on-error disabled:opacity-40 hover:opacity-90"
                      >
                        {deleting ? "Deleting…" : "Yes, delete"}
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmDelete(false)}
                        className="rounded-lg border border-outline-variant px-3 py-1.5 text-xs text-on-surface-variant hover:bg-surface-container"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

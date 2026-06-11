import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { queryKeys } from "../config/queryKeys";
import {
  createDirect,
  createGroup,
  getConversation,
  getMessages,
  listConversations,
} from "../lib/api/conversations";
import { getConversationPresence } from "../lib/api/presence";
import type { Message } from "../types/domain";

export function useConversations() {
  return useQuery({
    queryKey: queryKeys.conversations,
    queryFn: listConversations,
  });
}

export function useConversation(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.conversation(id ?? ""),
    queryFn: () => getConversation(id as string),
    enabled: !!id,
  });
}

/**
 * History as cursor pages (newest first per page). The flattened `messages`
 * are re-sorted ascending for rendering; `fetchNextPage` loads older history.
 */
export function useMessages(id: string | undefined) {
  const query = useInfiniteQuery({
    queryKey: queryKeys.messages(id ?? ""),
    queryFn: ({ pageParam }) => getMessages(id as string, pageParam),
    enabled: !!id,
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  });

  const messages: Message[] = (query.data?.pages ?? [])
    .flatMap((p) => p.messages)
    .sort((a, b) => a.sequenceNumber - b.sequenceNumber);

  return { ...query, messages };
}

export function useConversationPresence(id: string | undefined, enabled = true) {
  return useQuery({
    queryKey: queryKeys.conversationPresence(id ?? ""),
    queryFn: () => getConversationPresence(id as string),
    enabled: !!id && enabled,
  });
}

export function useCreateDirect() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: (userId: string) => createDirect(userId),
    onSuccess: (conversation) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
      navigate(`/chats/${conversation.id}`);
    },
  });
}

export function useCreateGroup() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: ({ name, memberIds }: { name: string; memberIds: string[] }) =>
      createGroup(name, memberIds),
    onSuccess: (conversation) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
      navigate(`/groups/${conversation.id}`);
    },
  });
}

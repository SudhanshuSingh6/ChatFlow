import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "../config/queryKeys";
import {
  acceptFriendRequest,
  declineFriendRequest,
  listFriendRequests,
  listFriends,
  sendFriendRequest,
  unfriend,
} from "../lib/api/friends";

export function useFriends() {
  return useQuery({ queryKey: queryKeys.friends, queryFn: listFriends });
}

export function useFriendRequests(box: "received" | "sent" = "received") {
  return useQuery({
    queryKey: queryKeys.friendRequests(box),
    queryFn: () => listFriendRequests(box),
  });
}

/** Refresh both friends and the pending lists after any friendship change. */
function useInvalidateFriendData() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.friends });
    queryClient.invalidateQueries({
      queryKey: queryKeys.friendRequests("received"),
    });
    queryClient.invalidateQueries({
      queryKey: queryKeys.friendRequests("sent"),
    });
  };
}

export function useSendFriendRequest() {
  const invalidate = useInvalidateFriendData();
  return useMutation({
    mutationFn: (username: string) => sendFriendRequest(username),
    onSuccess: invalidate,
  });
}

export function useRespondToRequest() {
  const invalidate = useInvalidateFriendData();
  return useMutation({
    mutationFn: ({
      friendshipId,
      action,
    }: {
      friendshipId: string;
      action: "accept" | "decline";
    }) =>
      action === "accept"
        ? acceptFriendRequest(friendshipId)
        : declineFriendRequest(friendshipId),
    onSuccess: invalidate,
  });
}

export function useUnfriend() {
  const invalidate = useInvalidateFriendData();
  return useMutation({
    mutationFn: (userId: string) => unfriend(userId),
    onSuccess: invalidate,
  });
}

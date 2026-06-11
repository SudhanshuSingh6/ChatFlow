import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";

import LoginPage from "../../pages/auth/LoginPage";
import RegisterPage from "../../pages/auth/RegisterPage";
import AppShell from "../../pages/AppShell";
import EmptyState from "../../pages/EmptyState";
import ChatsPage from "../../pages/chats/ChatsPage";
import ConversationPage from "../../pages/chats/ConversationPage";
import GroupsPage from "../../pages/groups/GroupsPage";
import GroupConversationPage from "../../pages/groups/GroupConversationPage";
import FriendsPage from "../../pages/friends/FriendsPage";
import ProtectedRoute from "./ProtectedRoute";
import GuestRoute from "./GuestRoute";

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Guest-only: redirect to app if already signed in */}
        <Route element={<GuestRoute />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>

        {/* Authenticated-only app shell (rail + panes) */}
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route index element={<Navigate to="/chats" replace />} />

            <Route path="chats" element={<ChatsPage />}>
              <Route index element={<EmptyState />} />
              <Route path=":conversationId" element={<ConversationPage />} />
            </Route>

            <Route path="groups" element={<GroupsPage />}>
              <Route
                index
                element={
                  <EmptyState message="Select a group to start chatting" />
                }
              />
              <Route path=":groupId" element={<GroupConversationPage />} />
            </Route>

            <Route path="friends" element={<FriendsPage />} />
          </Route>
        </Route>

        {/* Unknown paths → home (which redirects to /login if a guest) */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

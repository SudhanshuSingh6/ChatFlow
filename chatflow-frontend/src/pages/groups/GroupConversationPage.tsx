import { useParams } from "react-router-dom";
import ConversationView from "../../components/chat/ConversationView";

export default function GroupConversationPage() {
  const { groupId = "" } = useParams();
  return <ConversationView key={groupId} id={groupId} />;
}

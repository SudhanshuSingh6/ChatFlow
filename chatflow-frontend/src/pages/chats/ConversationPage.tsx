import { useParams } from "react-router-dom";
import ConversationView from "../../components/chat/ConversationView";

export default function ConversationPage() {
  const { conversationId = "" } = useParams();
  return <ConversationView key={conversationId} id={conversationId} />;
}

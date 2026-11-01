import { Chat } from '../chat'
import { ChatProvider } from '../chat/provider'
import { DirectionalTransition } from '../components/PageTransition'

export default function ChatPage() {
  return (
    <ChatProvider>
      <DirectionalTransition>
        <Chat.Frame>
          <Chat.Sessions />
          <Chat.Conversation>
            <Chat.Toolbar />
            <Chat.Messages />
            <Chat.Error />
            <Chat.Composer />
          </Chat.Conversation>
          <Chat.Preview />
        </Chat.Frame>
      </DirectionalTransition>
    </ChatProvider>
  )
}

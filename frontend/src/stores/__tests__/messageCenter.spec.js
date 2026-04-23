import { createPinia, setActivePinia } from 'pinia'
import { useMessageCenterStore } from '@/stores/messageCenter'

describe('messageCenter store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('aggregates chat and notification unread totals', () => {
    const store = useMessageCenterStore()

    store.chatUnreadTotal = 4
    store.notificationUnreadTotal = 3

    expect(store.totalUnread).toBe(7)
  })

  it('suppresses popup when current route already matches the session', () => {
    const store = useMessageCenterStore()

    store.setCurrentChatSession('1_9_7')
    store.pushIncomingChatToast({
      sessionId: '1_9_7',
      senderName: 'Landlord A',
      content: 'hello'
    })

    expect(store.chatToasts).toHaveLength(0)
  })
})

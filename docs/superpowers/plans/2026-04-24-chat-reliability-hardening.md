# Chat Reliability Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing chat module recover correctly after reconnect, keep session summaries and unread totals self-healing, fix read watermark semantics, and harden first-message session creation against concurrency.

**Architecture:** Keep the current `chat_session` and `chat_message` model, keep websocket as a post-commit realtime optimization, and use the existing REST endpoints as the final reconciliation source. On the frontend, move chat session summaries into a shared store so reconnect can refresh `page` and `unread-total` centrally, while the chat page continues to own current-session message history and pull recovery.

**Tech Stack:** Spring Boot 3, MyBatis-Plus, Vue 3, Pinia, Vitest, JUnit 5, Mockito

---

## File Structure

### Backend files

- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\ChatSessionServiceImpl.java`
  Responsibility: make first-message session creation safe under duplicate-key concurrency and keep send semantics unchanged.
- Create: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\ChatSessionServiceImplTest.java`
  Responsibility: cover duplicate-key recovery and message insert success in the first-message path.

### Frontend files

- Modify: `C:\javapractice\MyRent\frontend\src\views\ChatView.vue`
  Responsibility: advance read watermark only from truly visible inbound messages.
- Modify: `C:\javapractice\MyRent\frontend\src\views\__tests__\ChatView.spec.js`
  Responsibility: prove that loaded-but-not-visible messages are not marked read.
- Create: `C:\javapractice\MyRent\frontend\src\stores\chatSession.js`
  Responsibility: hold shared session summaries loaded from `GET /chat-session/page` and apply websocket-derived optimistic updates.
- Create: `C:\javapractice\MyRent\frontend\src\stores\__tests__\chatSession.spec.js`
  Responsibility: verify session summary loading and optimistic upsert behavior.
- Modify: `C:\javapractice\MyRent\frontend\src\views\MessagesView.vue`
  Responsibility: consume the shared chat-session store instead of a page-local websocket list.
- Modify: `C:\javapractice\MyRent\frontend\src\views\__tests__\MessagesView.spec.js`
  Responsibility: align the messages page test with the shared stores and the existing unread-total load behavior.
- Modify: `C:\javapractice\MyRent\frontend\src\layouts\MainLayout.vue`
  Responsibility: refresh global unread totals and shared session summaries when the app-level websocket reconnects, and apply optimistic summary updates on incoming message events.
- Modify: `C:\javapractice\MyRent\frontend\src\layouts\__tests__\MainLayout.spec.js`
  Responsibility: verify websocket open triggers reconciliation and incoming events are forwarded to the relevant stores.

### Existing file left in place

- Keep: `C:\javapractice\MyRent\frontend\src\composables\useChatSessionList.js`
  Responsibility after this change set: legacy helper left untouched for now. Do not delete it in this plan because file deletion was not explicitly approved.

## Task 1: Fix Read Watermark Semantics In ChatView

**Files:**
- Modify: `C:\javapractice\MyRent\frontend\src\views\ChatView.vue`
- Test: `C:\javapractice\MyRent\frontend\src\views\__tests__\ChatView.spec.js`

- [ ] **Step 1: Write the failing test**

```javascript
import { flushPromises, mount } from '@vue/test-utils'
import ChatView from '@/views/ChatView.vue'
import { markMessagesRead, pullHistoryMessages, pullNewMessages } from '@/api/chat'

let intersectionCallback = null

vi.mock('@/api/chat', () => ({
  markMessagesRead: vi.fn().mockResolvedValue(1),
  pullHistoryMessages: vi.fn().mockResolvedValue({
    messages: [
      { id: 9, sessionId: '1_9_7', senderId: 9, receiverId: 1001, content: 'visible inbound' },
      { id: 10, sessionId: '1_9_7', senderId: 9, receiverId: 1001, content: 'hidden inbound' },
      { id: 11, sessionId: '1_9_7', senderId: 1001, receiverId: 9, content: 'my reply' }
    ],
    nextCursor: null,
    hasMore: false
  }),
  pullNewMessages: vi.fn().mockResolvedValue({ messages: [] }),
  sendChatMessage: vi.fn()
}))

describe('ChatView read watermark', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    global.IntersectionObserver = class {
      constructor(callback) {
        intersectionCallback = callback
      }
      observe() {}
      disconnect() {}
    }
    window.IntersectionObserver = global.IntersectionObserver
    global.WebSocket = class {
      close() {}
    }
    window.WebSocket = global.WebSocket
    HTMLElement.prototype.scrollTo = vi.fn()
  })

  it('marks only the visible inbound range as read', async () => {
    const wrapper = mount(ChatView, {
      global: {
        stubs: {
          ChatBubble: { props: ['message'], template: '<div>{{ message.content }}</div>' },
          EmptyState: { template: '<div />' },
          LoadingState: { template: '<div />' }
        }
      }
    })

    await flushPromises()

    const nodes = wrapper.findAll('.message-observer-item')
    intersectionCallback([
      {
        target: nodes[0].element,
        isIntersecting: true,
        intersectionRatio: 0.8
      }
    ])

    await vi.advanceTimersByTimeAsync(150)
    await flushPromises()

    expect(markMessagesRead).toHaveBeenCalledWith({
      sessionId: '1_9_7',
      upToMessageId: 9
    })
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
npm --prefix frontend run test:run -- src/views/__tests__/ChatView.spec.js
```

Expected:

```text
FAIL src/views/__tests__/ChatView.spec.js
Expected upToMessageId: 9
Received upToMessageId: 11
```

- [ ] **Step 3: Write minimal implementation**

Update the read-watermark selection in `C:\javapractice\MyRent\frontend\src\views\ChatView.vue`:

```javascript
async function syncVisibleReadState() {
  if (!pageActive || document.visibilityState !== 'visible') {
    return
  }
  const upToMessageId = getMaxVisibleReadableMessageId()
  if (!upToMessageId || upToMessageId <= lastReadUpToId.value) {
    return
  }
  try {
    const updatedCount = await markMessagesRead({
      sessionId: sessionId.value,
      upToMessageId
    })
    lastReadUpToId.value = upToMessageId
    if (updatedCount > 0) {
      messageCenterStore.decrementChatUnread(updatedCount)
    }
  } catch {
    // Ignore read receipt failures to keep the chat flow responsive.
  }
}
```

- [ ] **Step 4: Add the guard test for loaded-but-not-visible inbound messages**

Append a second assertion in `C:\javapractice\MyRent\frontend\src\views\__tests__\ChatView.spec.js`:

```javascript
it('does not mark loaded but non-visible inbound messages as read', async () => {
  const wrapper = mount(ChatView, {
    global: {
      stubs: {
        ChatBubble: { props: ['message'], template: '<div>{{ message.content }}</div>' },
        EmptyState: { template: '<div />' },
        LoadingState: { template: '<div />' }
      }
    }
  })

  await flushPromises()
  await vi.advanceTimersByTimeAsync(150)
  await flushPromises()

  expect(markMessagesRead).not.toHaveBeenCalled()

  wrapper.unmount()
})
```

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
npm --prefix frontend run test:run -- src/views/__tests__/ChatView.spec.js
```

Expected:

```text
PASS src/views/__tests__/ChatView.spec.js
2 passed
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/ChatView.vue frontend/src/views/__tests__/ChatView.spec.js
git commit -m "fix(chat): use visible inbound watermark for read receipts"
```

## Task 2: Share Session Summaries And Reconcile On Reconnect

**Files:**
- Create: `C:\javapractice\MyRent\frontend\src\stores\chatSession.js`
- Create: `C:\javapractice\MyRent\frontend\src\stores\__tests__\chatSession.spec.js`
- Modify: `C:\javapractice\MyRent\frontend\src\views\MessagesView.vue`
- Modify: `C:\javapractice\MyRent\frontend\src\views\__tests__\MessagesView.spec.js`
- Modify: `C:\javapractice\MyRent\frontend\src\layouts\MainLayout.vue`
- Modify: `C:\javapractice\MyRent\frontend\src\layouts\__tests__\MainLayout.spec.js`

- [ ] **Step 1: Write the failing store test**

Create `C:\javapractice\MyRent\frontend\src\stores\__tests__\chatSession.spec.js`:

```javascript
import { createPinia, setActivePinia } from 'pinia'
import { useChatSessionStore } from '@/stores/chatSession'

vi.mock('@/api/chat', () => ({
  fetchSessionPage: vi.fn().mockResolvedValue({
    records: [
      {
        sessionId: '1_9_7',
        peerId: 9,
        peerName: 'Landlord A',
        houseId: 7,
        houseTitle: 'Tianhe One Bed',
        lastMsgContent: 'older message',
        unreadCount: 2,
        updateTime: '2026-04-24T09:00:00'
      }
    ]
  })
}))

describe('chatSession store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads session summaries from /chat-session/page', async () => {
    const store = useChatSessionStore()

    await store.loadSessions()

    expect(store.sessions).toHaveLength(1)
    expect(store.sessions[0].sessionId).toBe('1_9_7')
    expect(store.sessions[0].unreadCount).toBe(2)
  })

  it('moves an updated session to the top on incoming message', () => {
    const store = useChatSessionStore()
    store.sessions = [
      {
        sessionId: '1_9_7',
        peerId: 9,
        peerName: 'Landlord A',
        houseId: 7,
        houseTitle: 'Tianhe One Bed',
        lastMsgContent: 'older message',
        unreadCount: 2,
        updateTime: '2026-04-24T09:00:00'
      },
      {
        sessionId: '1_8_5',
        peerId: 8,
        peerName: 'Landlord B',
        houseId: 5,
        houseTitle: 'Yuexiu Loft',
        lastMsgContent: 'previous top',
        unreadCount: 0,
        updateTime: '2026-04-24T10:00:00'
      }
    ]

    store.upsertSessionFromMessage(
      {
        sessionId: '1_9_7',
        senderId: 9,
        receiverId: 1001,
        content: 'fresh message',
        createTime: '2026-04-24T11:00:00'
      },
      1001
    )

    expect(store.sessions[0].sessionId).toBe('1_9_7')
    expect(store.sessions[0].lastMsgContent).toBe('fresh message')
    expect(store.sessions[0].unreadCount).toBe(3)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
npm --prefix frontend run test:run -- src/stores/__tests__/chatSession.spec.js
```

Expected:

```text
FAIL src/stores/__tests__/chatSession.spec.js
Cannot find module '@/stores/chatSession'
```

- [ ] **Step 3: Implement the shared session-summary store**

Create `C:\javapractice\MyRent\frontend\src\stores\chatSession.js`:

```javascript
import { defineStore } from 'pinia'
import { fetchSessionPage } from '@/api/chat'
import { formatRequestError } from '@/utils/format'

function normalizeSession(session) {
  return {
    ...session,
    peerName: session.peerName || (session.peerId ? `User ${session.peerId}` : ''),
    houseLabel: session.houseTitle || '',
    unreadCount: Number(session.unreadCount || 0)
  }
}

export const useChatSessionStore = defineStore('chatSession', {
  state: () => ({
    loading: false,
    error: '',
    sessions: []
  }),
  actions: {
    async loadSessions() {
      this.loading = true
      this.error = ''
      try {
        const page = await fetchSessionPage({ current: 1, size: 50 })
        const records = Array.isArray(page?.records) ? page.records : []
        this.sessions = records.map(normalizeSession)
      } catch (err) {
        this.error = formatRequestError(err, 'Session list unavailable')
        this.sessions = []
      } finally {
        this.loading = false
      }
    },
    upsertSessionFromMessage(message, currentUserId) {
      if (!message?.sessionId) {
        return
      }

      const currentUser = String(currentUserId || '')
      const index = this.sessions.findIndex(
        (item) => String(item.sessionId) === String(message.sessionId)
      )

      if (index < 0) {
        return
      }

      const current = this.sessions[index]
      const updated = {
        ...current,
        lastMsgContent: message.content || current.lastMsgContent,
        updateTime: message.createTime || new Date().toISOString(),
        unreadCount: String(message.receiverId) === currentUser
          ? Number(current.unreadCount || 0) + 1
          : Number(current.unreadCount || 0)
      }

      const nextSessions = [...this.sessions]
      nextSessions.splice(index, 1)
      nextSessions.unshift(updated)
      this.sessions = nextSessions
    }
  }
})
```

- [ ] **Step 4: Make MessagesView consume the shared store**

Replace the local composable use in `C:\javapractice\MyRent\frontend\src\views\MessagesView.vue`:

```javascript
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useChatSessionStore } from '@/stores/chatSession'
import { useMessageCenterStore } from '@/stores/messageCenter'

const router = useRouter()
const chatSessionStore = useChatSessionStore()
const messageCenterStore = useMessageCenterStore()
const notificationLoading = ref(false)
const notificationError = ref('')
const notifications = ref([])

const loading = computed(() => chatSessionStore.loading)
const error = computed(() => chatSessionStore.error)
const sessions = computed(() => chatSessionStore.sessions)

function refreshCurrentTab() {
  if (activeTab.value === 'notifications') {
    loadNotifications()
    return
  }
  chatSessionStore.loadSessions()
}

onMounted(() => {
  messageCenterStore.loadUnreadTotals()
  chatSessionStore.loadSessions()
  loadNotifications()
})
```

- [ ] **Step 5: Reconcile unread totals and session summaries on websocket open in MainLayout**

Update `C:\javapractice\MyRent\frontend\src\layouts\MainLayout.vue`:

```javascript
import { useChatSessionStore } from '@/stores/chatSession'
import { useMessageCenterStore } from '@/stores/messageCenter'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const chatSessionStore = useChatSessionStore()
const messageCenterStore = useMessageCenterStore()

function connectWs() {
  if (!active || !getToken()) {
    return
  }

  clearReconnectTimer()
  closeWs()
  ws = new WebSocket(buildWsUrl())

  ws.onopen = () => {
    if (!active) {
      return
    }
    messageCenterStore.loadUnreadTotals()
    chatSessionStore.loadSessions()
  }

  ws.onmessage = (event) => {
    if (!active) {
      return
    }
    try {
      const payload = JSON.parse(event.data)
      messageCenterStore.handleIncomingChatMessage(payload)
      chatSessionStore.upsertSessionFromMessage(payload, authStore.userId)
    } catch {
      // Ignore malformed websocket payloads.
    }
  }
}
```

- [ ] **Step 6: Update the page-level tests to match the shared-store model**

Update `C:\javapractice\MyRent\frontend\src\views\__tests__\MessagesView.spec.js`:

```javascript
vi.mock('@/stores/chatSession', () => ({
  useChatSessionStore: () => ({
    loading: false,
    error: '',
    sessions: [
      {
        sessionId: '1_9_7',
        peerId: 9,
        peerName: 'Landlord A',
        unreadCount: 2,
        houseId: 7,
        houseTitle: 'Tianhe One Bed'
      }
    ],
    loadSessions: vi.fn()
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    chatUnreadTotal: 2,
    notificationUnreadTotal: 1,
    loadUnreadTotals: vi.fn(),
    decrementNotificationUnread: vi.fn(),
    setNotificationUnreadTotal: vi.fn()
  })
}))
```

Update `C:\javapractice\MyRent\frontend\src\layouts\__tests__\MainLayout.spec.js`:

```javascript
const loadUnreadTotals = vi.fn()
const handleIncomingChatMessage = vi.fn()
const loadSessions = vi.fn()
const upsertSessionFromMessage = vi.fn()

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    chatToasts: [],
    loadUnreadTotals,
    dismissChatToast: vi.fn(),
    setCurrentChatSession: vi.fn(),
    handleIncomingChatMessage
  })
}))

vi.mock('@/stores/chatSession', () => ({
  useChatSessionStore: () => ({
    loadSessions,
    upsertSessionFromMessage
  })
}))
```

- [ ] **Step 7: Run targeted frontend tests**

Run:

```bash
npm --prefix frontend run test:run -- src/stores/__tests__/chatSession.spec.js src/views/__tests__/MessagesView.spec.js src/layouts/__tests__/MainLayout.spec.js
```

Expected:

```text
PASS src/stores/__tests__/chatSession.spec.js
PASS src/views/__tests__/MessagesView.spec.js
PASS src/layouts/__tests__/MainLayout.spec.js
```

- [ ] **Step 8: Commit**

```bash
git add frontend/src/stores/chatSession.js frontend/src/stores/__tests__/chatSession.spec.js frontend/src/views/MessagesView.vue frontend/src/views/__tests__/MessagesView.spec.js frontend/src/layouts/MainLayout.vue frontend/src/layouts/__tests__/MainLayout.spec.js
git commit -m "fix(chat): reconcile summaries and unread totals on reconnect"
```

## Task 3: Harden First-Message Session Creation Against Duplicate Keys

**Files:**
- Modify: `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\ChatSessionServiceImpl.java`
- Create: `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\ChatSessionServiceImplTest.java`

- [ ] **Step 1: Write the failing backend unit test**

Create `C:\javapractice\MyRent\src\test\java\cn\yy\myrent\service\impl\ChatSessionServiceImplTest.java`:

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.dto.MessageDTO;
import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.websocket.ChatWebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ChatSessionServiceImplTest {

    @Test
    void shouldRecoverWhenSessionInsertHitsDuplicateKey() {
        ChatSessionServiceImpl service = new ChatSessionServiceImpl();
        ChatSessionMapper chatSessionMapper = Mockito.mock(ChatSessionMapper.class);
        ChatMessageMapper chatMessageMapper = Mockito.mock(ChatMessageMapper.class);
        UserMapper userMapper = Mockito.mock(UserMapper.class);
        HouseMapper houseMapper = Mockito.mock(HouseMapper.class);
        ChatWebSocketSessionManager sessionManager = Mockito.mock(ChatWebSocketSessionManager.class);
        HouseHotService houseHotService = Mockito.mock(HouseHotService.class);

        ReflectionTestUtils.setField(service, "baseMapper", chatSessionMapper);
        ReflectionTestUtils.setField(service, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "houseMapper", houseMapper);
        ReflectionTestUtils.setField(service, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(service, "houseHotService", houseHotService);

        MessageDTO dto = new MessageDTO();
        dto.setSenderId(1001L);
        dto.setReceiverId(9L);
        dto.setHouseId(7L);
        dto.setContent("hello");

        House house = new House().setId(7L).setPublisherUserId(9L).setStatus(1);
        User sender = new User().setId(1001L).setName("Renter A");
        User receiver = new User().setId(9L).setName("Landlord A");
        ChatSession existingSession = new ChatSession()
                .setSessionId("9_1001_7")
                .setUserId1(9L)
                .setUserId2(1001L)
                .setHouseId(7L);

        when(houseMapper.selectById(7L)).thenReturn(house);
        when(userMapper.selectById(1001L)).thenReturn(sender);
        when(userMapper.selectById(9L)).thenReturn(receiver);
        when(chatSessionMapper.selectOne(any())).thenReturn(null, existingSession);
        when(chatSessionMapper.insert(any(ChatSession.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenReturn(1);
        when(chatSessionMapper.update(any(), any())).thenReturn(1);

        ChatMessage message = service.sendMessage(dto);

        assertEquals("9_1001_7", message.getSessionId());
        assertEquals(1001L, message.getSenderId());
        assertEquals(9L, message.getReceiverId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn "-Dtest=ChatSessionServiceImplTest" test
```

Expected:

```text
FAIL ChatSessionServiceImplTest
DuplicateKeyException
```

- [ ] **Step 3: Refactor session creation into a duplicate-key-safe helper**

Add a helper in `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\ChatSessionServiceImpl.java`:

```java
private ChatSession ensureChatSession(
        String sessionId,
        Long senderId,
        Long receiverId,
        Long houseId,
        String content,
        LocalDateTime now) {
    ChatSession existing = this.lambdaQuery()
            .eq(ChatSession::getSessionId, sessionId)
            .one();

    if (existing != null) {
        refreshExistingSession(sessionId, houseId, content, now);
        return existing;
    }

    ChatSession newSession = new ChatSession();
    newSession.setSessionId(sessionId);
    newSession.setUserId1(Math.min(senderId, receiverId));
    newSession.setUserId2(Math.max(senderId, receiverId));
    newSession.setHouseId(houseId);
    newSession.setLastMsgContent(content);
    newSession.setCreateTime(now);
    newSession.setUpdateTime(now);

    try {
        this.save(newSession);
        return newSession;
    } catch (DuplicateKeyException e) {
        ChatSession recovered = this.lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .one();
        if (recovered == null) {
            throw e;
        }
        refreshExistingSession(sessionId, houseId, content, now);
        return recovered;
    }
}

private void refreshExistingSession(String sessionId, Long houseId, String content, LocalDateTime now) {
    boolean updated = this.lambdaUpdate()
            .set(ChatSession::getHouseId, houseId)
            .set(ChatSession::getLastMsgContent, content)
            .set(ChatSession::getUpdateTime, now)
            .eq(ChatSession::getSessionId, sessionId)
            .update();
    if (!updated) {
        throw new RuntimeException("更新会话失败");
    }
}
```

- [ ] **Step 4: Replace the inline create-or-update logic with the helper**

Update `sendMessage(...)` in `C:\javapractice\MyRent\src\main\java\cn\yy\myrent\service\impl\ChatSessionServiceImpl.java`:

```java
House house = houseMapper.selectById(houseId);
ChatSession chatSession = this.lambdaQuery()
        .eq(ChatSession::getSessionId, sessionId)
        .one();
ChatSessionPermissionValidator.validate(house, senderId, receiverId, chatSession != null);
validateExistingSession(chatSession, senderId, receiverId, houseId);

ChatSession ensuredSession = ensureChatSession(sessionId, senderId, receiverId, houseId, content, now);
validateExistingSession(ensuredSession, senderId, receiverId, houseId);
```

and remove the old inline `if (chatSession == null) { ... } else { ... }` block.

- [ ] **Step 5: Run the backend unit test to verify it passes**

Run:

```bash
mvn "-Dtest=ChatSessionServiceImplTest" test
```

Expected:

```text
BUILD SUCCESS
Tests run: 1, Failures: 0, Errors: 0
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/ChatSessionServiceImpl.java src/test/java/cn/yy/myrent/service/impl/ChatSessionServiceImplTest.java
git commit -m "fix(chat): recover from duplicate session creation"
```

## Task 4: Run Regression Suite And Verify Spec Coverage

**Files:**
- Modify: `C:\javapractice\MyRent\docs\superpowers\plans\2026-04-24-chat-reliability-hardening.md`

- [ ] **Step 1: Run the targeted frontend regression suite**

Run:

```bash
npm --prefix frontend run test:run -- src/views/__tests__/ChatView.spec.js src/stores/__tests__/chatSession.spec.js src/views/__tests__/MessagesView.spec.js src/layouts/__tests__/MainLayout.spec.js
```

Expected:

```text
PASS src/views/__tests__/ChatView.spec.js
PASS src/stores/__tests__/chatSession.spec.js
PASS src/views/__tests__/MessagesView.spec.js
PASS src/layouts/__tests__/MainLayout.spec.js
```

- [ ] **Step 2: Run the targeted backend regression suite**

Run:

```bash
mvn "-Dtest=ChatSessionPermissionValidatorTest,ChatSessionServiceImplTest" test
```

Expected:

```text
BUILD SUCCESS
Tests run: 5, Failures: 0, Errors: 0
```

- [ ] **Step 3: Manually verify the reconnect reconciliation flow**

Use this checklist in a dev session:

```text
1. Open /chat/1_9_7 as user A.
2. Disconnect the browser network.
3. From another client, send a new message from user C to user A in session 1_8_5.
4. Re-enable network and wait for websocket reconnect.
5. Confirm current chat session 1_9_7 catches up through pull.
6. Navigate to /messages and confirm session 1_8_5 appears without manual browser refresh.
7. Confirm the global unread badge matches the new server unread total.
```

Expected:

```text
Current chat remains continuous.
New off-screen session appears in the session summary list.
Global unread total is corrected after reconnect.
```

- [ ] **Step 4: Update the plan checklist with any deviations discovered during execution**

If one of the tests or manual checks reveals a mismatch, append a short note under this task before continuing:

```markdown
Execution note:
- Adjusted MainLayout websocket open reconciliation to await `loadUnreadTotals()` before rendering the badge because the badge flickered under slow API responses.
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/__tests__/ChatView.spec.js frontend/src/stores/__tests__/chatSession.spec.js frontend/src/views/__tests__/MessagesView.spec.js frontend/src/layouts/__tests__/MainLayout.spec.js src/test/java/cn/yy/myrent/service/impl/ChatSessionServiceImplTest.java
git commit -m "test(chat): add reliability regression coverage"
```

## Self-Review

### Spec coverage

- read watermark based on actual visibility: covered by Task 1
- reconnect recovery for current session: preserved in Task 1 and verified manually in Task 4
- `page` and `unread-total` reconciliation after reconnect: covered by Task 2
- self-healing session summaries and unread totals: covered by Task 2 and Task 4
- first-message duplicate-key handling: covered by Task 3
- reuse of existing `GET /chat-session/page` and `GET /chat-session/unread-total`: covered by Task 2

### Placeholder scan

No `TODO`, `TBD`, "handle appropriately", or "similar to above" placeholders remain. Every task names exact files, commands, and code snippets.

### Type consistency

- `lastMessageId` remains the sync cursor and is not repurposed in the plan
- `upToMessageId` remains the read watermark in Task 1
- shared store method names are consistent:
  - `loadSessions()`
  - `upsertSessionFromMessage(...)`


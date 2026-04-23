# Notification V1 Design

Date: 2026-04-22

## Background

The current project already supports house detail browsing, house favorites, chat sessions, chat history pull, chat read receipts, and WebSocket chat delivery.

What it does not support yet is a first-release notification system that separates:

- real-time chat interruption while the user is online
- total unread chat awareness after the user comes back online
- persistent business notifications that users can review later
- publisher follow relationships for new-house notifications

The goal of this design is to ship a small but production-usable notification v1 that fits the current codebase instead of replacing the existing chat model.

## Confirmed Product Scope

The first release includes exactly these capabilities:

1. Online chat pop-up notifications
2. Chat unread total badge after the user comes back online
3. A persistent in-app notification inbox
4. Follow-publisher notifications for newly published houses

The confirmed business notification sources are:

- favorited house changed to offline
- favorited house changed to rented
- favorited house deleted
- favorited house price changed
- followed publisher published a new house

## Goals

- Keep chat as the source of truth for chat messages and chat unread state.
- Add a separate notification inbox for non-chat business events.
- Show one aggregated unread badge on the main navigation message entry.
- Keep the first release small, readable, and aligned with the current Spring Boot plus Vue architecture.
- Avoid premature optimization such as Redis follower caches or push/pull hybrid fanout strategies.

## Non-Goals

- SMS, email, mobile push, or desktop system notifications
- Notification grouping or folding
- Redis follower-set cache
- MQ-based async fanout for publisher notifications
- Order, payment, refund, or review notifications in the inbox
- Special deleted-house archive pages
- Replacing the existing chat session unread model with a global notification model

## Recommended Architecture

Do not unify chat messages and business notifications into one storage model.

The recommended v1 architecture has four bounded modules:

1. `chat realtime notify`
   Purpose: show an online pop-up when a new chat message arrives and the user is not viewing that exact session.

2. `chat unread summary`
   Purpose: expose total unread chat count for global badges and the chat tab badge.

3. `notification inbox`
   Purpose: persist business notifications, query them, and manage read state.

4. `publisher follow`
   Purpose: manage follow and unfollow relationships between renters and publishers.

This keeps the existing chat flow intact while adding persistent notification behavior only where chat semantics do not apply.

## Confirmed Product Decisions

### Messages Page Structure

`/messages` becomes a two-tab entry:

- `Chat`
- `Notifications`

Each tab shows its own unread count:

- `Chat (chatUnreadTotal)`
- `Notifications (notificationUnreadTotal)`

The top navigation message badge shows:

- `chatUnreadTotal + notificationUnreadTotal`

### Online Chat Pop-up Rules

- A new WebSocket chat message triggers a pop-up only when the user is online and is not currently viewing that same `sessionId`.
- If the user is already inside the target session page, no pop-up is shown.
- If the user is offline, the system does not replay historical pop-ups after login.
- Clicking the pop-up navigates to `/chat/:sessionId`.

Recommended first-release pop-up content:

- title: `New message from {senderName}`
- body: message content preview, truncated to around 20 to 30 characters
- action hint: `View`

### Notification Inbox Read Rules

- Entering the inbox list does not auto-mark notifications as read.
- Clicking a single notification marks that notification as read and then navigates.
- The inbox supports a `mark all read` action.

### Favorited House Change Rules

Only these changes create inbox notifications for house followers:

- house becomes offline
- house becomes rented
- house is deleted
- house price changes

The first release does not notify on title, description, image, or other field edits.

### Publisher Follow Rules

- House detail page adds a follow button beside the publisher information.
- Following a publisher is different from favoriting a house and must use a separate relation.
- Every newly published house generates one notification per follower.
- The first release does not batch multiple new houses into one notification.

### Notification Redirect Rules

For the first release, all business notifications redirect to house detail by `houseId`.

- `HOUSE_OFFLINE` -> `/house/:houseId`
- `HOUSE_RENTED` -> `/house/:houseId`
- `HOUSE_PRICE_CHANGED` -> `/house/:houseId`
- `PUBLISHER_NEW_HOUSE` -> `/house/:houseId`
- `HOUSE_DELETED` -> `/house/:houseId`, with the detail page showing an unavailable fallback state

## Data Model

### 1. Keep Existing Chat Tables

Continue using:

- `chat_session`
- `chat_message`

No inbox rows are generated for chat messages in v1.

### 2. Add `notification` Table

Recommended columns:

- `id`
- `user_id`
- `type`
- `title`
- `content`
- `biz_key`
- `redirect_type`
- `redirect_target_id`
- `extra_json`
- `is_read`
- `read_time`
- `create_time`

Recommended indexes and constraints:

- index on `(user_id, is_read, create_time)`
- unique index on `(user_id, biz_key)`

Field guidance:

- `type` stores notification type such as `HOUSE_PRICE_CHANGED`
- `biz_key` prevents duplicate delivery for the same logical event
- `redirect_type` allows future extension even if v1 only uses `house_detail`
- `extra_json` can store structured extras such as old price, new price, publisher name, or house title

### 3. Add `publisher_follow` Table

Recommended columns:

- `id`
- `user_id`
- `publisher_user_id`
- `status`
- `create_time`
- `cancel_time`

Recommended indexes and constraints:

- unique index on `(user_id, publisher_user_id)`
- index on `(publisher_user_id, status)`

Semantics:

- `house_favorite` means "I follow this house"
- `publisher_follow` means "I follow this publisher"

They should not be merged because their trigger conditions and future query paths are different.

## Notification Types

The first release supports only these inbox types:

- `HOUSE_OFFLINE`
- `HOUSE_RENTED`
- `HOUSE_DELETED`
- `HOUSE_PRICE_CHANGED`
- `PUBLISHER_NEW_HOUSE`

This leaves room for later expansion without polluting the first release.

## Event Trigger Design

The first release should generate business notifications from backend business actions, not from scheduled scans.

### 1. Chat Message Delivery

Current chat behavior stays intact:

1. sender sends chat message
2. backend saves `chat_session` and `chat_message`
3. backend pushes message through WebSocket after commit
4. frontend updates chat session state
5. frontend decides whether to show a real-time pop-up

### 2. Favorited House Changed

When a house is updated:

1. backend determines whether the change matches one of the supported notification events
2. backend queries active house favorites for that `houseId`
3. backend inserts one notification row per user

Supported event checks:

- status changed to offline
- status changed to rented
- delete action executed
- price changed from `oldPrice` to `newPrice`

### 3. Followed Publisher Published a New House

When a new house is successfully published:

1. backend finds active followers from `publisher_follow` by `publisher_user_id`
2. backend inserts one `PUBLISHER_NEW_HOUSE` notification per follower

The first release does not use Redis or async queue fanout for this path.

## Idempotency Rules

Notification generation must be idempotent.

Recommended `biz_key` patterns:

- house status change:
  - `house:{houseId}:type:{type}:version:{changeVersion}`
- house price change:
  - `house:{houseId}:price:{oldPrice}->{newPrice}`
- publisher new house:
  - `publisher:{publisherId}:house:{houseId}:new`

Combined with unique index `(user_id, biz_key)`, this prevents duplicate rows when the same business action retries.

`changeVersion` can be a stable change sequence or a deterministic update marker from the house write path. The implementation should choose one explicit source and use it consistently.

## API Design

### Chat Summary

- `GET /chat-session/unread-total`

Behavior:

- returns total unread chat message count for the current user
- used by top navigation badge and `Chat` tab badge

### Notification Inbox

- `GET /notification/page`
- `GET /notification/unread-total`
- `POST /notification/read/{id}`
- `POST /notification/read-all`

Behavior:

- page endpoint returns paginated inbox items ordered by latest first
- unread total returns the unread inbox count for the current user
- single read marks one notification as read
- read all marks all unread notifications for the current user as read

### Publisher Follow

- `POST /publisher-follow/{publisherUserId}`
- `DELETE /publisher-follow/{publisherUserId}`
- `GET /publisher-follow/{publisherUserId}/status`

Behavior:

- follow and unfollow only affect the current user
- status endpoint supports the house detail follow button state

## Frontend Design

### Global Badge Placement

The current layout already uses:

- top nav on desktop
- bottom tab bar on mobile

The global unread badge should be added to the shared message navigation item so that both desktop and mobile entry points reflect the aggregated unread total.

### Messages Page

`/messages` becomes a two-tab page:

- chat tab shows chat sessions
- notification tab shows inbox items

Each tab displays its own unread count badge.

### House Detail Page

Keep the current favorite action.

Add a new publisher follow action beside publisher information:

- `Follow`
- `Following`

This button is independent from house favorite state.

### Online Pop-up Placement

The online chat pop-up should be mounted at `MainLayout` level so it can appear while browsing other pages such as home, list, or house detail.

The pop-up should not be owned by `MessagesView` or `ChatView`, because the user needs to see it outside those pages.

## Read-State Flow

### Chat

Keep the current chat read behavior:

- visible inbound messages inside the chat session are marked read
- chat unread total decreases accordingly

### Notification Inbox

- inbox list does not auto-read on entry
- clicking one item marks that item read
- `mark all read` marks all unread inbox notifications read

## Performance and Future Optimization

The first release should query followers from MySQL directly.

Do not introduce Redis follower-set cache in v1 because:

- current user scale is small
- new-house publishing is not the hottest path
- correctness is more important than cache complexity in the first release

Recommended later optimization path:

1. Add Redis set cache per publisher:
   - key example: `follow:publisher:{publisherId}`
2. Keep MySQL as the source of truth
3. Update Redis only after DB transaction commit
4. If Redis update fails, delete the cache key and rebuild later from DB

If follower volume becomes large later, then introduce async fanout or MQ-based notification generation. That is explicitly out of scope for v1.

## Suggested Implementation Order

1. Add `notification` table and inbox backend APIs
2. Add `publisher_follow` table and backend follow APIs
3. Add house-detail publisher follow button
4. Implement favorited-house change notification generation
5. Implement followed-publisher new-house notification generation
6. Add `GET /chat-session/unread-total`
7. Refactor `/messages` into `Chat / Notifications` tabs
8. Add global online chat pop-up in `MainLayout`
9. Add shared aggregated unread badge on desktop and mobile navigation

This order reduces risk by building persistent data and APIs first, then attaching UI and real-time behavior.

## Main Risks

- duplicate business notifications
- missing or incorrect event detection on house updates
- aggregated badge mismatch between chat unread and inbox unread
- users receiving publisher notifications after unfollow
- deleted-house redirect producing a broken UX

The design addresses these by:

- unique `(user_id, biz_key)` protection
- explicit change-type boundaries
- separate unread totals for chat and inbox
- DB-based active follow query in v1
- house detail unavailable fallback state

## Test Focus

Recommended coverage for the first release:

- online new chat message shows pop-up when not in current session
- online new chat message does not show pop-up when already in current session
- chat unread total is correct after login and after reading messages
- house price change creates one inbox notification for each active favorite user
- repeated retry of the same change does not create duplicate notifications
- unfollowed users do not receive new-house notifications
- top navigation message badge equals `chatUnreadTotal + notificationUnreadTotal`
- single notification read and `全部已读` both update badge counts correctly

## Summary

Notification v1 should not be designed as one giant unified notification system.

Instead, it should preserve the existing chat message model and add a separate persistent inbox for business events. That gives the project a clean first-release boundary:

- chat handles conversations and real-time interruption
- inbox handles persistent business reminders
- top navigation aggregates both into one badge
- publisher follow stays independent from house favorite

This is the smallest design that still supports the intended user experience and leaves a clean path for later scaling work.

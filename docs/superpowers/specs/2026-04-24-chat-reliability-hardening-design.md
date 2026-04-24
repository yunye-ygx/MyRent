# Chat Reliability Hardening Design

Date: 2026-04-24

## Background

The current project already supports these chat capabilities:

- session creation based on `minUserId_maxUserId_houseId`
- message persistence through `chat_session` and `chat_message`
- post-commit WebSocket push to online receivers
- chat history loading in the chat page
- incremental pull by `lastMessageId`
- read receipt updates through `POST /chat-message/read`
- session summary query through `GET /chat-session/page`
- global unread count query through `GET /chat-session/unread-total`

The current problem is not the absence of chat functionality. The problem is that chat state is still fragile under disconnect, reconnect, multi-session message arrival, read-state advancement, and concurrent first-message send paths.

This design focuses on reliability hardening for the existing chat module instead of building a richer chat product.

## Confirmed Scope

This design covers only stability-first hardening for the existing chat module.

It includes:

1. clearer state and cursor semantics
2. reconnect recovery and global chat-state reconciliation
3. correct read semantics based on actual visibility
4. self-healing of session summaries and unread totals after missed realtime events
5. idempotency and concurrency protection for first-message send paths
6. explicit reuse of the existing `GET /chat-session/page` and `GET /chat-session/unread-total`

## Goals

- Keep `chat_message` as the source of truth for message facts.
- Keep WebSocket as a realtime acceleration path, not the source of final consistency.
- Make `pull`, `history`, `read`, `page`, and `unread-total` semantics unambiguous.
- Ensure current chat view, session list, and global unread badge can recover after disconnect or missed websocket events.
- Fix the current read behavior so that loaded messages are not treated as already read unless actually viewed.
- Preserve the current house-chat business constraints:
  - only the current house publisher can be contacted
  - the first session must be initiated by the renter
  - the publisher can reply only after the session already exists
- Prefer small, explicit changes over a large protocol rewrite.

## Non-Goals

- image, voice, file, or rich-media messages
- chat search
- message recall, quote, forward, or delete
- session pin, mute, or archive
- delivered-state UI
- MQ-based chat delivery redesign
- Redis-based chat cursor cache
- replacing the existing `page` or `unread-total` endpoints
- rewriting the entire frontend realtime architecture

## Current Problems

The current module has five concrete reliability issues.

### 1. Read watermark is advanced too aggressively

The chat page already observes message visibility, but the actual read request still uses the last loaded message id instead of the last actually visible inbound message id.

This can mark a larger loaded range as read even when the user has only seen the bottom part of the list.

### 2. Current-session recovery exists, but global reconciliation is incomplete

The current chat page can pull missed messages for the active session, but reconnect recovery does not fully define when to refresh:

- the session summary list
- the global unread total

This means a user can recover the current conversation but still miss a newly active session from another sender until the messages page is refreshed.

### 3. `pull` cursor and `read` watermark are easy to confuse

The current project already has separate endpoints, but the mental model is not fixed enough yet.

Without a strict distinction, it is easy to mix up:

- "already synchronized to frontend"
- "already read by the user"

### 4. Realtime updates are not the same as final truth

The current implementation can tolerate a user having multiple WebSocket connections, and the frontend currently creates websocket connections from multiple component paths.

That is not automatically incorrect, but it means realtime events can be duplicated, missed during reconnect windows, or processed in different places. The backend must therefore treat REST reconciliation as mandatory.

### 5. First-message send path is not fully idempotent

The current first-message flow is effectively:

1. query session by `sessionId`
2. create session if not found
3. insert message

Under concurrent first-message requests, the session creation path can hit the unique index on `chat_session.session_id`. That should be converted into a safe, expected path instead of a raw failure.

## Recommended Architecture

Do not redesign the chat system around websocket-only semantics.

The recommended architecture remains:

1. `message facts`
   Source of truth: `chat_message`

2. `session summaries`
   Source of truth: `GET /chat-session/page`

3. `global unread total`
   Source of truth: `GET /chat-session/unread-total`

4. `realtime acceleration`
   Source of truth: post-commit websocket push, but only as an optimization layer

5. `reconciliation`
   Source of truth: `history`, `pull`, `page`, and `unread-total`

This keeps the current chat model intact while making the recovery path explicit and reliable.

## State Model

The chat module must distinguish four different state layers.

### 1. Message Fact State

Question answered:

- which messages exist

Source of truth:

- `chat_message`

Semantics:

- a message exists if it has been successfully persisted
- websocket delivery success is not required for message existence

### 2. Synchronization State

Question answered:

- which messages have already been synchronized to the frontend

Source of truth:

- frontend-held `lastMessageId`
- server-side pull semantics

Semantics:

- `lastMessageId` means the maximum message id already accepted into the local message list
- it is a sync cursor
- it does not imply the user has read those messages

### 3. Read State

Question answered:

- which inbound messages the user has actually seen

Source of truth:

- frontend-held `upToMessageId`
- backend read updates through `POST /chat-message/read`

Semantics:

- `upToMessageId` means the largest inbound message id in the current session that has truly entered the visible reading area while the page is visible
- it is a read watermark
- it must not be inferred from the last loaded message

### 4. Summary State

Question answered:

- what the current message center should show

Source of truth:

- `GET /chat-session/page`
- `GET /chat-session/unread-total`

Semantics:

- session list, per-session unread count, session ordering, and global unread total are all summary state
- they must be re-queryable after missed realtime updates

## Cursor and Watermark Semantics

### `lastMessageId`

Official meaning:

- the largest message id already synchronized into the local frontend state

Used by:

- `GET /chat-message/pull`

Answers:

- which new messages am I still missing locally

Does not answer:

- which messages have I read

### `upToMessageId`

Official meaning:

- the largest inbound message id in the current session that the user has actually seen

Used by:

- `POST /chat-message/read`

Answers:

- up to which message can the current user truthfully confirm read

Does not answer:

- which messages have been synchronized locally

### Required Separation

The system must explicitly allow this state:

- `lastMessageId = 200`
- `upToMessageId = 180`

This means:

- messages are synchronized to 200
- the user has only read to 180

That is valid and expected.

## API Semantics

This design keeps the current API family and fixes their responsibilities instead of replacing them.

### `POST /chat-session/send`

Responsibilities:

- validate sender, receiver, house, and content
- enforce chat business rules
- create or update the session row
- persist the chat message
- trigger post-commit websocket push

Not responsible for:

- guaranteeing that the receiver instantly sees the message in realtime
- guaranteeing that frontend summary state updates without reconciliation

### `GET /chat-message/history`

Responsibilities:

- load older messages for one session
- support upward scrolling in a chat page

Primary parameters:

- `sessionId`
- `beforeMessageId`

Question answered:

- what earlier messages should be shown above the current window

### `GET /chat-message/pull`

Responsibilities:

- load newer messages missing from local frontend state
- support current-session catch-up after reconnect or fallback recovery

Primary parameters:

- `lastMessageId`
- optional `sessionId`
- `limit`

Question answered:

- what newer messages do I still need to synchronize locally

Not responsible for:

- changing read state

### `POST /chat-message/read`

Responsibilities:

- advance read watermark for the current user inside one session
- batch-mark inbound unread messages as read up to a conservative visible boundary

Primary parameters:

- `sessionId`
- `upToMessageId`

Question answered:

- up to which inbound message can the user confirm actual reading

Not responsible for:

- message synchronization
- session summary reconstruction

### `GET /chat-session/page`

Responsibilities:

- rebuild the current user's session summary snapshot
- return per-session last message summary, ordering, peer info, and unread count

Question answered:

- what should the session list look like right now

### `GET /chat-session/unread-total`

Responsibilities:

- rebuild the current user's total chat unread count

Question answered:

- what should the global chat unread badge show right now

## Realtime Delivery Semantics

### Core Rule

Realtime websocket push is an optimization, not the final truth source.

The actual reliability order remains:

1. persist session and message
2. commit transaction
3. push websocket event after commit
4. reconcile later through REST if realtime delivery is missed

### WebSocket Payload Semantics

This design does not require a full websocket protocol rewrite.

The current approach of pushing `ChatMessage` can remain for this stability-first version, as long as the system explicitly accepts that:

- websocket payloads are message events
- frontend summary state derived from websocket can be wrong temporarily
- REST reconciliation must overwrite derived state after reconnect or page entry

### Multi-Connection Compatibility

The backend must continue to support one user having multiple active websocket sessions.

Reason:

- the current backend already stores `userId -> Set<WebSocketSession>`
- current frontend structure may create more than one websocket connection
- multiple browser tabs and future multi-device cases also naturally require this compatibility

This design does not require the backend to enforce single-connection behavior.

## Reconnect, Recovery, and Reconciliation

The system must recover along two separate lines:

1. current-session continuity
2. global summary reconciliation

### Current-Session Continuity

If the user currently has an active chat page:

- use `GET /chat-message/history` for initial message window
- use `GET /chat-message/pull` with `sessionId + lastMessageId` after reconnect to catch missed session messages

This solves:

- missing messages in the currently open chat

### Global Summary Reconciliation

After websocket reconnect succeeds, the frontend must refresh:

- `GET /chat-session/page`
- `GET /chat-session/unread-total`

This solves:

- newly active sessions from other senders during disconnect
- outdated session ordering
- incorrect per-session unread counts
- incorrect global chat badge

### Required Reconnect Flow

After websocket reconnect succeeds:

1. if a chat page is open, pull missed messages for the current session
2. refresh `GET /chat-session/page`
3. refresh `GET /chat-session/unread-total`

These three actions together define reconnect recovery.

### Entry-Point Recovery Rules

When user enters `/messages`:

- always request `GET /chat-session/page`
- always request `GET /chat-session/unread-total`

When user enters `/chat/:sessionId`:

- request `GET /chat-message/history`
- establish or reuse websocket realtime path
- after websocket open, request `GET /chat-message/pull` for that session

### Self-Healing Rule

The chat module must be self-healing.

Even if websocket events are missed, the system must recover true state after any of these actions:

- websocket reconnect success
- entering the messages page
- entering a chat page
- manual refresh

Self-healing sources:

- current session messages: `history + pull`
- session summaries: `page`
- unread badge: `unread-total`

## Read Semantics

### Official Read Rule

A message is eligible to advance read state only if all of these conditions are true:

1. it belongs to the current session
2. it is inbound for the current user
3. the chat page is visible
4. the message bubble has entered the current scroll container viewport
5. the visible ratio reaches the threshold

The current threshold remains:

- `0.6`

### Read Watermark Selection

The frontend must submit:

- the maximum visible inbound message id

It must not submit:

- the last loaded message id
- the last history result id
- the last pull result id

### Why Batch Read Can Remain

The backend may continue using the current batch update model:

- current user is the receiver
- same session
- `id <= upToMessageId`
- `status = 0`

This remains valid because the frontend watermark is now conservative and visibility-based.

### Read-State Restrictions

The following actions must not auto-advance read state by themselves:

- websocket message arrival
- successful `pull`
- successful `history`
- entering the chat page before a message becomes visible

Sync does not equal read.

### History Loading Rule

Older historical messages may be marked read later if:

- they are loaded
- they enter the visible area
- the page is visible

Loading history alone must not auto-read it.

## Session Summary and Unread Rules

### Session Summary Truth

Per-session summary truth belongs to:

- `GET /chat-session/page`

This includes:

- session ordering
- `lastMsgContent`
- `updateTime`
- `peerName`
- per-session unread count

### Global Unread Truth

Global chat unread truth belongs to:

- `GET /chat-session/unread-total`

### Derived Local State

The frontend may optimistically derive summary and unread changes from websocket events, but those derived values are temporary.

They must be overwritten by:

- `page`
- `unread-total`

after reconnect or explicit page entry.

### Example Reconciliation Scenario

If user A is chatting with user B and disconnects, and user C sends A a message during the disconnect window:

- current-session pull for `A <-> B` cannot reconstruct the new `A <-> C` session summary
- `GET /chat-session/page` is required to surface the new session
- `GET /chat-session/unread-total` is required to correct the global unread badge

This is why current-session recovery and global summary reconciliation must remain separate.

## Idempotency and Concurrency

### Existing Risk

The current send path is vulnerable during first-message concurrency:

1. request A checks session and finds nothing
2. request B checks session and finds nothing
3. request A inserts the session
4. request B hits the unique constraint on `session_id`

This should not be treated as an abnormal business failure.

### Required Session-Level Idempotency

The send path must become safe under concurrent first-message creation.

Recommended behavior:

- keep `chat_session.session_id` as the unique business key
- if session insert collides with the unique key, convert it into the normal "session already exists" path
- continue with safe session update and message insert

Recommended implementation choices:

1. catch duplicate-key exception and re-query the session
2. or replace the first-create path with an explicit upsert pattern

Either approach is acceptable. The implementation must choose one and keep it consistent.

### Optional Message-Level Idempotency

This design allows adding a small request field such as `clientMessageId` in the future, but it does not require message-level deduplication in this stability-first version.

Required for this version:

- session creation must be idempotent

Optional for later:

- message submission deduplication across retries

## Data Model and Schema Adjustments

This design prefers the smallest possible schema adjustments.

### Keep Existing Tables

Continue using:

- `chat_session`
- `chat_message`

### Keep Existing Session Key Model

Continue using:

- `sessionId = minUserId_maxUserId_houseId`

Reason:

- it matches the current business rule that one renter and one publisher have one session per house
- it avoids duplicated sessions caused by sender-order differences

### Required Index Expectations

The design continues to rely on:

- unique index on `chat_session.session_id`
- message indexes supporting:
  - session history by `(session_id, id)`
  - unread counting by `(receiver_id, status)`
  - read updates by `(receiver_id, session_id, status, id)`

No mandatory new table is introduced in this design.

## Error Handling Principles

### Send Failures

If session unique-key collision happens during concurrent first-message creation:

- do not expose raw SQL errors to the client
- treat it as an expected concurrency case
- recover into the existing-session path if possible

### Pull and Reconciliation Failures

If `pull`, `page`, or `unread-total` fails during reconnect recovery:

- the system must allow retry
- the frontend should treat local summary state as potentially stale
- later successful reconciliation must overwrite stale derived state

This design does not require a large new UI for recovery errors, but it does require the protocol to support retries and self-healing.

### Read Failures

If read update fails:

- do not advance the durable notion of read
- local optimistic state may be temporarily shown, but later `page` and `unread-total` queries must be allowed to correct it

## Recommended Frontend Constraints

This is primarily a backend-oriented design, but a few frontend constraints are necessary because backend semantics depend on them.

### Realtime Connection Model

The backend should remain compatible with multiple websocket connections per user.

The recommended frontend direction is:

- one logical app-level websocket connection per frontend runtime
- internal distribution of websocket events to:
  - current chat view
  - session summary state
  - global unread state
  - toast logic

This design does not require the frontend refactor to happen in the same change set, but it assumes that REST reconciliation remains the real consistency mechanism while the frontend is still in transition.

### Summary Refresh Timing

Frontend must refresh:

- `page`
- `unread-total`

at least on:

- messages page entry
- websocket reconnect success

### Current Session Catch-Up Timing

Frontend must refresh:

- `pull` for the current session

at least on:

- chat page websocket open
- reconnect success while the chat page is active

## Implementation Order

1. fix the read watermark selection rule in the chat page so it uses visible inbound message ids
2. formalize reconnect recovery so websocket reconnect triggers:
   - current-session `pull`
   - `page`
   - `unread-total`
3. ensure messages page entry always reloads `page` and `unread-total`
4. harden the send path against concurrent first-message session creation
5. normalize error handling so duplicate-key and reconciliation failures become expected, recoverable paths
6. add regression tests for read semantics, reconnect reconciliation, and first-message concurrency handling

This order reduces risk by correcting the most user-visible correctness issue first, then closing the bigger recovery gaps, and finally hardening concurrency.

## Main Risks

- read state still advancing too far if frontend continues using the wrong watermark
- reconnect recovery only fixing the current session while leaving other sessions stale
- duplicate websocket processing causing temporary local summary drift
- first-message concurrent sends still surfacing raw constraint errors
- optimistic unread updates diverging from backend truth

The design addresses these by:

- separating sync cursor from read watermark
- requiring `page` and `unread-total` reconciliation after reconnect
- treating websocket updates as temporary derived state
- making session creation idempotent at the unique-key boundary
- using REST snapshot endpoints as final correction sources

## Test Focus

Recommended coverage for this hardening version:

- visible inbound messages advance read watermark correctly
- loaded but non-visible inbound messages do not become read
- history loading alone does not mark messages as read
- websocket reconnect triggers current-session pull
- websocket reconnect triggers `GET /chat-session/page`
- websocket reconnect triggers `GET /chat-session/unread-total`
- a message arriving in another session during disconnect becomes visible after reconciliation
- concurrent first-message session creation does not expose a raw duplicate-key failure
- `lastMessageId` based pull and `upToMessageId` based read remain semantically separate

## Summary

This stability-first design keeps the current chat module architecture but removes the major semantic gaps that currently make it fragile.

The core design rules are:

- persist first, push later
- websocket is acceleration, not truth
- `lastMessageId` is a sync cursor
- `upToMessageId` is a read watermark
- read is visibility-based, not load-based
- current-session recovery uses `history + pull`
- global message center recovery uses `page + unread-total`
- session creation must be idempotent under concurrency

This is the smallest design that can make the current chat module production-usable without expanding scope into a larger chat v2 rewrite.

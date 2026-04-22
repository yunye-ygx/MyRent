# Notification V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add notification v1 with online chat pop-ups, aggregated unread badges, a persistent notification inbox, and publisher-follow notifications for new houses.

**Architecture:** Keep chat unread state inside the existing `chat_message` and `chat_session` flow, and add a separate `notification` inbox for non-chat business events. Add `publisher_follow` as an independent relationship from `house_favorite`, then wire `HouseCommandServiceImpl` to generate inbox notifications on house create, update, and delete after the house write commits.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, MySQL 8, Vue 3, Pinia, Vue Router, Axios, Vitest, JUnit 5, Mockito, Maven

---

## File Map

### Backend

- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\sql\rent-schema\notification.sql`
  Responsibility: define the notification inbox table with unread and idempotency fields.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\sql\rent-schema\publisher_follow.sql`
  Responsibility: define the publisher-follow relationship table.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\sql\rent-schema\rent-schema-all.sql`
  Responsibility: keep the aggregate schema script in sync.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\common\NotificationType.java`
  Responsibility: centralize inbox type constants.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\entity\Notification.java`
  Responsibility: map the `notification` table.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\entity\PublisherFollow.java`
  Responsibility: map the `publisher_follow` table.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\mapper\NotificationMapper.java`
  Responsibility: base mapper for notification rows.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\mapper\PublisherFollowMapper.java`
  Responsibility: base mapper for publisher-follow rows.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\INotificationService.java`
  Responsibility: define inbox query, read, unread-total, and business notification generation operations.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\IPublisherFollowService.java`
  Responsibility: define follow, unfollow, and follow-status operations.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\impl\NotificationServiceImpl.java`
  Responsibility: persist inbox notifications, calculate unread totals, and generate house-change and new-house events.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\impl\PublisherFollowServiceImpl.java`
  Responsibility: enforce publisher follow rules and relation reactivation.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\controller\NotificationController.java`
  Responsibility: expose inbox page, unread total, single read, and read-all endpoints.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\controller\PublisherFollowController.java`
  Responsibility: expose publisher follow endpoints.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\vo\UnreadTotalVO.java`
  Responsibility: standardize unread-total responses for chat and inbox.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\vo\PublisherFollowStatusVO.java`
  Responsibility: return follow-state payload for house detail.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\impl\HouseCommandServiceImpl.java`
  Responsibility: generate notifications after house create, update, and delete commits.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\IChatSessionService.java`
  Responsibility: expose chat unread-total summary.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\impl\ChatSessionServiceImpl.java`
  Responsibility: implement unread-total query.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\controller\ChatSessionController.java`
  Responsibility: expose `GET /chat-session/unread-total`.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\mapper\ChatSessionMapper.java`
  Responsibility: add unread-total query contract.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\resources\mapper\ChatSessionMapper.xml`
  Responsibility: implement unread-total SQL.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\controller\HouseControllerWebMvcTest.java`
  Responsibility: mock new mapper beans that WebMvc slices now need.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\service\impl\NotificationServiceImplTest.java`
  Responsibility: cover notification generation, idempotency, and read actions.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\service\impl\PublisherFollowServiceImplTest.java`
  Responsibility: cover follow, unfollow, and re-follow rules.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\service\impl\HouseCommandServiceImplTest.java`
  Responsibility: verify house command paths dispatch notification hooks.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\controller\ChatSessionControllerWebMvcTest.java`
  Responsibility: verify chat unread-total endpoint contract.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\controller\NotificationControllerWebMvcTest.java`
  Responsibility: verify inbox page, unread total, and read endpoints.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\controller\PublisherFollowControllerWebMvcTest.java`
  Responsibility: verify follow endpoints and response shapes.

### Frontend

- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\api\notification.js`
  Responsibility: call inbox page, unread total, read, and read-all endpoints.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\api\publisherFollow.js`
  Responsibility: call follow, unfollow, and follow-status endpoints.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\api\chat.js`
  Responsibility: add `fetchChatUnreadTotal`.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\stores\messageCenter.js`
  Responsibility: own chat unread total, notification unread total, and online chat toast queue.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\NotificationInboxItem.vue`
  Responsibility: render one inbox row.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\chat\OnlineMessageToast.vue`
  Responsibility: render one online chat pop-up card.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\house\HouseDetailSummary.vue`
  Responsibility: show publisher follow action beside publisher metadata.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\views\HouseDetailView.vue`
  Responsibility: load follow status and trigger follow/unfollow actions.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\views\MessagesView.vue`
  Responsibility: render `Chat / Notifications` tabs, inbox list, unread badges, and read-all action.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\layout\AppTopNav.vue`
  Responsibility: render aggregate unread badge on desktop nav.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\AppTabBar.vue`
  Responsibility: render aggregate unread badge on mobile message tab.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\layouts\MainLayout.vue`
  Responsibility: bootstrap unread totals and render global online chat toast stack.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\router\index.js`
  Responsibility: no new route, but leave route names stable for notification redirects.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\design\site.js`
  Responsibility: keep nav item metadata stable while badges render in components.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\views\__tests__\MessagesView.spec.js`
  Responsibility: verify tab switching, unread badges, and inbox actions.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\views\__tests__\HouseDetailView.spec.js`
  Responsibility: verify publisher follow button state and action.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\__tests__\AppTopNav.spec.js`
  Responsibility: verify aggregate unread badge on desktop nav.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\__tests__\AppTabBar.spec.js`
  Responsibility: verify aggregate unread badge on mobile tab.
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\layouts\__tests__\MainLayout.spec.js`
  Responsibility: verify global online toast rendering.
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\stores\__tests__\messageCenter.spec.js`
  Responsibility: verify unread-total aggregation and pop-up suppression rules.

### Task 1: Add schema, entities, and shared constants

**Files:**
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\sql\rent-schema\notification.sql`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\sql\rent-schema\publisher_follow.sql`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\sql\rent-schema\rent-schema-all.sql`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\common\NotificationType.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\entity\Notification.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\entity\PublisherFollow.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\vo\UnreadTotalVO.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\vo\PublisherFollowStatusVO.java`
- Test: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\service\impl\NotificationServiceImplTest.java`

- [ ] **Step 1: Write the failing backend contract test for new inbox types**

Create `NotificationServiceImplTest.java` with this starter test:

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.NotificationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationServiceImplTest {

    @Test
    void notificationTypeShouldExposeInboxEventConstants() {
        assertEquals("HOUSE_OFFLINE", NotificationType.HOUSE_OFFLINE);
        assertEquals("HOUSE_RENTED", NotificationType.HOUSE_RENTED);
        assertEquals("HOUSE_DELETED", NotificationType.HOUSE_DELETED);
        assertEquals("HOUSE_PRICE_CHANGED", NotificationType.HOUSE_PRICE_CHANGED);
        assertEquals("PUBLISHER_NEW_HOUSE", NotificationType.PUBLISHER_NEW_HOUSE);
    }
}
```

- [ ] **Step 2: Run the targeted backend test to verify it fails**

Run:

```bash
mvn -Dtest=NotificationServiceImplTest#notificationTypeShouldExposeInboxEventConstants test
```

Expected: FAIL because `NotificationType` does not exist yet.

- [ ] **Step 3: Add the schema scripts, entities, and shared value objects**

Create `notification.sql`:

```sql
CREATE TABLE IF NOT EXISTS `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(64) NOT NULL,
  `title` varchar(128) NOT NULL,
  `content` varchar(255) NOT NULL,
  `biz_key` varchar(255) NOT NULL,
  `redirect_type` varchar(64) NOT NULL DEFAULT 'house_detail',
  `redirect_target_id` bigint NOT NULL,
  `extra_json` varchar(1000) DEFAULT NULL,
  `is_read` tinyint NOT NULL DEFAULT '0',
  `read_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_user_biz` (`user_id`, `biz_key`),
  KEY `idx_notification_user_read_time` (`user_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知收件箱';
```

Create `publisher_follow.sql`:

```sql
CREATE TABLE IF NOT EXISTS `publisher_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `publisher_user_id` bigint NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `cancel_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_user_publisher` (`user_id`, `publisher_user_id`),
  KEY `idx_follow_publisher_status` (`publisher_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发布者关注关系';
```

Append both table definitions to `rent-schema-all.sql`, then add these Java files:

```java
// src/main/java/cn/yy/myrent/common/NotificationType.java
package cn.yy.myrent.common;

public final class NotificationType {

    public static final String HOUSE_OFFLINE = "HOUSE_OFFLINE";
    public static final String HOUSE_RENTED = "HOUSE_RENTED";
    public static final String HOUSE_DELETED = "HOUSE_DELETED";
    public static final String HOUSE_PRICE_CHANGED = "HOUSE_PRICE_CHANGED";
    public static final String PUBLISHER_NEW_HOUSE = "PUBLISHER_NEW_HOUSE";

    private NotificationType() {
    }
}
```

```java
// src/main/java/cn/yy/myrent/entity/Notification.java
package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("notification")
public class Notification implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String type;

    private String title;

    private String content;

    @TableField("biz_key")
    private String bizKey;

    @TableField("redirect_type")
    private String redirectType;

    @TableField("redirect_target_id")
    private Long redirectTargetId;

    @TableField("extra_json")
    private String extraJson;

    @TableField("is_read")
    private Integer isRead;

    @TableField("read_time")
    private LocalDateTime readTime;

    @TableField("create_time")
    private LocalDateTime createTime;
}
```

```java
// src/main/java/cn/yy/myrent/entity/PublisherFollow.java
package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("publisher_follow")
public class PublisherFollow implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("publisher_user_id")
    private Long publisherUserId;

    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("cancel_time")
    private LocalDateTime cancelTime;
}
```

```java
// src/main/java/cn/yy/myrent/vo/UnreadTotalVO.java
package cn.yy.myrent.vo;

import lombok.Data;

@Data
public class UnreadTotalVO {

    private Long total;
}
```

```java
// src/main/java/cn/yy/myrent/vo/PublisherFollowStatusVO.java
package cn.yy.myrent.vo;

import lombok.Data;

@Data
public class PublisherFollowStatusVO {

    private Long publisherUserId;
    private Boolean following;
}
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
mvn -Dtest=NotificationServiceImplTest#notificationTypeShouldExposeInboxEventConstants test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sql/rent-schema/notification.sql sql/rent-schema/publisher_follow.sql sql/rent-schema/rent-schema-all.sql src/main/java/cn/yy/myrent/common/NotificationType.java src/main/java/cn/yy/myrent/entity/Notification.java src/main/java/cn/yy/myrent/entity/PublisherFollow.java src/main/java/cn/yy/myrent/vo/UnreadTotalVO.java src/main/java/cn/yy/myrent/vo/PublisherFollowStatusVO.java src/test/java/cn/yy/myrent/service/impl/NotificationServiceImplTest.java
git commit -m "feat(notification): add notification and publisher follow schema"
```

### Task 2: Add notification inbox and publisher-follow backend contracts

**Files:**
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\mapper\NotificationMapper.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\mapper\PublisherFollowMapper.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\INotificationService.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\IPublisherFollowService.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\controller\NotificationController.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\controller\PublisherFollowController.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\controller\NotificationControllerWebMvcTest.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\controller\PublisherFollowControllerWebMvcTest.java`

- [ ] **Step 1: Write the failing WebMvc tests**

Create `NotificationControllerWebMvcTest.java`:

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.service.INotificationService;
import cn.yy.myrent.vo.UnreadTotalVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private INotificationService notificationService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @Test
    void unreadTotalShouldReturnCurrentUserInboxCount() throws Exception {
        UnreadTotalVO vo = new UnreadTotalVO();
        vo.setTotal(3L);
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(notificationService.buildUnreadTotal(1001L)).willReturn(vo);

        mockMvc.perform(get("/notification/unread-total").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    void pageShouldReturnLatestNotificationRows() throws Exception {
        Notification item = new Notification()
                .setId(8L)
                .setUserId(1001L)
                .setType("HOUSE_PRICE_CHANGED")
                .setTitle("Price changed")
                .setContent("The monthly price changed from 5200 to 5000.")
                .setRedirectType("house_detail")
                .setRedirectTargetId(7L)
                .setIsRead(0)
                .setCreateTime(LocalDateTime.of(2026, 4, 22, 10, 0));
        Page<Notification> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(item));

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(notificationService.pageMine(1001L, 1L, 10L)).willReturn(page);

        mockMvc.perform(get("/notification/page").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].type").value("HOUSE_PRICE_CHANGED"))
                .andExpect(jsonPath("$.data.records[0].redirectTargetId").value(7));
    }
}
```

Create `PublisherFollowControllerWebMvcTest.java`:

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.service.IPublisherFollowService;
import cn.yy.myrent.vo.PublisherFollowStatusVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublisherFollowController.class)
class PublisherFollowControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IPublisherFollowService publisherFollowService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @Test
    void statusShouldReturnCurrentFollowState() throws Exception {
        PublisherFollowStatusVO vo = new PublisherFollowStatusVO();
        vo.setPublisherUserId(9L);
        vo.setFollowing(true);

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(publisherFollowService.getStatus(9L, 1001L)).willReturn(vo);

        mockMvc.perform(get("/publisher-follow/9/status").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(true));
    }

    @Test
    void followShouldReturnUpdatedFollowState() throws Exception {
        PublisherFollowStatusVO vo = new PublisherFollowStatusVO();
        vo.setPublisherUserId(9L);
        vo.setFollowing(true);

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(publisherFollowService.follow(9L, 1001L)).willReturn(vo);

        mockMvc.perform(post("/publisher-follow/9").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publisherUserId").value(9))
                .andExpect(jsonPath("$.data.following").value(true));
    }
}
```

- [ ] **Step 2: Run the controller tests to verify they fail**

Run:

```bash
mvn -Dtest=NotificationControllerWebMvcTest,PublisherFollowControllerWebMvcTest test
```

Expected: FAIL because the controllers and service contracts do not exist yet.

- [ ] **Step 3: Add mapper, service, and controller contracts**

Create the base mappers:

```java
package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface NotificationMapper extends BaseMapper<Notification> {
}
```

```java
package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.PublisherFollow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface PublisherFollowMapper extends BaseMapper<PublisherFollow> {
}
```

Create the service interfaces:

```java
package cn.yy.myrent.service;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.vo.UnreadTotalVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface INotificationService extends IService<Notification> {

    Page<Notification> pageMine(Long userId, Long current, Long size);

    UnreadTotalVO buildUnreadTotal(Long userId);

    void markRead(Long notificationId, Long userId);

    void markAllRead(Long userId);

    void notifyHouseCreated(House house);

    void notifyHouseUpdated(House oldHouse, House newHouse);

    void notifyHouseDeleted(House oldHouse);
}
```

```java
package cn.yy.myrent.service;

import cn.yy.myrent.vo.PublisherFollowStatusVO;

public interface IPublisherFollowService {

    PublisherFollowStatusVO follow(Long publisherUserId, Long userId);

    PublisherFollowStatusVO unfollow(Long publisherUserId, Long userId);

    PublisherFollowStatusVO getStatus(Long publisherUserId, Long userId);
}
```

Create `NotificationController.java`:

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.service.INotificationService;
import cn.yy.myrent.vo.UnreadTotalVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final INotificationService notificationService;

    @GetMapping("/page")
    public Result<Page<Notification>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(notificationService.pageMine(userId, current, size));
    }

    @GetMapping("/unread-total")
    public Result<UnreadTotalVO> unreadTotal() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(notificationService.buildUnreadTotal(userId));
    }

    @PostMapping("/read/{id}")
    public Result<Void> read(@PathVariable("id") Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        notificationService.markRead(id, userId);
        return Result.success();
    }

    @PostMapping("/read-all")
    public Result<Void> readAll() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        notificationService.markAllRead(userId);
        return Result.success();
    }
}
```

Create `PublisherFollowController.java`:

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.service.IPublisherFollowService;
import cn.yy.myrent.vo.PublisherFollowStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/publisher-follow")
@RequiredArgsConstructor
public class PublisherFollowController {

    private final IPublisherFollowService publisherFollowService;

    @PostMapping("/{publisherUserId}")
    public Result<PublisherFollowStatusVO> follow(@PathVariable Long publisherUserId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(publisherFollowService.follow(publisherUserId, userId));
    }

    @DeleteMapping("/{publisherUserId}")
    public Result<PublisherFollowStatusVO> unfollow(@PathVariable Long publisherUserId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(publisherFollowService.unfollow(publisherUserId, userId));
    }

    @GetMapping("/{publisherUserId}/status")
    public Result<PublisherFollowStatusVO> status(@PathVariable Long publisherUserId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(publisherFollowService.getStatus(publisherUserId, userId));
    }
}
```

- [ ] **Step 4: Run the controller tests to verify they pass**

Run:

```bash
mvn -Dtest=NotificationControllerWebMvcTest,PublisherFollowControllerWebMvcTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/mapper/NotificationMapper.java src/main/java/cn/yy/myrent/mapper/PublisherFollowMapper.java src/main/java/cn/yy/myrent/service/INotificationService.java src/main/java/cn/yy/myrent/service/IPublisherFollowService.java src/main/java/cn/yy/myrent/controller/NotificationController.java src/main/java/cn/yy/myrent/controller/PublisherFollowController.java src/test/java/cn/yy/myrent/controller/NotificationControllerWebMvcTest.java src/test/java/cn/yy/myrent/controller/PublisherFollowControllerWebMvcTest.java
git commit -m "feat(notification): add inbox and follow controller contracts"
```

### Task 3: Implement publisher-follow rules and inbox query/read behavior

**Files:**
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\impl\PublisherFollowServiceImpl.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\impl\NotificationServiceImpl.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\service\impl\PublisherFollowServiceImplTest.java`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\service\impl\NotificationServiceImplTest.java`

- [ ] **Step 1: Write the failing service tests**

Create `PublisherFollowServiceImplTest.java`:

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.PublisherFollow;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.PublisherFollowMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.vo.PublisherFollowStatusVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublisherFollowServiceImplTest {

    @Mock
    private PublisherFollowMapper publisherFollowMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private PublisherFollowServiceImpl publisherFollowService;

    @Test
    void followShouldCreateActiveRelation() {
        when(userMapper.selectById(9L)).thenReturn(new User().setId(9L).setName("Publisher"));
        when(publisherFollowMapper.selectOne(any())).thenReturn(null);
        when(publisherFollowMapper.insert(any(PublisherFollow.class))).thenReturn(1);

        PublisherFollowStatusVO result = publisherFollowService.follow(9L, 1001L);

        assertTrue(result.getFollowing());
    }

    @Test
    void unfollowShouldReturnInactiveState() {
        PublisherFollow relation = new PublisherFollow().setId(7L).setUserId(1001L).setPublisherUserId(9L).setStatus(1);
        when(userMapper.selectById(9L)).thenReturn(new User().setId(9L).setName("Publisher"));
        when(publisherFollowMapper.selectOne(any())).thenReturn(relation);
        when(publisherFollowMapper.updateById(any(PublisherFollow.class))).thenReturn(1);

        PublisherFollowStatusVO result = publisherFollowService.unfollow(9L, 1001L);

        assertFalse(result.getFollowing());
    }
}
```

Append these tests to `NotificationServiceImplTest.java`:

```java
@Mock
private NotificationMapper notificationMapper;

@Mock
private HouseFavoriteMapper houseFavoriteMapper;

@Mock
private PublisherFollowMapper publisherFollowMapper;

@InjectMocks
private NotificationServiceImpl notificationService;

@Test
void buildUnreadTotalShouldCountUnreadRows() {
    when(notificationMapper.selectCount(any())).thenReturn(4L);

    UnreadTotalVO result = notificationService.buildUnreadTotal(1001L);

    assertEquals(4L, result.getTotal());
}

@Test
void markAllReadShouldUpdateUnreadRowsForCurrentUser() {
    when(notificationMapper.update(any(), any())).thenReturn(2);

    assertDoesNotThrow(() -> notificationService.markAllRead(1001L));
}
```

- [ ] **Step 2: Run the targeted service tests to verify they fail**

Run:

```bash
mvn -Dtest=PublisherFollowServiceImplTest,NotificationServiceImplTest test
```

Expected: FAIL because the implementations do not exist yet.

- [ ] **Step 3: Implement the follow and inbox query/read services**

Create `PublisherFollowServiceImpl.java`:

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.PublisherFollow;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.PublisherFollowMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IPublisherFollowService;
import cn.yy.myrent.vo.PublisherFollowStatusVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PublisherFollowServiceImpl implements IPublisherFollowService {

    private static final int STATUS_ACTIVE = 1;

    private final PublisherFollowMapper publisherFollowMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublisherFollowStatusVO follow(Long publisherUserId, Long userId) {
        validatePublisher(publisherUserId, userId);
        PublisherFollow existing = findRelation(publisherUserId, userId);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            publisherFollowMapper.insert(new PublisherFollow()
                    .setUserId(userId)
                    .setPublisherUserId(publisherUserId)
                    .setStatus(STATUS_ACTIVE)
                    .setCreateTime(now));
        } else if (existing.getStatus() == null || existing.getStatus() != STATUS_ACTIVE) {
            existing.setStatus(STATUS_ACTIVE);
            existing.setCancelTime(null);
            publisherFollowMapper.updateById(existing);
        }
        return buildStatus(publisherUserId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublisherFollowStatusVO unfollow(Long publisherUserId, Long userId) {
        validatePublisher(publisherUserId, userId);
        PublisherFollow existing = findRelation(publisherUserId, userId);
        if (existing != null && existing.getStatus() != null && existing.getStatus() == STATUS_ACTIVE) {
            existing.setStatus(0);
            existing.setCancelTime(LocalDateTime.now());
            publisherFollowMapper.updateById(existing);
        }
        return buildStatus(publisherUserId, false);
    }

    @Override
    public PublisherFollowStatusVO getStatus(Long publisherUserId, Long userId) {
        validatePublisher(publisherUserId, userId);
        PublisherFollow existing = findRelation(publisherUserId, userId);
        return buildStatus(publisherUserId, existing != null && Integer.valueOf(STATUS_ACTIVE).equals(existing.getStatus()));
    }

    private PublisherFollow findRelation(Long publisherUserId, Long userId) {
        return publisherFollowMapper.selectOne(new LambdaQueryWrapper<PublisherFollow>()
                .eq(PublisherFollow::getUserId, userId)
                .eq(PublisherFollow::getPublisherUserId, publisherUserId));
    }

    private void validatePublisher(Long publisherUserId, Long userId) {
        if (publisherUserId == null || userId == null) {
            throw new IllegalArgumentException("user id cannot be null");
        }
        if (publisherUserId.equals(userId)) {
            throw new IllegalArgumentException("cannot follow yourself");
        }
        User publisher = userMapper.selectById(publisherUserId);
        if (publisher == null) {
            throw new IllegalArgumentException("publisher not found");
        }
    }

    private PublisherFollowStatusVO buildStatus(Long publisherUserId, boolean following) {
        PublisherFollowStatusVO vo = new PublisherFollowStatusVO();
        vo.setPublisherUserId(publisherUserId);
        vo.setFollowing(following);
        return vo;
    }
}
```

Create `NotificationServiceImpl.java` with page/read behavior first:

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.NotificationMapper;
import cn.yy.myrent.mapper.PublisherFollowMapper;
import cn.yy.myrent.service.INotificationService;
import cn.yy.myrent.vo.UnreadTotalVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements INotificationService {

    private final HouseFavoriteMapper houseFavoriteMapper;
    private final PublisherFollowMapper publisherFollowMapper;

    public NotificationServiceImpl(HouseFavoriteMapper houseFavoriteMapper, PublisherFollowMapper publisherFollowMapper) {
        this.houseFavoriteMapper = houseFavoriteMapper;
        this.publisherFollowMapper = publisherFollowMapper;
    }

    @Override
    public Page<Notification> pageMine(Long userId, Long current, Long size) {
        long safeCurrent = Math.max(current == null ? 1L : current, 1L);
        long safeSize = Math.min(Math.max(size == null ? 10L : size, 1L), 50L);
        return this.page(new Page<>(safeCurrent, safeSize), new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getId));
    }

    @Override
    public UnreadTotalVO buildUnreadTotal(Long userId) {
        long total = this.count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        UnreadTotalVO vo = new UnreadTotalVO();
        vo.setTotal(total);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long notificationId, Long userId) {
        this.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .setSql("read_time = NOW()"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        this.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .setSql("read_time = NOW()"));
    }

    @Override
    public void notifyHouseCreated(House house) {
    }

    @Override
    public void notifyHouseUpdated(House oldHouse, House newHouse) {
    }

    @Override
    public void notifyHouseDeleted(House oldHouse) {
    }
}
```

- [ ] **Step 4: Run the targeted service tests to verify they pass**

Run:

```bash
mvn -Dtest=PublisherFollowServiceImplTest,NotificationServiceImplTest test
```

Expected: PASS for follow/unfollow and unread/read behavior.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/PublisherFollowServiceImpl.java src/main/java/cn/yy/myrent/service/impl/NotificationServiceImpl.java src/test/java/cn/yy/myrent/service/impl/PublisherFollowServiceImplTest.java src/test/java/cn/yy/myrent/service/impl/NotificationServiceImplTest.java
git commit -m "feat(notification): implement inbox queries and publisher follow rules"
```

### Task 4: Generate house-change and new-house notifications after commit

**Files:**
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\impl\NotificationServiceImpl.java`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\impl\HouseCommandServiceImpl.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\service\impl\HouseCommandServiceImplTest.java`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\service\impl\NotificationServiceImplTest.java`

- [ ] **Step 1: Add the failing event-generation tests**

Append to `NotificationServiceImplTest.java`:

```java
@Test
void notifyHouseUpdatedShouldInsertPriceChangeNotificationsForActiveFavorites() {
    House oldHouse = new House().setId(7L).setTitle("Tianhe One Bed").setPrice(5200).setStatus(1);
    House newHouse = new House().setId(7L).setTitle("Tianhe One Bed").setPrice(5000).setStatus(1);

    HouseFavorite favorite = new HouseFavorite().setUserId(1001L).setHouseId(7L).setStatus(1);
    when(houseFavoriteMapper.selectList(any())).thenReturn(List.of(favorite));
    when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

    notificationService.notifyHouseUpdated(oldHouse, newHouse);

    verify(notificationMapper).insert(argThat(item ->
            item.getUserId().equals(1001L)
                    && item.getType().equals(NotificationType.HOUSE_PRICE_CHANGED)
                    && item.getBizKey().equals("house:7:price:5200->5000")));
}

@Test
void notifyHouseCreatedShouldInsertNewHouseNotificationsForFollowers() {
    House house = new House().setId(8L).setPublisherUserId(9L).setTitle("New listing").setPrice(4300).setStatus(1);

    PublisherFollow follow = new PublisherFollow().setUserId(1002L).setPublisherUserId(9L).setStatus(1);
    when(publisherFollowMapper.selectList(any())).thenReturn(List.of(follow));
    when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

    notificationService.notifyHouseCreated(house);

    verify(notificationMapper).insert(argThat(item ->
            item.getUserId().equals(1002L)
                    && item.getType().equals(NotificationType.PUBLISHER_NEW_HOUSE)
                    && item.getBizKey().equals("publisher:9:house:8:new")));
}
```

Create `HouseCommandServiceImplTest.java`:

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.sync.house.classifier.HouseChangeClassificationResult;
import cn.yy.myrent.sync.house.classifier.HouseChangeClassifier;
import cn.yy.myrent.sync.house.HouseSyncDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseCommandServiceImplTest {

    @Mock
    private HouseSyncDispatcher houseSyncDispatcher;

    @Mock
    private HouseChangeClassifier houseChangeClassifier;

    @Mock
    private INotificationService notificationService;

    @InjectMocks
    private HouseCommandServiceImpl houseCommandService;

    @Test
    void createHouseShouldDispatchNewHouseNotification() {
        House house = new House().setId(8L).setPublisherUserId(9L).setTitle("New listing").setPrice(4300).setStatus(1);
        doNothing().when(notificationService).notifyHouseCreated(any(House.class));

        HouseCommandServiceImpl spy = org.mockito.Mockito.spy(houseCommandService);
        org.mockito.Mockito.doReturn(true).when(spy).save(any(House.class));

        assertTrue(spy.createHouseWithSync(house));

        verify(notificationService).notifyHouseCreated(house);
    }
}
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run:

```bash
mvn -Dtest=NotificationServiceImplTest,HouseCommandServiceImplTest test
```

Expected: FAIL because notification generation logic and house command wiring are not implemented.

- [ ] **Step 3: Implement notification generation and house-command hooks**

Update `NotificationServiceImpl.java` with helper methods:

```java
private static final int ACTIVE_STATUS = 1;

@Override
@Transactional(rollbackFor = Exception.class)
public void notifyHouseCreated(House house) {
    if (house == null || house.getId() == null || house.getPublisherUserId() == null) {
        return;
    }
    publisherFollowMapper.selectList(new LambdaQueryWrapper<PublisherFollow>()
                    .eq(PublisherFollow::getPublisherUserId, house.getPublisherUserId())
                    .eq(PublisherFollow::getStatus, ACTIVE_STATUS))
            .forEach(follow -> insertInbox(
                    follow.getUserId(),
                    NotificationType.PUBLISHER_NEW_HOUSE,
                    "Publisher posted a new house",
                    house.getTitle() + " is now available.",
                    "publisher:" + house.getPublisherUserId() + ":house:" + house.getId() + ":new",
                    house.getId(),
                    "{\"houseId\":" + house.getId() + "}"));
}

@Override
@Transactional(rollbackFor = Exception.class)
public void notifyHouseUpdated(House oldHouse, House newHouse) {
    if (oldHouse == null || newHouse == null || oldHouse.getId() == null) {
        return;
    }
    if (oldHouse.getPrice() != null && newHouse.getPrice() != null && !oldHouse.getPrice().equals(newHouse.getPrice())) {
        fanoutToFavoriteUsers(
                oldHouse.getId(),
                NotificationType.HOUSE_PRICE_CHANGED,
                "Price changed",
                "The monthly price changed from " + oldHouse.getPrice() + " to " + newHouse.getPrice() + ".",
                "house:" + oldHouse.getId() + ":price:" + oldHouse.getPrice() + "->" + newHouse.getPrice(),
                newHouse.getId());
    }
    if (!equalsStatus(oldHouse.getStatus(), newHouse.getStatus()) && Integer.valueOf(2).equals(newHouse.getStatus())) {
        fanoutToFavoriteUsers(oldHouse.getId(), NotificationType.HOUSE_RENTED, "House rented", oldHouse.getTitle() + " has been rented.", "house:" + oldHouse.getId() + ":type:HOUSE_RENTED:version:" + normalizeVersion(newHouse), newHouse.getId());
    }
    if (!equalsStatus(oldHouse.getStatus(), newHouse.getStatus()) && Integer.valueOf(0).equals(newHouse.getStatus())) {
        fanoutToFavoriteUsers(oldHouse.getId(), NotificationType.HOUSE_OFFLINE, "House offline", oldHouse.getTitle() + " is now offline.", "house:" + oldHouse.getId() + ":type:HOUSE_OFFLINE:version:" + normalizeVersion(newHouse), newHouse.getId());
    }
}

@Override
@Transactional(rollbackFor = Exception.class)
public void notifyHouseDeleted(House oldHouse) {
    if (oldHouse == null || oldHouse.getId() == null) {
        return;
    }
    fanoutToFavoriteUsers(oldHouse.getId(), NotificationType.HOUSE_DELETED, "House deleted", oldHouse.getTitle() + " is no longer available.", "house:" + oldHouse.getId() + ":type:HOUSE_DELETED:version:delete", oldHouse.getId());
}

private void fanoutToFavoriteUsers(Long houseId, String type, String title, String content, String bizKey, Long targetHouseId) {
    houseFavoriteMapper.selectList(new LambdaQueryWrapper<HouseFavorite>()
                    .eq(HouseFavorite::getHouseId, houseId)
                    .eq(HouseFavorite::getStatus, ACTIVE_STATUS))
            .forEach(favorite -> insertInbox(favorite.getUserId(), type, title, content, bizKey, targetHouseId, "{\"houseId\":" + targetHouseId + "}"));
}

private void insertInbox(Long userId, String type, String title, String content, String bizKey, Long targetId, String extraJson) {
    try {
        this.save(new Notification()
                .setUserId(userId)
                .setType(type)
                .setTitle(title)
                .setContent(content)
                .setBizKey(bizKey)
                .setRedirectType("house_detail")
                .setRedirectTargetId(targetId)
                .setExtraJson(extraJson)
                .setIsRead(0));
    } catch (Exception ignore) {
        // rely on (user_id, biz_key) unique index to swallow duplicate retries
    }
}

private boolean equalsStatus(Integer left, Integer right) {
    return left == null ? right == null : left.equals(right);
}

private int normalizeVersion(House house) {
    return house.getVersion() == null ? 0 : house.getVersion();
}
```

Update `HouseCommandServiceImpl.java`:

```java
@Autowired
private INotificationService notificationService;
```

Inside `createHouseWithSync` after save:

```java
dispatchCoreEvent(house.getId(), HouseSyncConstants.EVENT_HOUSE_ES_UPSERT, "house-create");
notificationService.notifyHouseCreated(house);
```

Inside `updateHouseWithSync` after `updateById(...)` succeeds:

```java
House newHouse = this.getById(id);
notificationService.notifyHouseUpdated(oldHouse, newHouse);
```

Inside `deleteHouseWithSync` before remove:

```java
House oldHouse = this.getById(id);
```

Then after `dispatchCoreEvent(...)`:

```java
notificationService.notifyHouseDeleted(oldHouse);
```

- [ ] **Step 4: Run the targeted tests to verify they pass**

Run:

```bash
mvn -Dtest=NotificationServiceImplTest,HouseCommandServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/NotificationServiceImpl.java src/main/java/cn/yy/myrent/service/impl/HouseCommandServiceImpl.java src/test/java/cn/yy/myrent/service/impl/NotificationServiceImplTest.java src/test/java/cn/yy/myrent/service/impl/HouseCommandServiceImplTest.java
git commit -m "feat(notification): generate inbox events from house mutations"
```

### Task 5: Add chat unread-total backend endpoint

**Files:**
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\mapper\ChatSessionMapper.java`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\resources\mapper\ChatSessionMapper.xml`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\IChatSessionService.java`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\service\impl\ChatSessionServiceImpl.java`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\main\java\cn\yy\myrent\controller\ChatSessionController.java`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\src\test\java\cn\yy\myrent\controller\ChatSessionControllerWebMvcTest.java`

- [ ] **Step 1: Write the failing unread-total controller test**

Create `ChatSessionControllerWebMvcTest.java`:

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.service.IChatSessionService;
import cn.yy.myrent.vo.UnreadTotalVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatSessionController.class)
class ChatSessionControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IChatSessionService chatSessionService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @Test
    void unreadTotalShouldReturnAggregatedChatCount() throws Exception {
        UnreadTotalVO vo = new UnreadTotalVO();
        vo.setTotal(6L);
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(chatSessionService.buildUnreadTotal(1001L)).willReturn(vo);

        mockMvc.perform(get("/chat-session/unread-total").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(6));
    }
}
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run:

```bash
mvn -Dtest=ChatSessionControllerWebMvcTest test
```

Expected: FAIL because `GET /chat-session/unread-total` does not exist.

- [ ] **Step 3: Add unread-total mapper, service, and controller code**

Update `ChatSessionMapper.java`:

```java
long countUnreadMessages(@Param("userId") Long userId);
```

Add to `ChatSessionMapper.xml`:

```xml
<select id="countUnreadMessages" resultType="long">
    SELECT COUNT(1)
    FROM chat_message
    WHERE receiver_id = #{userId}
      AND status = 0
</select>
```

Update `IChatSessionService.java`:

```java
UnreadTotalVO buildUnreadTotal(Long userId);
```

Update `ChatSessionServiceImpl.java`:

```java
@Override
public UnreadTotalVO buildUnreadTotal(Long userId) {
    UnreadTotalVO vo = new UnreadTotalVO();
    vo.setTotal(this.baseMapper.countUnreadMessages(userId));
    return vo;
}
```

Update `ChatSessionController.java`:

```java
@GetMapping("/unread-total")
public Result<UnreadTotalVO> unreadTotal() {
    Long userId = UserContext.getCurrentUserId();
    if (userId == null) {
        return Result.error(401, "please login first");
    }
    return Result.success(chatSessionService.buildUnreadTotal(userId));
}
```

- [ ] **Step 4: Run the controller test to verify it passes**

Run:

```bash
mvn -Dtest=ChatSessionControllerWebMvcTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/mapper/ChatSessionMapper.java src/main/resources/mapper/ChatSessionMapper.xml src/main/java/cn/yy/myrent/service/IChatSessionService.java src/main/java/cn/yy/myrent/service/impl/ChatSessionServiceImpl.java src/main/java/cn/yy/myrent/controller/ChatSessionController.java src/test/java/cn/yy/myrent/controller/ChatSessionControllerWebMvcTest.java
git commit -m "feat(chat): add unread total endpoint"
```

### Task 6: Add frontend API helpers and message-center store

**Files:**
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\api\notification.js`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\api\publisherFollow.js`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\api\chat.js`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\stores\messageCenter.js`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\stores\__tests__\messageCenter.spec.js`

- [ ] **Step 1: Write the failing store tests**

Create `messageCenter.spec.js`:

```js
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
```

- [ ] **Step 2: Run the frontend store test to verify it fails**

Run:

```bash
npm --prefix frontend run test:run -- src/stores/__tests__/messageCenter.spec.js
```

Expected: FAIL because the store and API helpers do not exist yet.

- [ ] **Step 3: Add API helpers and the message-center store**

Create `frontend/src/api/notification.js`:

```js
import http from './http'

export function fetchNotificationPage(params = {}) {
  return http.get('/notification/page', { params })
}

export function fetchNotificationUnreadTotal() {
  return http.get('/notification/unread-total')
}

export function markNotificationRead(id) {
  return http.post(`/notification/read/${id}`)
}

export function markAllNotificationsRead() {
  return http.post('/notification/read-all')
}
```

Create `frontend/src/api/publisherFollow.js`:

```js
import http from './http'

export function fetchPublisherFollowStatus(publisherUserId) {
  return http.get(`/publisher-follow/${publisherUserId}/status`)
}

export function followPublisher(publisherUserId) {
  return http.post(`/publisher-follow/${publisherUserId}`)
}

export function unfollowPublisher(publisherUserId) {
  return http.delete(`/publisher-follow/${publisherUserId}`)
}
```

Update `frontend/src/api/chat.js`:

```js
export function fetchChatUnreadTotal() {
  return http.get('/chat-session/unread-total')
}
```

Create `frontend/src/stores/messageCenter.js`:

```js
import { defineStore } from 'pinia'
import { fetchChatUnreadTotal } from '@/api/chat'
import { fetchNotificationUnreadTotal } from '@/api/notification'

export const useMessageCenterStore = defineStore('messageCenter', {
  state: () => ({
    chatUnreadTotal: 0,
    notificationUnreadTotal: 0,
    currentChatSessionId: '',
    chatToasts: []
  }),
  getters: {
    totalUnread(state) {
      return Number(state.chatUnreadTotal || 0) + Number(state.notificationUnreadTotal || 0)
    }
  },
  actions: {
    async loadUnreadTotals() {
      const [chat, notification] = await Promise.all([
        fetchChatUnreadTotal(),
        fetchNotificationUnreadTotal()
      ])
      this.chatUnreadTotal = Number(chat?.total || 0)
      this.notificationUnreadTotal = Number(notification?.total || 0)
    },
    setCurrentChatSession(sessionId) {
      this.currentChatSessionId = String(sessionId || '')
    },
    setChatUnreadTotal(total) {
      this.chatUnreadTotal = Number(total || 0)
    },
    setNotificationUnreadTotal(total) {
      this.notificationUnreadTotal = Number(total || 0)
    },
    decrementNotificationUnread() {
      this.notificationUnreadTotal = Math.max(0, Number(this.notificationUnreadTotal || 0) - 1)
    },
    resetChatToastQueue() {
      this.chatToasts = []
    },
    dismissChatToast(id) {
      this.chatToasts = this.chatToasts.filter((item) => item.id !== id)
    },
    pushIncomingChatToast(message) {
      if (!message?.sessionId || String(message.sessionId) === this.currentChatSessionId) {
        return
      }
      this.chatToasts = [
        ...this.chatToasts,
        {
          id: `${message.sessionId}-${message.id || Date.now()}`,
          sessionId: message.sessionId,
          senderName: message.senderName || `User ${message.senderId || ''}`,
          content: String(message.content || '').slice(0, 30),
          peerId: message.senderId,
          houseId: message.houseId
        }
      ]
    }
  }
})
```

- [ ] **Step 4: Run the frontend store test to verify it passes**

Run:

```bash
npm --prefix frontend run test:run -- src/stores/__tests__/messageCenter.spec.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/notification.js frontend/src/api/publisherFollow.js frontend/src/api/chat.js frontend/src/stores/messageCenter.js frontend/src/stores/__tests__/messageCenter.spec.js
git commit -m "feat(frontend): add notification api helpers and message center store"
```

### Task 7: Add publisher follow action on house detail

**Files:**
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\house\HouseDetailSummary.vue`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\views\HouseDetailView.vue`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\views\__tests__\HouseDetailView.spec.js`

- [ ] **Step 1: Extend the failing detail-page test**

Append to `HouseDetailView.spec.js`:

```js
import { followPublisher, fetchPublisherFollowStatus } from '@/api/publisherFollow'

vi.mock('@/api/publisherFollow', () => ({
  fetchPublisherFollowStatus: vi.fn().mockResolvedValue({ publisherUserId: 9, following: false }),
  followPublisher: vi.fn().mockResolvedValue({ publisherUserId: 9, following: true }),
  unfollowPublisher: vi.fn()
}))

it('shows and updates the publisher follow action', async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/house/:id', component: HouseDetailView }]
  })

  router.push('/house/7')
  await router.isReady()

  const wrapper = mount(HouseDetailView, {
    global: {
      plugins: [router]
    }
  })

  await flushPromises()

  const followButton = wrapper.find('[data-test="publisher-follow"]')
  expect(fetchPublisherFollowStatus).toHaveBeenCalledWith(9)
  expect(followButton.text()).toContain('Follow')

  await followButton.trigger('click')
  await flushPromises()

  expect(followPublisher).toHaveBeenCalledWith(9)
})
```

- [ ] **Step 2: Run the targeted frontend test to verify it fails**

Run:

```bash
npm --prefix frontend run test:run -- src/views/__tests__/HouseDetailView.spec.js
```

Expected: FAIL because the follow button and follow-status loading do not exist yet.

- [ ] **Step 3: Implement follow-state loading and the UI**

Update `HouseDetailSummary.vue` props and template:

```vue
<div>
  <dt>Publisher</dt>
  <dd class="publisher-row">
    <span>{{ publisherName }}</span>
    <button
      v-if="canFollowPublisher"
      data-test="publisher-follow"
      class="ghost-btn"
      :disabled="publisherFollowLoading"
      @click="$emit('publisher-follow')"
    >
      {{ publisherFollowText }}
    </button>
  </dd>
</div>
```

```js
publisherFollowLoading: {
  type: Boolean,
  default: false
},
publisherFollowText: {
  type: String,
  default: 'Follow'
},
canFollowPublisher: {
  type: Boolean,
  default: false
}
```

```js
defineEmits(['back', 'publisher-follow'])
```

Update `HouseDetailView.vue` imports and state:

```js
import { fetchPublisherFollowStatus, followPublisher, unfollowPublisher } from '@/api/publisherFollow'

const publisherFollowLoading = ref(false)
const publisherFollowStatus = ref({ publisherUserId: null, following: false })

const canFollowPublisher = computed(() => (
  Boolean(authStore.userId)
  && Boolean(house.value?.publisherUserId)
  && String(authStore.userId) !== String(house.value.publisherUserId)
))

const publisherFollowText = computed(() => (
  publisherFollowLoading.value
    ? 'Processing...'
    : publisherFollowStatus.value?.following
      ? 'Following'
      : 'Follow'
))
```

Add loader and action:

```js
async function loadPublisherFollowStatus() {
  publisherFollowStatus.value = {
    publisherUserId: house.value?.publisherUserId || null,
    following: false
  }
  if (!canFollowPublisher.value) {
    return
  }
  try {
    publisherFollowStatus.value = await fetchPublisherFollowStatus(house.value.publisherUserId)
  } catch {
    publisherFollowStatus.value = {
      publisherUserId: house.value.publisherUserId,
      following: false
    }
  }
}

async function togglePublisherFollow() {
  if (!canFollowPublisher.value || publisherFollowLoading.value) {
    return
  }
  publisherFollowLoading.value = true
  try {
    publisherFollowStatus.value = publisherFollowStatus.value?.following
      ? await unfollowPublisher(house.value.publisherUserId)
      : await followPublisher(house.value.publisherUserId)
  } catch (err) {
    window.alert(formatRequestError(err, 'Publisher follow action failed.'))
  } finally {
    publisherFollowLoading.value = false
  }
}
```

Call `loadPublisherFollowStatus()` inside `loadHouse()` after `loadPublisher()` and pass props to `HouseDetailSummary`:

```vue
:publisher-follow-loading="publisherFollowLoading"
:publisher-follow-text="publisherFollowText"
:can-follow-publisher="canFollowPublisher"
@publisher-follow="togglePublisherFollow"
```

- [ ] **Step 4: Run the targeted frontend test to verify it passes**

Run:

```bash
npm --prefix frontend run test:run -- src/views/__tests__/HouseDetailView.spec.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/house/HouseDetailSummary.vue frontend/src/views/HouseDetailView.vue frontend/src/views/__tests__/HouseDetailView.spec.js
git commit -m "feat(frontend): add publisher follow action on house detail"
```

### Task 8: Refactor `/messages` into chat and notifications tabs

**Files:**
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\views\MessagesView.vue`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\NotificationInboxItem.vue`
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\views\__tests__\MessagesView.spec.js`

- [ ] **Step 1: Write the failing messages-page test**

Create `MessagesView.spec.js`:

```js
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import MessagesView from '@/views/MessagesView.vue'

vi.mock('@/api/chat', () => ({
  fetchSessionPage: vi.fn().mockResolvedValue({
    records: [
      { sessionId: '1_9_7', peerId: 9, peerName: 'Landlord A', unreadCount: 2, houseId: 7, houseTitle: 'Tianhe One Bed' }
    ]
  })
}))

vi.mock('@/api/notification', () => ({
  fetchNotificationPage: vi.fn().mockResolvedValue({
    records: [
      { id: 5, type: 'HOUSE_PRICE_CHANGED', title: 'Price changed', content: 'Monthly price is now 5000.', redirectTargetId: 7, isRead: 0 }
    ]
  }),
  markNotificationRead: vi.fn().mockResolvedValue({}),
  markAllNotificationsRead: vi.fn().mockResolvedValue({})
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    chatUnreadTotal: 2,
    notificationUnreadTotal: 1,
    decrementNotificationUnread: vi.fn(),
    setNotificationUnreadTotal: vi.fn()
  })
}))

describe('MessagesView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders chat and notifications tabs with unread badges', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/messages', component: MessagesView }, { path: '/chat/:sessionId', component: { template: '<div />' } }]
    })
    router.push('/messages')
    await router.isReady()

    const wrapper = mount(MessagesView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    expect(wrapper.text()).toContain('Chat (2)')
    expect(wrapper.text()).toContain('Notifications (1)')
    expect(wrapper.text()).toContain('Landlord A')

    await wrapper.get('[data-tab="notifications"]').trigger('click')
    expect(wrapper.text()).toContain('Price changed')
  })
})
```

- [ ] **Step 2: Run the frontend test to verify it fails**

Run:

```bash
npm --prefix frontend run test:run -- src/views/__tests__/MessagesView.spec.js
```

Expected: FAIL because the tabbed UI and inbox rendering do not exist yet.

- [ ] **Step 3: Implement inbox item rendering and the tabbed messages page**

Create `NotificationInboxItem.vue`:

```vue
<template>
  <article class="notification app-surface" :class="{ unread: Number(item.isRead || 0) === 0 }" @click="$emit('click')">
    <div class="head">
      <h3>{{ item.title }}</h3>
      <span v-if="Number(item.isRead || 0) === 0" class="badge">New</span>
    </div>
    <p class="copy">{{ item.content }}</p>
  </article>
</template>

<script setup>
defineProps({
  item: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])
</script>
```

Replace `MessagesView.vue` with a tabbed flow:

```vue
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchSessionPage } from '@/api/chat'
import { fetchNotificationPage, markAllNotificationsRead, markNotificationRead } from '@/api/notification'
import NotificationInboxItem from '@/components/NotificationInboxItem.vue'
import SessionItem from '@/components/SessionItem.vue'
import { useChatSessionList } from '@/composables/useChatSessionList'
import { useMessageCenterStore } from '@/stores/messageCenter'

const router = useRouter()
const messageCenterStore = useMessageCenterStore()
const activeTab = ref('chat')
const notificationLoading = ref(false)
const notificationError = ref('')
const notifications = ref([])
const { loading, error, sessions, loadSessions } = useChatSessionList(fetchSessionPage)

const chatTabLabel = computed(() => `Chat (${messageCenterStore.chatUnreadTotal})`)
const notificationTabLabel = computed(() => `Notifications (${messageCenterStore.notificationUnreadTotal})`)

async function loadNotifications() {
  notificationLoading.value = true
  notificationError.value = ''
  try {
    const page = await fetchNotificationPage({ current: 1, size: 20 })
    notifications.value = Array.isArray(page?.records) ? page.records : []
  } catch (err) {
    notificationError.value = err?.message || 'Notifications unavailable'
    notifications.value = []
  } finally {
    notificationLoading.value = false
  }
}

async function openNotification(item) {
  if (Number(item?.isRead || 0) === 0) {
    await markNotificationRead(item.id)
    item.isRead = 1
    messageCenterStore.decrementNotificationUnread()
  }
  router.push(`/house/${item.redirectTargetId}`)
}

async function markAllRead() {
  await markAllNotificationsRead()
  notifications.value = notifications.value.map((item) => ({ ...item, isRead: 1 }))
  messageCenterStore.setNotificationUnreadTotal(0)
}

function goChat(session) {
  router.push({
    path: `/chat/${session.sessionId}`,
    query: {
      peerId: String(session.peerId || ''),
      peerName: session.peerName || '',
      houseId: session.houseId ? String(session.houseId) : ''
    }
  })
}

onMounted(() => {
  loadSessions()
  loadNotifications()
})
</script>
```

Render tabs:

```vue
<button data-tab="chat" class="ghost-btn" :class="{ active: activeTab === 'chat' }" @click="activeTab = 'chat'">{{ chatTabLabel }}</button>
<button data-tab="notifications" class="ghost-btn" :class="{ active: activeTab === 'notifications' }" @click="activeTab = 'notifications'">{{ notificationTabLabel }}</button>
<button v-if="activeTab === 'notifications'" class="ghost-btn" @click="markAllRead">Mark all read</button>
```

Then render either the current `SessionItem` list or the new `NotificationInboxItem` list.

- [ ] **Step 4: Run the messages-page test to verify it passes**

Run:

```bash
npm --prefix frontend run test:run -- src/views/__tests__/MessagesView.spec.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/MessagesView.vue frontend/src/components/NotificationInboxItem.vue frontend/src/views/__tests__/MessagesView.spec.js
git commit -m "feat(frontend): add notifications tab to messages page"
```

### Task 9: Add global unread badges and online chat toast rendering

**Files:**
- Create: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\chat\OnlineMessageToast.vue`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\layout\AppTopNav.vue`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\AppTabBar.vue`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\layouts\MainLayout.vue`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\__tests__\AppTopNav.spec.js`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\components\__tests__\AppTabBar.spec.js`
- Modify: `C:\Users\黄昊\.codex\worktrees\55e8\MyRent\frontend\src\layouts\__tests__\MainLayout.spec.js`

- [ ] **Step 1: Extend the failing nav and layout tests**

Update `AppTopNav.spec.js`:

```js
vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    totalUnread: 5
  })
}))

expect(wrapper.text()).toContain('5')
```

Update `AppTabBar.spec.js`:

```js
vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    totalUnread: 3
  })
}))

expect(wrapper.text()).toContain('3')
```

Update `MainLayout.spec.js`:

```js
vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    chatToasts: [
      { id: 'toast-1', sessionId: '1_9_7', senderName: 'Landlord A', content: 'hello', peerId: 9, houseId: 7 }
    ],
    loadUnreadTotals: vi.fn(),
    dismissChatToast: vi.fn()
  })
}))

expect(wrapper.text()).toContain('Landlord A')
```

- [ ] **Step 2: Run the targeted frontend tests to verify they fail**

Run:

```bash
npm --prefix frontend run test:run -- src/components/__tests__/AppTopNav.spec.js src/components/__tests__/AppTabBar.spec.js src/layouts/__tests__/MainLayout.spec.js
```

Expected: FAIL because badges and toast rendering do not exist yet.

- [ ] **Step 3: Implement nav badges and the global online chat toast stack**

Create `OnlineMessageToast.vue`:

```vue
<template>
  <button class="toast app-surface" @click="$emit('click')">
    <strong>{{ toast.senderName }} sent a new message</strong>
    <p>{{ toast.content }}</p>
  </button>
</template>

<script setup>
defineProps({
  toast: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])
</script>
```

Update `AppTopNav.vue` to use the store and show a badge on the message nav item:

```vue
<script setup>
import { useMessageCenterStore } from '@/stores/messageCenter'

const messageCenterStore = useMessageCenterStore()

function isMessageItem(item) {
  return item.to === '/messages'
}
</script>
```

```vue
<RouterLink ...>
  <span>{{ item.label }}</span>
  <span v-if="isMessageItem(item) && messageCenterStore.totalUnread > 0" class="nav-badge">
    {{ messageCenterStore.totalUnread }}
  </span>
</RouterLink>
```

Update `AppTabBar.vue` similarly for `item.path === '/messages'`.

Update `MainLayout.vue`:

```vue
<template>
  <div class="app-shell">
    <div class="toast-stack">
      <OnlineMessageToast
        v-for="toast in messageCenterStore.chatToasts"
        :key="toast.id"
        :toast="toast"
        @click="openToast(toast)"
      />
    </div>
    <div class="app-container flex min-h-screen flex-col gap-6 py-5 lg:py-8">
      <AppTopNav :items="topNavItems" :current-path="route.path" />
      <main class="min-h-0 flex-1">
        <router-view />
      </main>
    </div>
    <AppTabBar class="lg:hidden" />
  </div>
</template>

<script setup>
import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import OnlineMessageToast from '@/components/chat/OnlineMessageToast.vue'
import { useMessageCenterStore } from '@/stores/messageCenter'

const route = useRoute()
const router = useRouter()
const messageCenterStore = useMessageCenterStore()

function openToast(toast) {
  messageCenterStore.dismissChatToast(toast.id)
  router.push({
    path: `/chat/${toast.sessionId}`,
    query: {
      peerId: String(toast.peerId || ''),
      peerName: toast.senderName || '',
      houseId: toast.houseId ? String(toast.houseId) : ''
    }
  })
}

watch(
  () => route.params.sessionId,
  (sessionId) => {
    messageCenterStore.setCurrentChatSession(sessionId)
  },
  { immediate: true }
)

onMounted(() => {
  messageCenterStore.loadUnreadTotals()
})
</script>
```

To feed the toast queue, update `useChatSessionList.js` after `upsertSessionFromMessage(payload)`:

```js
import { useMessageCenterStore } from '@/stores/messageCenter'

const messageCenterStore = useMessageCenterStore()
```

Then inside `ws.onmessage`:

```js
if (String(payload.receiverId) === String(authStore.userId || '')) {
  messageCenterStore.setChatUnreadTotal(Number(messageCenterStore.chatUnreadTotal || 0) + 1)
  messageCenterStore.pushIncomingChatToast(payload)
}
```

- [ ] **Step 4: Run the targeted frontend tests to verify they pass**

Run:

```bash
npm --prefix frontend run test:run -- src/components/__tests__/AppTopNav.spec.js src/components/__tests__/AppTabBar.spec.js src/layouts/__tests__/MainLayout.spec.js src/views/__tests__/MessagesView.spec.js src/views/__tests__/HouseDetailView.spec.js src/stores/__tests__/messageCenter.spec.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/chat/OnlineMessageToast.vue frontend/src/components/layout/AppTopNav.vue frontend/src/components/AppTabBar.vue frontend/src/layouts/MainLayout.vue frontend/src/composables/useChatSessionList.js frontend/src/components/__tests__/AppTopNav.spec.js frontend/src/components/__tests__/AppTabBar.spec.js frontend/src/layouts/__tests__/MainLayout.spec.js
git commit -m "feat(frontend): add unread badges and online chat toasts"
```

## Verification Sweep

- [ ] **Step 1: Run the full targeted backend test set**

Run:

```bash
mvn -Dtest=NotificationServiceImplTest,PublisherFollowServiceImplTest,HouseCommandServiceImplTest,ChatSessionControllerWebMvcTest,NotificationControllerWebMvcTest,PublisherFollowControllerWebMvcTest,HouseControllerWebMvcTest test
```

Expected: PASS across the new notification, follow, unread-total, and controller contract tests.

- [ ] **Step 2: Run the full targeted frontend test set**

Run:

```bash
npm --prefix frontend run test:run -- src/stores/__tests__/messageCenter.spec.js src/views/__tests__/MessagesView.spec.js src/views/__tests__/HouseDetailView.spec.js src/components/__tests__/AppTopNav.spec.js src/components/__tests__/AppTabBar.spec.js src/layouts/__tests__/MainLayout.spec.js
```

Expected: PASS across message-center, detail-page follow, messages tabs, nav badge, and global toast coverage.

- [ ] **Step 3: Run the frontend production build**

Run:

```bash
npm --prefix frontend run build
```

Expected: PASS with the new messages tabs, badges, and house-detail follow UI compiled successfully.

- [ ] **Step 4: Commit the verification-safe integration state**

```bash
git add .
git commit -m "feat: deliver notification v1"
```

## Self-Review

- Spec coverage: this plan covers online chat pop-ups, chat unread totals, inbox CRUD-lite behavior, favorite-house change notifications, publisher follow and new-house notifications, the `Chat / Notifications` messages page split, and aggregate unread badges on shared navigation.
- Placeholder scan: each task names exact files, code, and commands, with no deferred implementation markers left in the task body.
- Type consistency: the plan uses `UnreadTotalVO`, `PublisherFollowStatusVO`, `NotificationType`, `NotificationServiceImpl`, and `PublisherFollowServiceImpl` consistently across backend and frontend tasks.

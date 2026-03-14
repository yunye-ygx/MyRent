package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.ChatReadReqDTO;
import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.service.IChatMessageService;
import cn.yy.myrent.service.IChatSessionService;
import cn.yy.myrent.vo.ChatPullVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/chat-message")
public class ChatMessageController {

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private IChatSessionService chatSessionService;

    @GetMapping("/pull")
    @Operation(description = "重连后补拉新消息")
    public Result<ChatPullVO> pull(
            @RequestParam(value = "lastMessageId", required = false) Long lastMessageId,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        if (StringUtils.hasText(sessionId) && !hasSessionPermission(sessionId, userId)) {
            return Result.error(403, "无权访问该会话");
        }
        ChatPullVO result = chatMessageService.pullNewMessages(userId, lastMessageId, sessionId, limit);
        return Result.success(result);
    }

    @GetMapping("/history")
    @Operation(description = "上滑加载历史消息")
    public Result<ChatPullVO> history(
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "beforeMessageId", required = false) Long beforeMessageId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        if (!StringUtils.hasText(sessionId)) {
            return Result.error("sessionId不能为空");
        }
        if (beforeMessageId != null && beforeMessageId <= 0L) {
            return Result.error("beforeMessageId必须大于0");
        }
        if (!hasSessionPermission(sessionId, userId)) {
            return Result.error(403, "无权访问该会话");
        }

        ChatPullVO result = chatMessageService.pullHistoryMessages(userId, sessionId, beforeMessageId, limit);
        return Result.success(result);
    }

    @PostMapping("/read")
    @Operation(description = "批量回执已读")
    public Result<Integer> read(@RequestBody ChatReadReqDTO reqDTO) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        if (reqDTO == null
                || !StringUtils.hasText(reqDTO.getSessionId())
                || reqDTO.getUpToMessageId() == null
                || reqDTO.getUpToMessageId() <= 0L) {
            return Result.error("参数不能为空");
        }
        if (!hasSessionPermission(reqDTO.getSessionId(), userId)) {
            return Result.error(403, "无权操作该会话");
        }

        int updatedCount = chatMessageService.markMessagesRead(userId, reqDTO.getSessionId(), reqDTO.getUpToMessageId());
        return Result.success("已读回执成功", updatedCount);
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询消息")
    public Result<ChatMessage> getById(@PathVariable("id") Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        ChatMessage message = chatMessageService.getById(id);
        if (message == null) {
            return Result.error("消息不存在");
        }
        if (!userId.equals(message.getSenderId()) && !userId.equals(message.getReceiverId())) {
            return Result.error(403, "无权查看该消息");
        }

        chatMessageService.fillUserNames(Collections.singletonList(message));
        return Result.success(message);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询消息")
    public Result<Page<ChatMessage>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        if (StringUtils.hasText(sessionId) && !hasSessionPermission(sessionId, userId)) {
            return Result.error(403, "无权访问该会话");
        }

        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        Page<ChatMessage> page = chatMessageService.lambdaQuery()
                .and(wrapper -> wrapper.eq(ChatMessage::getSenderId, userId)
                        .or()
                        .eq(ChatMessage::getReceiverId, userId))
                .eq(StringUtils.hasText(sessionId), ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .page(new Page<>(safeCurrent, safeSize));
        chatMessageService.fillUserNames(page.getRecords());
        return Result.success(page);
    }

    @PostMapping
    @Operation(summary = "新增消息")
    public Result<Long> create(@RequestBody ChatMessage chatMessage) {
        chatMessage.setId(null);
        boolean saved = chatMessageService.save(chatMessage);
        if (!saved) {
            return Result.error("新增消息失败");
        }
        return Result.success("新增消息成功", chatMessage.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新消息")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody ChatMessage chatMessage) {
        chatMessage.setId(id);
        boolean updated = chatMessageService.updateById(chatMessage);
        if (!updated) {
            return Result.error("更新消息失败或消息不存在");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除消息")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean removed = chatMessageService.removeById(id);
        if (!removed) {
            return Result.error("删除消息失败或消息不存在");
        }
        return Result.success();
    }

    private boolean hasSessionPermission(String sessionId, Long userId) {
        if (!StringUtils.hasText(sessionId) || userId == null) {
            return false;
        }
        return chatSessionService.lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .and(wrapper -> wrapper.eq(ChatSession::getUserId1, userId)
                        .or()
                        .eq(ChatSession::getUserId2, userId))
                .count() > 0;
    }
}

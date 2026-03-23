package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.MessageDTO;
import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.service.IChatSessionService;
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

@RestController
@RequestMapping("/chat-session")
public class ChatSessionController {

    @Autowired
    private IChatSessionService chatSessionService;

    @PostMapping("/send")
    @Operation(description = "发送消息")
    public Result<ChatMessage> send(@RequestBody MessageDTO messageDTO) {
        Long senderId = UserContext.getCurrentUserId();
        if (senderId == null) {
            return Result.error(401, "请先登录");
        }
        if (messageDTO == null
                || messageDTO.getReceiverId() == null
                || messageDTO.getHouseId() == null
                || messageDTO.getHouseId() <= 0L
                || !StringUtils.hasText(messageDTO.getContent())) {
            return Result.error("参数不能为空");
        }
        try {
            messageDTO.setSenderId(senderId);
            ChatMessage chatMessage = chatSessionService.sendMessage(messageDTO);
            return Result.success(chatMessage);
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "发送失败" : e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询会话")
    public Result<ChatSession> getById(@PathVariable("id") Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        ChatSession session = chatSessionService.getById(id);
        if (session == null) {
            return Result.error("会话不存在");
        }
        if (!isSessionParticipant(session, userId)) {
            return Result.error(403, "无权访问该会话");
        }
        return Result.success(session);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询会话")
    public Result<Page<ChatSession>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        return Result.success(queryCurrentUserSessions(userId, current, size));
    }

    @GetMapping("/mine")
    @Operation(summary = "查询当前用户会话")
    public Result<Page<ChatSession>> mine(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        return Result.success(queryCurrentUserSessions(userId, current, size));
    }

    @PostMapping
    @Operation(summary = "新增会话")
    public Result<Long> create(@RequestBody ChatSession chatSession) {
        chatSession.setId(null);
        boolean saved = chatSessionService.save(chatSession);
        if (!saved) {
            return Result.error("新增会话失败");
        }
        return Result.success("新增会话成功", chatSession.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新会话")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody ChatSession chatSession) {
        chatSession.setId(id);
        boolean updated = chatSessionService.updateById(chatSession);
        if (!updated) {
            return Result.error("更新会话失败或会话不存在");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除会话")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean removed = chatSessionService.removeById(id);
        if (!removed) {
            return Result.error("删除会话失败或会话不存在");
        }
        return Result.success();
    }

    private boolean isSessionParticipant(ChatSession session, Long userId) {
        return session != null
                && userId != null
                && (userId.equals(session.getUserId1()) || userId.equals(session.getUserId2()));
    }

    private Page<ChatSession> queryCurrentUserSessions(Long userId, Long current, Long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        return chatSessionService.lambdaQuery()
                .and(wrapper -> wrapper.eq(ChatSession::getUserId1, userId)
                        .or()
                        .eq(ChatSession::getUserId2, userId))
                .orderByDesc(ChatSession::getUpdateTime)
                .page(new Page<>(safeCurrent, safeSize));
    }
}

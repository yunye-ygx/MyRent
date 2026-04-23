package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.MessageDTO;
import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.service.IChatSessionService;
import cn.yy.myrent.vo.ChatSessionSummaryVO;
import cn.yy.myrent.vo.UnreadTotalVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    @Operation(description = "Send a chat message")
    public Result<ChatMessage> send(@RequestBody MessageDTO messageDTO) {
        Long senderId = UserContext.getCurrentUserId();
        if (senderId == null) {
            return Result.error(401, "please login first");
        }
        if (messageDTO == null
                || messageDTO.getReceiverId() == null
                || messageDTO.getHouseId() == null
                || messageDTO.getHouseId() <= 0L
                || !StringUtils.hasText(messageDTO.getContent())) {
            return Result.error("message params cannot be empty");
        }
        try {
            messageDTO.setSenderId(senderId);
            ChatMessage chatMessage = chatSessionService.sendMessage(messageDTO);
            return Result.success(chatMessage);
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "send message failed" : e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get session by id")
    public Result<ChatSession> getById(@PathVariable("id") Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }

        ChatSession session = chatSessionService.getById(id);
        if (session == null) {
            return Result.error("session not found");
        }
        if (!isSessionParticipant(session, userId)) {
            return Result.error(403, "no permission to access this session");
        }
        return Result.success(session);
    }

    @GetMapping("/page")
    @Operation(summary = "Page session summaries")
    public Result<Page<ChatSessionSummaryVO>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }

        return Result.success(chatSessionService.querySessionSummaries(userId, current, size));
    }

    @GetMapping("/unread-total")
    public Result<UnreadTotalVO> unreadTotal() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }

        return Result.success(chatSessionService.buildUnreadTotal(userId));
    }

    @GetMapping("/mine")
    @Operation(summary = "Query current user session summaries")
    public Result<Page<ChatSessionSummaryVO>> mine(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }

        return Result.success(chatSessionService.querySessionSummaries(userId, current, size));
    }

    private boolean isSessionParticipant(ChatSession session, Long userId) {
        return session != null
                && userId != null
                && (userId.equals(session.getUserId1()) || userId.equals(session.getUserId2()));
    }
}

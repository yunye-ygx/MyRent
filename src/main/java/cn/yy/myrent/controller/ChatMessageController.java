package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.dto.ChatReadReqDTO;
import cn.yy.myrent.service.IChatMessageService;
import cn.yy.myrent.vo.ChatPullVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/chat-message")
public class ChatMessageController {

    @Autowired
    private IChatMessageService chatMessageService;

    @GetMapping("/pull")
    @Operation(description = "重连后补拉新消息")
    public Result<ChatPullVO> pull(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "lastMessageId", required = false) Long lastMessageId,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        if (userId == null) {
            return Result.error("userId不能为空");
        }
        ChatPullVO result = chatMessageService.pullNewMessages(userId, lastMessageId, sessionId, limit);
        return Result.success(result);
    }

    @PostMapping("/read")
    @Operation(description = "批量回执已读")
    public Result<Integer> read(@RequestBody ChatReadReqDTO reqDTO) {
        if (reqDTO == null
                || reqDTO.getUserId() == null
                || !StringUtils.hasText(reqDTO.getSessionId())
                || reqDTO.getUpToMessageId() == null
                || reqDTO.getUpToMessageId() <= 0L) {
            return Result.error("参数不能为空");
        }
        int updatedCount = chatMessageService.markMessagesRead(reqDTO.getUserId(), reqDTO.getSessionId(), reqDTO.getUpToMessageId());
        return Result.success("已读回执成功", updatedCount);
    }
}

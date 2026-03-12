package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.dto.MessageDTO;
import cn.yy.myrent.service.IChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 聊天会话列表 前端控制器
 * </p>
 *
 * @author yy
 * @since 2026-03-12
 */
@RestController
@RequestMapping("/chat-session")
public class ChatSessionController {

    @Autowired
    private IChatSessionService chatSessionService;

    @PostMapping("/send")
    @Operation(description = "发送信息")
    public Result send(@RequestBody MessageDTO messageDTO) {
        if (messageDTO == null
                || messageDTO.getSenderId() == null
                || messageDTO.getReceiverId() == null
                || !StringUtils.hasText(messageDTO.getContent())) {
            return Result.error("参数不能为空");
        }
        try {
            chatSessionService.sendMessage(messageDTO);
            return Result.success("发送成功", null);
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "发送失败" : e.getMessage());
        }
    }
}


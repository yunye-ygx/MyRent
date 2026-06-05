package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.dto.AiChatReqDTO;
import cn.yy.myrent.service.ai.chat.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final JwtTokenUtil jwtTokenUtil;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @Valid @RequestBody AiChatReqDTO reqDTO,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String tokenParam) {

        Long userId = resolveUserId(authorization, tokenParam);
        SseEmitter emitter = new SseEmitter(120_000L);
        aiChatService.chat(userId, reqDTO.getMessage(), reqDTO.getSessionId(), emitter);
        return emitter;
    }

    private Long resolveUserId(String authorization, String tokenParam) {
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        } else if (tokenParam != null && !tokenParam.isBlank()) {
            token = tokenParam;
        }
        if (token == null) {
            throw new IllegalStateException("未登录");
        }
        return jwtTokenUtil.parseUserId(token);
    }
}

package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.service.ai.AiRecommendService;
import cn.yy.myrent.vo.AiRecommendChatVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/ai-recommend")
@RequiredArgsConstructor
public class AiRecommendController {

    // Task 1 transition: optional injection avoids startup coupling until a dedicated toggle/fallback bean is added.
    private final Optional<AiRecommendService> aiRecommendService;

    @GetMapping("/session")
    public Result<AiRecommendChatVO> session() {
        if (aiRecommendService.isEmpty()) {
            return Result.error(503, "ai recommend service unavailable");
        }
        return Result.success(aiRecommendService.get().getOrCreateSession(UserContext.getCurrentUserId()));
    }

    @PostMapping("/chat")
    public Result<AiRecommendChatVO> chat(@Valid @RequestBody AiRecommendChatReqDTO reqDTO) {
        if (aiRecommendService.isEmpty()) {
            return Result.error(503, "ai recommend service unavailable");
        }
        return Result.success(aiRecommendService.get().chat(UserContext.getCurrentUserId(), reqDTO));
    }

    @PostMapping("/reset")
    public Result<AiRecommendChatVO> reset() {
        if (aiRecommendService.isEmpty()) {
            return Result.error(503, "ai recommend service unavailable");
        }
        return Result.success(aiRecommendService.get().reset(UserContext.getCurrentUserId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "invalid request";
        return Result.error(400, message);
    }
}

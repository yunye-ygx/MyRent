package cn.yy.myrent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class AiRecommendChatReqDTO {

    private String message;

    @Valid
    private AiRecommendInteractionDTO interaction;

    @AssertTrue(message = "message or interaction must be provided")
    public boolean hasUsableInput() {
        return message != null || interaction != null;
    }

    @AssertTrue(message = "message cannot be blank")
    public boolean isMessageValidWhenProvided() {
        return message == null || StringUtils.hasText(message) || interaction != null;
    }
}

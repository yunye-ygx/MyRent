package cn.yy.myrent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiRecommendChatReqDTO {

    @NotBlank(message = "message cannot be blank")
    private String message;
}

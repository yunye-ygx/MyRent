package cn.yy.myrent.dto;

import lombok.Data;

@Data
public class AiChatReqDTO {
    private String message;
    private Long sessionId;
}

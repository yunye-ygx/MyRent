package cn.yy.myrent.dto;

import lombok.Data;

@Data
public class ChatReadReqDTO {

    private String sessionId;

    private Long upToMessageId;
}

package cn.yy.myrent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionSummaryVO {

    private String sessionId;

    private Long peerId;

    private String peerName;

    private Long houseId;

    private String houseTitle;

    private String lastMsgContent;

    private LocalDateTime updateTime;

    private Integer unreadCount;
}

package cn.yy.myrent.sync.house.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HouseNormalRetryMessage {

    /**
     * 原始同步消息体（JSON字符串），重试时直接回投MQ。
     */
    private String messageBody;

    /**
     * 当前已重试次数。
     */
    private Integer retryCount;

    /**
     * 首次发送失败时间。
     */
    private LocalDateTime firstFailedTime;

    /**
     * 最近一次重试时间。
     */
    private LocalDateTime lastRetryTime;
}

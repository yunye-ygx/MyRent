package cn.yy.myrent.sync.house.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HouseSyncMessage {

    /**
     * 消息唯一标识，用于链路追踪与幂等识别。
     */
    private String messageId;

    /**
     * 房源ID，对应 house 表主键。
     */
    private Long houseId;

    /**
     * 事件类型。
     * 可选值：HOUSE_ES_UPSERT（新增/更新索引）、HOUSE_ES_DELETE（删除索引）。
     */
    private String eventType;

    /**
     * 是否核心事件。
     * true：核心字段变更，走本地任务表保障可靠投递。
     * false：普通字段变更，走直接MQ+补偿。
     */
    private Boolean coreEvent;

    /**
     * 事件产生时间（业务侧构建消息的时间）。
     */
    private LocalDateTime eventTime;
}

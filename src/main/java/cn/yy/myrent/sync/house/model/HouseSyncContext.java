package cn.yy.myrent.sync.house.model;

import lombok.Data;

@Data
public class HouseSyncContext {

    /**
     * 需要同步的房源ID。
     */
    private Long houseId;

    /**
     * 同步事件类型。
     * 可选值：HOUSE_ES_UPSERT、HOUSE_ES_DELETE。
     */
    private String eventType;

    /**
     * 是否核心事件。
     * true 走核心策略（本地任务表）；false 走普通策略（直发MQ+补偿）。
     */
    private boolean coreEvent;

    /**
     * 触发原因，便于日志定位。
     * 示例：house-create、house-update-core、order-timeout-release。
     */
    private String reason;
}

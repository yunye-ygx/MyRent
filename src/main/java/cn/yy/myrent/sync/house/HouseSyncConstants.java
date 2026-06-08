package cn.yy.myrent.sync.house;

public final class HouseSyncConstants {

    private HouseSyncConstants() {
    }

    public static final String BIZ_TYPE_HOUSE = "HOUSE";

    public static final String EVENT_HOUSE_CREATED = "HOUSE_CREATED";
    public static final String EVENT_HOUSE_UPDATED = "HOUSE_UPDATED";
    public static final String EVENT_HOUSE_PRICE_CHANGED = "HOUSE_PRICE_CHANGED";
    public static final String EVENT_HOUSE_RENTED = "HOUSE_RENTED";
    public static final String EVENT_HOUSE_OFFLINE = "HOUSE_OFFLINE";
    public static final String EVENT_HOUSE_DELETED = "HOUSE_DELETED";

    public static final int LOCAL_TASK_STATUS_PENDING = 0;
    public static final int LOCAL_TASK_MAX_RETRY_COUNT = 5;

    public static final String NORMAL_COMPENSATE_REDIS_LIST_KEY = "house:sync:normal:retry";
    public static final int NORMAL_COMPENSATE_MAX_RETRY = 10;
}


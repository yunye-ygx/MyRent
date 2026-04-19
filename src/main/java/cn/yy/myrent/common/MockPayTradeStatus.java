package cn.yy.myrent.common;

public final class MockPayTradeStatus {

    public static final int CREATED = 0;
    public static final int PAYING = 1;
    public static final int SUCCESS = 2;
    public static final int USER_CANCELLED = 3;
    public static final int CLOSED_TIMEOUT = 4;

    private MockPayTradeStatus() {
    }
}

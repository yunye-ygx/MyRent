package cn.yy.myrent.common;

public final class OrderStatus {

    public static final int UNPAID = 0;
    public static final int PAID_LOCKED = 1;
    public static final int CLOSED_TIMEOUT = 2;
    public static final int USER_CANCELLED = 3;
    public static final int REFUNDED = 4;

    private OrderStatus() {
    }
}

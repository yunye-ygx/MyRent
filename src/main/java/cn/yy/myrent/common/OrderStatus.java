package cn.yy.myrent.common;

public final class OrderStatus {

    public static final int UNPAID = 0;
    public static final int PAID = 1;
    public static final int PAID_LOCKED = PAID;
    public static final int CLOSED_TIMEOUT = 2;
    public static final int USER_CANCELLED = 3;
    public static final int REFUNDED = 4;
    public static final int COMPLETED = 5;
    public static final int REVIEWED = 6;

    private OrderStatus() {
    }
}

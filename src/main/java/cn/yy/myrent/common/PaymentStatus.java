package cn.yy.myrent.common;

public final class PaymentStatus {

    public static final int PENDING = 0;
    public static final int PAYING = 1;
    public static final int PAID = 2;
    public static final int USER_CANCELLED = 3;
    public static final int CLOSED_TIMEOUT = 4;
    public static final int DUPLICATE_PAID = 5;
    public static final int REFUNDED = 6;

    private PaymentStatus() {
    }
}

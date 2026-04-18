package cn.yy.myrent.common;

public final class PaymentStatus {

    public static final int WAITING = 0;
    public static final int SUCCESS = 1;
    public static final int CANCELLED = 3;
    public static final int CLOSED_TIMEOUT = 4;

    private PaymentStatus() {
    }
}

package cn.yy.myrent.common;

public final class PaymentRefundStatus {

    public static final int PENDING = 0;
    public static final int PROCESSING = 1;
    public static final int SUCCESS = 2;
    public static final int RETRY = 3;
    public static final int FAILED = 4;
    public static final int MANUAL_REVIEW = 5;
    public static final int CANCELLED = 6;

    private PaymentRefundStatus() {
    }
}

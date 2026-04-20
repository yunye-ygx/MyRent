package cn.yy.myrent.common;

public final class PaymentRefundSourceType {

    public static final int USER_APPLY = 1;
    public static final int LATE_SUCCESS_UNRECOVERABLE = 2;
    public static final int DUPLICATE_PAID = 3;
    public static final int ADMIN_MANUAL = 4;
    public static final int OTHER_COMPENSATION = 5;

    private PaymentRefundSourceType() {
    }
}

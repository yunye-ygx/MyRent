package cn.yy.myrent.common;

public enum PaymentRepairResult {
    PAID_WIN,
    DUPLICATE_CALLBACK,
    DUPLICATE_PAID,
    LATE_SUCCESS_RECOVERED,
    LATE_SUCCESS_UNRECOVERABLE
}

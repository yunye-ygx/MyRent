package cn.yy.myrent.common;

import cn.hutool.core.util.IdUtil;

public class GenerateOrder {
    public static String generateOrderNo(String prefix) {
        String orderNo=prefix+ IdUtil.getSnowflakeNextIdStr();
        return orderNo;
    }
}

package cn.yy.myrent.vo;

import lombok.Data;

@Data
public class AdminDashboardVO {
    private Long totalUsers;
    private Long totalHouses;
    private Long todayOrders;
    private Long todayRevenue; // 单位：分
}

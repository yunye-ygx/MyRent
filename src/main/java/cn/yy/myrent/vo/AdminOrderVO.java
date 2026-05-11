package cn.yy.myrent.vo;

import cn.yy.myrent.entity.Order;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrderVO extends Order {
    private String userPhone;
    private String userName;
    private String houseTitle;
}

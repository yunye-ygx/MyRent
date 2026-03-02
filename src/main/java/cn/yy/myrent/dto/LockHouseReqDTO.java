package cn.yy.myrent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class LockHouseReqDTO {

    @NotNull(message = "房源ID不能为空")
    private Long houseId;

    @NotNull(message = "版本号不能为空")
    private Integer version; // 面试考点：前端必须传当前房源的版本号，用于乐观锁
}

package cn.yy.myrent.controller;


import cn.yy.myrent.common.Result;
import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.service.IOrderService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 定金订单表 前端控制器
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
@RestController
@RequestMapping("/order")
public class OrderController {
    @Autowired
    private IOrderService orderService;

    @PostMapping("/createOrder")
    @Operation(summary = "创建订单", description = "创建订单接口")
    public Result createOrder(LockHouseReqDTO lockHouse,Long userId){
        orderService.createOrder(lockHouse,userId);
        return Result.success();
    }
}

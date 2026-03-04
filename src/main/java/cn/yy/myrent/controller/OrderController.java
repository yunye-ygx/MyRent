package cn.yy.myrent.controller;


import cn.yy.myrent.common.Result;
import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.service.IOrderService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    public ResponseEntity<Result> createOrder(@RequestBody LockHouseReqDTO lockHouse){
        try {
            orderService.createOrder(lockHouse);
            return ResponseEntity.ok(Result.success("订单创建成功,请尽快支付"));
        } catch (RuntimeException e) {
            // 业务失败提示前端
            String msg = e.getMessage() != null ? e.getMessage() : "房源已下架";
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(msg));
        } catch (Exception e) {
            // 兜底错误
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("系统繁忙，请稍后重试"));
        }
    }


}

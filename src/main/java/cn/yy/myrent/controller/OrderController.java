package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.service.IOrderService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
@Slf4j
public class OrderController {

    @Autowired
    private IOrderService orderService;

    @PostMapping("/createOrder")
    @Operation(summary = "创建订单", description = "下单并锁定房源")
    public ResponseEntity<Result<Void>> createOrder(@RequestBody LockHouseReqDTO lockHouse) {
        log.info("收到创建订单请求，houseId={}", lockHouse == null ? null : lockHouse.getHouseId());
        try {
            orderService.createOrder(lockHouse);
            log.info("创建订单成功，houseId={}", lockHouse == null ? null : lockHouse.getHouseId());
            return ResponseEntity.ok(Result.success("订单创建成功，请尽快支付", null));
        } catch (IllegalStateException e) {
            log.warn("创建订单失败：未登录或上下文缺失，houseId={}, message={}",
                    lockHouse == null ? null : lockHouse.getHouseId(),
                    e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.error(401, e.getMessage()));
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "房源已下架";
            log.warn("创建订单业务失败，houseId={}, message={}", lockHouse == null ? null : lockHouse.getHouseId(), msg);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(msg));
        } catch (Exception e) {
            log.error("创建订单系统异常，houseId={}", lockHouse == null ? null : lockHouse.getHouseId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("系统繁忙，请稍后重试"));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询订单")
    public Result<Order> getById(@PathVariable("id") Long id) {
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        return Result.success(order);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询订单")
    public Result<Page<Order>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        Page<Order> page = orderService.lambdaQuery()
                .orderByDesc(Order::getId)
                .page(new Page<>(safeCurrent, safeSize));
        return Result.success(page);
    }

    @PostMapping
    @Operation(summary = "新增订单")
    public Result<Long> create(@RequestBody Order order) {
        order.setId(null);
        boolean saved = orderService.save(order);
        if (!saved) {
            return Result.error("新增订单失败");
        }
        return Result.success("新增订单成功", order.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新订单")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody Order order) {
        order.setId(id);
        boolean updated = orderService.updateById(order);
        if (!updated) {
            return Result.error("更新订单失败或订单不存在");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除订单")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean removed = orderService.removeById(id);
        if (!removed) {
            return Result.error("删除订单失败或订单不存在");
        }
        return Result.success();
    }
}

package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.LockHouseReqDTO;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.vo.CreateOrderVO;
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

    @PostMapping("/create")
    @Operation(summary = "create order and return mock checkout info")
    public ResponseEntity<Result<CreateOrderVO>> createOrder(@RequestBody LockHouseReqDTO lockHouse) {
        log.info("create order request, houseId={}", lockHouse == null ? null : lockHouse.getHouseId());
        try {
            CreateOrderVO result = orderService.createOrder(lockHouse);
            return ResponseEntity.ok(Result.success("order created, please pay soon", result));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(e.getMessage()));
        } catch (Exception e) {
            log.error("create order failed, houseId={}", lockHouse == null ? null : lockHouse.getHouseId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("system busy, please retry later"));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "query order by id")
    public Result<Order> getById(@PathVariable("id") Long id) {
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("order not found");
        }
        return Result.success(order);
    }

    @GetMapping("/page")
    @Operation(summary = "page query orders")
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

    @GetMapping("/mine")
    @Operation(summary = "query current user orders")
    public Result<Page<Order>> mine(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }

        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        Page<Order> page = orderService.lambdaQuery()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId)
                .page(new Page<>(safeCurrent, safeSize));
        return Result.success(page);
    }

    @PostMapping
    @Operation(summary = "create order record")
    public Result<Long> create(@RequestBody Order order) {
        order.setId(null);
        boolean saved = orderService.save(order);
        if (!saved) {
            return Result.error("create order failed");
        }
        return Result.success("create order success", order.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "update order")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody Order order) {
        order.setId(id);
        boolean updated = orderService.updateById(order);
        if (!updated) {
            return Result.error("update order failed");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "delete order")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean removed = orderService.removeById(id);
        if (!removed) {
            return Result.error("delete order failed");
        }
        return Result.success();
    }
}

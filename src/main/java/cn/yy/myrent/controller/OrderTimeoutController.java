package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.entity.OrderTimeout;
import cn.yy.myrent.service.IOrderTimeoutService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/order-timeout")
public class OrderTimeoutController {

    @Autowired
    private IOrderTimeoutService orderTimeoutService;

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询超时任务")
    public Result<OrderTimeout> getById(@PathVariable("id") Long id) {
        OrderTimeout orderTimeout = orderTimeoutService.getById(id);
        if (orderTimeout == null) {
            return Result.error("超时任务不存在");
        }
        return Result.success(orderTimeout);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询超时任务")
    public Result<Page<OrderTimeout>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        Page<OrderTimeout> page = orderTimeoutService.lambdaQuery()
                .orderByDesc(OrderTimeout::getId)
                .page(new Page<>(safeCurrent, safeSize));
        return Result.success(page);
    }

    @PostMapping
    @Operation(summary = "新增超时任务")
    public Result<Long> create(@RequestBody OrderTimeout orderTimeout) {
        orderTimeout.setId(null);
        boolean saved = orderTimeoutService.save(orderTimeout);
        if (!saved) {
            return Result.error("新增超时任务失败");
        }
        return Result.success("新增超时任务成功", orderTimeout.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新超时任务")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody OrderTimeout orderTimeout) {
        orderTimeout.setId(id);
        boolean updated = orderTimeoutService.updateById(orderTimeout);
        if (!updated) {
            return Result.error("更新超时任务失败或记录不存在");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除超时任务")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean removed = orderTimeoutService.removeById(id);
        if (!removed) {
            return Result.error("删除超时任务失败或记录不存在");
        }
        return Result.success();
    }
}

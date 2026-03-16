package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.service.IPaymentService;
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
@RequestMapping("/payment")
public class PaymentController {

    @Autowired
    private IPaymentService paymentService;

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询支付记录")
    public Result<Payment> getById(@PathVariable("id") Long id) {
        Payment payment = paymentService.getById(id);
        if (payment == null) {
            return Result.error("支付记录不存在");
        }
        return Result.success(payment);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询支付记录")
    public Result<Page<Payment>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        Page<Payment> page = paymentService.lambdaQuery()
                .orderByDesc(Payment::getId)
                .page(new Page<>(safeCurrent, safeSize));
        return Result.success(page);
    }

    @PostMapping
    @Operation(summary = "新增支付记录")
    public Result<Long> create(@RequestBody Payment payment) {
        payment.setId(null);
        boolean saved = paymentService.save(payment);
        if (!saved) {
            return Result.error("新增支付记录失败");
        }
        return Result.success("新增支付记录成功", payment.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新支付记录")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody Payment payment) {
        payment.setId(id);
        boolean updated = paymentService.updateById(payment);
        if (!updated) {
            return Result.error("更新支付记录失败或记录不存在");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除支付记录")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean removed = paymentService.removeById(id);
        if (!removed) {
            return Result.error("删除支付记录失败或记录不存在");
        }
        return Result.success();
    }
}

package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.dto.MockPaymentCallbackReqDTO;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.service.IPaymentService;
import cn.yy.myrent.vo.MockCheckoutVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class PaymentController {

    @Autowired
    private IPaymentService paymentService;

    @GetMapping("/mock-checkout/{paymentNo}")
    public Result<MockCheckoutVO> mockCheckout(@PathVariable String paymentNo) {
        log.info("如果跳转到支付页面，修改支付状态为支付中");
        return Result.success(paymentService.getMockCheckout(paymentNo));
    }

    @PostMapping("/callback/mock")
    public Result<Void> mockCallback(@RequestBody MockPaymentCallbackReqDTO req) {
        paymentService.handleMockCallback(req);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "query payment by id")
    public Result<Payment> getById(@PathVariable("id") Long id) {
        Payment payment = paymentService.getById(id);
        if (payment == null) {
            return Result.error("payment not found");
        }
        return Result.success(payment);
    }

    @GetMapping("/page")
    @Operation(summary = "page query payments")
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
    @Operation(summary = "create payment record")
    public Result<Long> create(@RequestBody Payment payment) {
        payment.setId(null);
        boolean saved = paymentService.save(payment);
        if (!saved) {
            return Result.error("create payment failed");
        }
        return Result.success("create payment success", payment.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "update payment")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody Payment payment) {
        payment.setId(id);
        boolean updated = paymentService.updateById(payment);
        if (!updated) {
            return Result.error("update payment failed");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "delete payment")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean removed = paymentService.removeById(id);
        if (!removed) {
            return Result.error("delete payment failed");
        }
        return Result.success();
    }
}

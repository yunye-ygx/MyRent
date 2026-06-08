package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Payment;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.service.IPaymentService;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.vo.AdminDashboardVO;
import cn.yy.myrent.vo.AdminOrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private IUserService userService;
    @Autowired private IHouseService houseService;
    @Autowired private IHouseCommandService houseCommandService;
    @Autowired private IOrderService orderService;
    @Autowired private IPaymentService paymentService;

    @GetMapping("/dashboard")
    @Operation(summary = "数据概览")
    public Result<AdminDashboardVO> dashboard() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setTotalUsers(userService.count());
        vo.setTotalHouses(houseService.count());
        vo.setTodayOrders(orderService.lambdaQuery().ge(Order::getCreateTime, todayStart).count());
        List<Payment> paid = paymentService.lambdaQuery()
                .isNotNull(Payment::getPaidTime).ge(Payment::getPaidTime, todayStart).list();
        vo.setTodayRevenue(paid.stream().mapToLong(p -> p.getPayAmount() != null ? p.getPayAmount() : 0).sum());
        return Result.success(vo);
    }

    @GetMapping("/users")
    @Operation(summary = "分页查询用户")
    public Result<Page<User>> listUsers(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword) {
        var query = userService.lambdaQuery().orderByDesc(User::getId);
        if (StringUtils.hasText(keyword))
            query.and(q -> q.like(User::getPhone, keyword).or().like(User::getName, keyword));
        Page<User> result = query.page(new Page<>(page, size));
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(result);
    }

    @PutMapping("/users/{id}/ban")
    @Operation(summary = "封禁用户")
    public Result<Void> banUser(@PathVariable Long id) {
        return userService.lambdaUpdate().eq(User::getId, id).set(User::getBanned, 1).update()
                ? Result.success() : Result.error("用户不存在");
    }

    @PutMapping("/users/{id}/unban")
    @Operation(summary = "解封用户")
    public Result<Void> unbanUser(@PathVariable Long id) {
        return userService.lambdaUpdate().eq(User::getId, id).set(User::getBanned, 0).update()
                ? Result.success() : Result.error("用户不存在");
    }

    @GetMapping("/houses")
    @Operation(summary = "分页查询房源")
    public Result<Page<House>> listHouses(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String keyword) {
        var query = houseService.lambdaQuery().orderByDesc(House::getId);
        if (auditStatus != null) query.eq(House::getAuditStatus, auditStatus);
        if (StringUtils.hasText(city)) query.eq(House::getCity, city);
        if (StringUtils.hasText(keyword)) query.like(House::getTitle, keyword);
        return Result.success(query.page(new Page<>(page, size)));
    }

    @PostMapping("/houses")
    @Operation(summary = "发布房源")
    public Result<Long> createHouse(@RequestBody House house) {
        house.setId(null);
        house.setStatus(1);
        house.setVersion(0);
        house.setAuditStatus(1);
        house.setCreateTime(LocalDateTime.now());
        house.setTotalCost(null);
        if (!houseCommandService.createHouseWithSync(house)) {
            return Result.error("发布房源失败");
        }
        return Result.success("发布成功", house.getId());
    }

    @PutMapping("/houses/{id}")
    @Operation(summary = "编辑房源")
    public Result<Void> updateHouse(@PathVariable Long id, @RequestBody House house) {
        house.setId(id);
        house.setTotalCost(null);
        house.setStatus(null);
        house.setVersion(null);
        return houseCommandService.updateHouseWithSync(id, house) ? Result.success() : Result.error("房源不存在");
    }

    @DeleteMapping("/houses/{id}")
    @Operation(summary = "删除房源")
    public Result<Void> deleteHouse(@PathVariable Long id) {
        return houseCommandService.deleteHouseWithSync(id) ? Result.success() : Result.error("房源不存在");
    }

    @PutMapping("/houses/{id}/approve")
    @Operation(summary = "审核通过")
    public Result<Void> approveHouse(@PathVariable Long id) {
        return houseService.lambdaUpdate().eq(House::getId, id).set(House::getAuditStatus, 1).update()
                ? Result.success() : Result.error("房源不存在");
    }

    @PutMapping("/houses/{id}/reject")
    @Operation(summary = "审核拒绝")
    public Result<Void> rejectHouse(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return houseService.lambdaUpdate().eq(House::getId, id).set(House::getAuditStatus, 2).update()
                ? Result.success() : Result.error("房源不存在");
    }

    @GetMapping("/orders")
    @Operation(summary = "分页查询订单")
    public Result<Page<AdminOrderVO>> listOrders(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer status) {
        var query = orderService.lambdaQuery().orderByDesc(Order::getCreateTime);
        if (status != null) query.eq(Order::getStatus, status);
        Page<Order> orderPage = query.page(new Page<>(page, size));
        Page<AdminOrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(order -> {
            AdminOrderVO vo = new AdminOrderVO();
            vo.setId(order.getId()); vo.setOrderNo(order.getOrderNo());
            vo.setUserId(order.getUserId()); vo.setHouseId(order.getHouseId());
            vo.setAmount(order.getAmount()); vo.setStatus(order.getStatus());
            vo.setExpireTime(order.getExpireTime()); vo.setPaidTime(order.getPaidTime());
            vo.setCreateTime(order.getCreateTime());
            User user = userService.getById(order.getUserId());
            if (user != null) { vo.setUserPhone(user.getPhone()); vo.setUserName(user.getName()); }
            House house = houseService.getById(order.getHouseId());
            if (house != null) vo.setHouseTitle(house.getTitle());
            return vo;
        }).toList());
        return Result.success(voPage);
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "订单详情")
    public Result<AdminOrderVO> getOrder(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order == null) return Result.error("订单不存在");
        AdminOrderVO vo = new AdminOrderVO();
        vo.setId(order.getId()); vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId()); vo.setHouseId(order.getHouseId());
        vo.setAmount(order.getAmount()); vo.setStatus(order.getStatus());
        vo.setExpireTime(order.getExpireTime()); vo.setPaidTime(order.getPaidTime());
        vo.setCreateTime(order.getCreateTime());
        User user = userService.getById(order.getUserId());
        if (user != null) { vo.setUserPhone(user.getPhone()); vo.setUserName(user.getName()); }
        House house = houseService.getById(order.getHouseId());
        if (house != null) vo.setHouseTitle(house.getTitle());
        return Result.success(vo);
    }

    @GetMapping("/payments")
    @Operation(summary = "分页查询支付记录")
    public Result<Page<Payment>> listPayments(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer status) {
        var query = paymentService.lambdaQuery().orderByDesc(Payment::getCreateTime);
        if (status != null) query.eq(Payment::getStatus, status);
        return Result.success(query.page(new Page<>(page, size)));
    }
}

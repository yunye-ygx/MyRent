package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.HouseAlertCreateReqDTO;
import cn.yy.myrent.entity.HouseAlert;
import cn.yy.myrent.service.IHouseAlertService;
import cn.yy.myrent.vo.HouseAlertVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/house-alert")
@RequiredArgsConstructor
public class HouseAlertController {

    private final IHouseAlertService houseAlertService;

    @GetMapping("/mine")
    public Result<List<HouseAlertVO>> listMine() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(houseAlertService.listMine(userId).stream().map(this::toVO).toList());
    }

    @PostMapping
    public Result<HouseAlertVO> create(@Valid @RequestBody HouseAlertCreateReqDTO reqDTO) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        return Result.success(toVO(houseAlertService.createAlert(reqDTO, userId)));
    }

    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable("id") Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "please login first");
        }
        houseAlertService.disableAlert(id, userId);
        return Result.success();
    }

    private HouseAlertVO toVO(HouseAlert alert) {
        HouseAlertVO vo = new HouseAlertVO();
        vo.setId(alert.getId());
        vo.setCity(alert.getCity());
        vo.setRegion(alert.getRegion());
        vo.setMaxPrice(alert.getMaxPrice());
        vo.setRentType(alert.getRentType());
        vo.setStatus(alert.getStatus());
        vo.setCreateTime(alert.getCreateTime());
        vo.setUpdateTime(alert.getUpdateTime());
        return vo;
    }
}

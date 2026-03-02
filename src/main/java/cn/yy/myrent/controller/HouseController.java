package cn.yy.myrent.controller;


import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.IHouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 房源信息表 前端控制器
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
@RestController
@RequestMapping("/house")
@Tag(name = "房源管理")
public class HouseController {
    @Autowired
    private IHouseService houseService;

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询房源", description = "通过主键ID获取房源详情") // ← 方法描述
    public House getById(
            @Parameter(description = "房源ID", required = true) // ← 参数描述
            @PathVariable Long id) {
        return houseService.getById(id);
    }

}

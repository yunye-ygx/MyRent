package cn.yy.myrent.controller;


import cn.yy.myrent.common.Result;

import cn.yy.myrent.dto.SearchHouseReqDTO;

import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.vo.HouseVO;
import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping
    @Operation(summary = "根据坐标查询房源", description = "通过坐标获取房源详情") // ← 方法描述
    public Result searchHouse(@RequestBody SearchHouseReqDTO reqDTO){

        List<HouseVO> houseVo =houseService.searchNearbyHouse(reqDTO);
        return Result.success(houseVo);
    }


}

package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.vo.HouseVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

@RestController
@RequestMapping("/house")
@Tag(name = "房源管理")
public class HouseController {

    @Autowired
    private IHouseService houseService;

    @PostMapping("/nearby")
    @Operation(summary = "附近房源搜索", description = "优先ES，失败自动降级DB")
    public Result<List<HouseVO>> searchHouse(@RequestBody SearchHouseReqDTO reqDTO) {
        List<HouseVO> houseVo = houseService.searchNearbyHouse(reqDTO);
        return Result.success(houseVo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询房源")
    public Result<House> getById(@PathVariable("id") Long id) {
        House house = houseService.getById(id);
        if (house == null) {
            return Result.error("房源不存在");
        }
        return Result.success(house);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询房源")
    public Result<Page<House>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        Page<House> page = houseService.lambdaQuery()
                .orderByDesc(House::getId)
                .page(new Page<>(safeCurrent, safeSize));
        return Result.success(page);
    }

    @PostMapping
    @Operation(summary = "新增房源")
    public Result<Long> create(@RequestBody House house) {
        house.setId(null);
        boolean saved = houseService.save(house);
        if (!saved) {
            return Result.error("新增房源失败");
        }
        return Result.success("新增房源成功", house.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新房源")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody House house) {
        house.setId(id);
        boolean updated = houseService.updateById(house);
        if (!updated) {
            return Result.error("更新房源失败或房源不存在");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除房源")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean removed = houseService.removeById(id);
        if (!removed) {
            return Result.error("删除房源失败或房源不存在");
        }
        return Result.success();
    }
}

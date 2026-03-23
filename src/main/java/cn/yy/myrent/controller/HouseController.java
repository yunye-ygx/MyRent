package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.sync.house.service.HouseEsSyncService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/house")
@Tag(name = "房源管理")
@Slf4j
@RequiredArgsConstructor
public class HouseController {

    private final IHouseService houseService;
    private final IHouseCommandService houseCommandService;
    private final HouseEsSyncService houseEsSyncService;

    @PostMapping("/nearby")
    @Operation(summary = "附近房源搜索", description = "优先走 ES，失败后自动降级")
    public Result<HouseSearchResultVO> searchHouse(@RequestBody SearchHouseReqDTO reqDTO) {
        try {
            return Result.success(houseService.searchNearbyHouse(reqDTO));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage() == null ? "附近房源搜索失败" : e.getMessage());
        }
    }

    @GetMapping("/hot")
    @Operation(summary = "热门房源", description = "返回按热度分排序的可租房源")
    public Result<HouseSearchResultVO> hotHouses(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return Result.success(houseService.hotHouses(page, size));
    }

    @PostMapping("/smart-guide")
    @Operation(summary = "智能找房引导", description = "先做 ES 预筛选，再由 DB 完成最终过滤和排序")
    public Result<SmartGuideResultVO> smartGuide(@Valid @RequestBody SmartGuideReqDTO reqDTO) {
        return Result.success(houseService.smartGuide(reqDTO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查询房源")
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

    @PostMapping("/es/rebuild-all")
    @Operation(summary = "全量重建房源 ES 文档", description = "从 MySQL 全量扫描 house 表并重新写入 Elasticsearch")
    public Result<Integer> rebuildHouseEs() {
        int rebuildCount = houseEsSyncService.rebuildAllFromDb();
        return Result.success("房源 ES 全量重建完成", rebuildCount);
    }

    @PostMapping
    @Operation(summary = "新增房源")
    public Result<Long> create(@RequestBody House house) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            log.warn("create house failed: unauthenticated");
            return Result.error(401, "请先登录");
        }

        house.setId(null);
        house.setPublisherUserId(currentUserId);
        boolean saved = houseCommandService.createHouseWithSync(house);
        if (!saved) {
            log.error("create house failed, userId={}", currentUserId);
            return Result.error("新增房源失败");
        }
        return Result.success("新增房源成功", house.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新房源")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody House house) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "请先登录");
        }

        House dbHouse = houseService.getById(id);
        if (dbHouse == null) {
            return Result.error("房源不存在");
        }
        if (!currentUserId.equals(dbHouse.getPublisherUserId())) {
            return Result.error(403, "仅发布人可修改该房源");
        }

        house.setId(id);
        house.setPublisherUserId(dbHouse.getPublisherUserId());
        boolean updated = houseCommandService.updateHouseWithSync(id, house);
        if (!updated) {
            return Result.error("更新房源失败或房源不存在");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除房源")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "请先登录");
        }

        House dbHouse = houseService.getById(id);
        if (dbHouse == null) {
            return Result.error("房源不存在");
        }
        if (!currentUserId.equals(dbHouse.getPublisherUserId())) {
            return Result.error(403, "仅发布人可删除该房源");
        }

        boolean removed = houseCommandService.deleteHouseWithSync(id);
        if (!removed) {
            return Result.error("删除房源失败或房源不存在");
        }
        return Result.success();
    }
}

package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/house")
@Tag(name = "房源管理")
@Slf4j
public class HouseController {

    @Autowired
    private IHouseService houseService;

    @PostMapping("/nearby")
    @Operation(summary = "附近房源搜索", description = "优先ES，失败自动降级DB")
    public Result<HouseSearchResultVO> searchHouse(@RequestBody SearchHouseReqDTO reqDTO) {
        HouseSearchResultVO result = houseService.searchNearbyHouse(reqDTO);
        return Result.success(result);
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
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            log.warn("新增房源失败：未登录");
            return Result.error(401, "请先登录");
        }

        log.info("新增房源请求，publisherUserId={}, title={}", currentUserId, house == null ? null : house.getTitle());

        house.setId(null);
        house.setPublisherUserId(currentUserId);
        boolean saved = houseService.save(house);
        if (!saved) {
            log.error("新增房源失败，publisherUserId={}, title={}", currentUserId, house.getTitle());
            return Result.error("新增房源失败");
        }
        log.info("新增房源成功，houseId={}, publisherUserId={}", house.getId(), currentUserId);
        return Result.success("新增房源成功", house.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新房源")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody House house) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            log.warn("更新房源失败：未登录，houseId={}", id);
            return Result.error(401, "请先登录");
        }

        House dbHouse = houseService.getById(id);
        if (dbHouse == null) {
            log.warn("更新房源失败：房源不存在，houseId={}, userId={}", id, currentUserId);
            return Result.error("房源不存在");
        }

        if (!currentUserId.equals(dbHouse.getPublisherUserId())) {
            log.warn("更新房源失败：非发布人操作，houseId={}, publisherUserId={}, currentUserId={}",
                    id,
                    dbHouse.getPublisherUserId(),
                    currentUserId);
            return Result.error(403, "仅发布人可修改该房源");
        }

        house.setId(id);
        house.setPublisherUserId(dbHouse.getPublisherUserId());
        boolean updated = houseService.updateById(house);
        if (!updated) {
            log.error("更新房源失败，houseId={}, currentUserId={}", id, currentUserId);
            return Result.error("更新房源失败或房源不存在");
        }
        log.info("更新房源成功，houseId={}, currentUserId={}", id, currentUserId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除房源")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            log.warn("删除房源失败：未登录，houseId={}", id);
            return Result.error(401, "请先登录");
        }

        House dbHouse = houseService.getById(id);
        if (dbHouse == null) {
            log.warn("删除房源失败：房源不存在，houseId={}, userId={}", id, currentUserId);
            return Result.error("房源不存在");
        }

        if (!currentUserId.equals(dbHouse.getPublisherUserId())) {
            log.warn("删除房源失败：非发布人操作，houseId={}, publisherUserId={}, currentUserId={}",
                    id,
                    dbHouse.getPublisherUserId(),
                    currentUserId);
            return Result.error(403, "仅发布人可删除该房源");
        }

        boolean removed = houseService.removeById(id);
        if (!removed) {
            log.error("删除房源失败，houseId={}, currentUserId={}", id, currentUserId);
            return Result.error("删除房源失败或房源不存在");
        }
        log.info("删除房源成功，houseId={}, currentUserId={}", id, currentUserId);
        return Result.success();
    }
}

package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.HouseFavorite;
import cn.yy.myrent.service.IHouseFavoriteService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.vo.HouseFavoriteStatusVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/house-favorite")
@RequiredArgsConstructor
public class HouseFavoriteController {

    private final IHouseFavoriteService houseFavoriteService;
    private final IHouseService houseService;

    @PostMapping("/{houseId}")
    public Result<HouseFavoriteStatusVO> favorite(@PathVariable("houseId") Long houseId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        try {
            return Result.success(houseFavoriteService.favorite(houseId, userId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage() == null ? "收藏失败" : e.getMessage());
        }
    }

    @DeleteMapping("/{houseId}")
    public Result<HouseFavoriteStatusVO> unfavorite(@PathVariable("houseId") Long houseId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        try {
            return Result.success(houseFavoriteService.unfavorite(houseId, userId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage() == null ? "取消收藏失败" : e.getMessage());
        }
    }

    @GetMapping("/{houseId}/status")
    public Result<HouseFavoriteStatusVO> status(@PathVariable("houseId") Long houseId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        try {
            return Result.success(houseFavoriteService.getFavoriteStatus(houseId, userId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage() == null ? "查询收藏状态失败" : e.getMessage());
        }
    }

    @GetMapping("/mine")
    public Result<Page<House>> mine(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        Page<HouseFavorite> favoritePage = houseFavoriteService.lambdaQuery()
                .eq(HouseFavorite::getUserId, userId)
                .eq(HouseFavorite::getStatus, 1)
                .orderByDesc(HouseFavorite::getFavoriteTime)
                .orderByDesc(HouseFavorite::getId)
                .page(new Page<>(safeCurrent, safeSize));

        Page<House> housePage = new Page<>(safeCurrent, safeSize, favoritePage.getTotal());
        List<HouseFavorite> relations = favoritePage.getRecords();
        if (relations == null || relations.isEmpty()) {
            housePage.setRecords(Collections.emptyList());
            return Result.success(housePage);
        }

        List<Long> houseIds = relations.stream()
                .map(HouseFavorite::getHouseId)
                .collect(Collectors.toList());
        Map<Long, House> houseMap = houseService.listByIds(houseIds).stream()
                .collect(Collectors.toMap(House::getId, house -> house));
        List<House> houses = houseIds.stream()
                .map(houseMap::get)
                .filter(house -> house != null)
                .collect(Collectors.toList());
        housePage.setRecords(houses);
        return Result.success(housePage);
    }
}

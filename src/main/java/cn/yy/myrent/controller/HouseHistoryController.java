package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.service.IHouseHistoryService;
import cn.yy.myrent.vo.HouseHistoryCalendarVO;
import cn.yy.myrent.vo.HouseHistoryItemVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/house-history")
@Tag(name = "房源浏览历史")
@RequiredArgsConstructor
public class HouseHistoryController {

    private final IHouseHistoryService houseHistoryService;

    @GetMapping("/calendar")
    @Operation(summary = "查询当月可点击浏览日期")
    public Result<HouseHistoryCalendarVO> calendar(@RequestParam Integer year,
                                                   @RequestParam Integer month) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(houseHistoryService.getCalendar(userId, year, month));
    }

    @GetMapping("/mine")
    @Operation(summary = "分页查询我的浏览历史")
    public Result<Page<HouseHistoryItemVO>> mine(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @RequestParam(value = "browseDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate browseDate) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(houseHistoryService.pageMine(userId, current, size, browseDate));
    }
}

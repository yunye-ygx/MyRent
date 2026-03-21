package cn.yy.myrent.sync.house;

import cn.yy.myrent.sync.house.service.HouseEsSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@Slf4j
public class HouseDailyEsConsistencyTask {

    private static final ZoneId TASK_ZONE_ID = ZoneId.of("Asia/Shanghai");

    @Autowired
    private HouseEsSyncService houseEsSyncService;

    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void compensateTodayHouseData() {
        LocalDate today = LocalDate.now(TASK_ZONE_ID);
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime startTime = yesterday.atStartOfDay();
        LocalDateTime endTime = today.atStartOfDay();
        log.info("start daily house DB/ES consistency task for yesterday, startTime={}, endTime={}", startTime, endTime);
        houseEsSyncService.compensateByCreateTimeRange(startTime, endTime);
    }
}

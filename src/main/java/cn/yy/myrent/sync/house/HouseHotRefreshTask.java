package cn.yy.myrent.sync.house;

import cn.yy.myrent.service.hot.HouseHotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HouseHotRefreshTask {

    private final HouseHotService houseHotService;

    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Shanghai")
    public void refreshHotRanking() {
        houseHotService.rebuildHotRanking();
        log.info("daily hot-house refresh completed");
    }
}

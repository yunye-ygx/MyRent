package cn.yy.myrent.service;

import cn.yy.myrent.entity.HouseHistory;
import cn.yy.myrent.vo.HouseHistoryCalendarVO;
import cn.yy.myrent.vo.HouseHistoryItemVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;

public interface IHouseHistoryService extends IService<HouseHistory> {

    void recordBrowse(Long houseId, Long userId);

    HouseHistoryCalendarVO getCalendar(Long userId, Integer year, Integer month);

    Page<HouseHistoryItemVO> pageMine(Long userId, long current, long size, LocalDate browseDate);
}

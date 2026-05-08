package cn.yy.myrent.sync.house.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.hot.HouseHotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseHotSyncServiceImplTest {

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private HouseHotService houseHotService;

    @InjectMocks
    private HouseHotSyncServiceImpl service;

    @Test
    void syncHouseChangeShouldRemoveUnavailableHouseFromHotRankings() {
        when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setStatus(2).setCity("shanghai"));

        service.syncHouseChange(7L);

        verify(houseHotService).removeHouseFromAllHotRankings(7L);
    }

    @Test
    void syncHouseChangeShouldRemoveDeletedHouseFromHotRankings() {
        when(houseMapper.selectById(7L)).thenReturn(null);

        service.syncHouseChange(7L);

        verify(houseHotService).removeHouseFromAllHotRankings(7L);
    }

    @Test
    void syncHouseChangeShouldKeepAvailableHouseUntilRebuild() {
        when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setStatus(1).setCity("shanghai"));

        service.syncHouseChange(7L);

        verify(houseHotService, never()).removeHouseFromAllHotRankings(7L);
    }
}

package cn.yy.myrent.service.impl;

import cn.yy.myrent.dto.HouseListFilterReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.service.smartguide.SmartGuideRecommendationService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseServiceImplListFilterTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SmartGuideRecommendationService smartGuideRecommendationService;

    @Mock
    private HouseHotService houseHotService;

    @Mock
    private LocationResolveService locationResolveService;

    @Mock
    private IUserService userService;

    @Mock
    private HouseMapper houseMapper;

    @InjectMocks
    private HouseServiceImpl houseService;

    @Test
    void convertHouseToVoShouldCarryFeatureFlags() {
        House house = new House();
        house.setId(1L);
        house.setTitle("\u59d1\u82cf\u533a\u7cbe\u88c5\u4e00\u5c45");
        house.setCity("\u82cf\u5dde");
        house.setRegion("\u59d1\u82cf");
        house.setNearSubway(1);
        house.setPrivateBathroom(1);
        house.setHasBalcony(0);
        house.setCivilWaterElectric(1);
        house.setPrice(280000);
        house.setDepositAmount(100000);
        house.setStatus(1);

        HouseVO vo = ReflectionTestUtils.invokeMethod(houseService, "convertHouseToVo", house);

        assertEquals("\u82cf\u5dde", vo.getCity());
        assertEquals("\u59d1\u82cf", vo.getRegion());
        assertEquals(Boolean.TRUE, vo.getNearSubway());
        assertEquals(Boolean.TRUE, vo.getPrivateBathroom());
        assertEquals(Boolean.FALSE, vo.getHasBalcony());
        assertEquals(Boolean.TRUE, vo.getCivilWaterElectric());
    }

    @Test
    void filterListShouldPassFeatureFlagsToMapper() {
        ReflectionTestUtils.setField(houseService, "baseMapper", houseMapper);

        House house = new House();
        house.setId(2L);
        house.setTitle("\u59d1\u82cf\u533a\u54c1\u8d28\u4e00\u5c45");
        house.setCity("\u82cf\u5dde");
        house.setRegion("\u59d1\u82cf");
        house.setNearSubway(1);
        house.setPrivateBathroom(1);
        house.setHasBalcony(1);
        house.setCivilWaterElectric(1);
        house.setPrice(320000);
        house.setDepositAmount(100000);
        house.setStatus(1);

        when(houseMapper.selectListFilterPage(
                eq("\u82cf\u5dde"),
                eq("\u59d1\u82cf"),
                eq(1),
                eq(150000),
                eq(350000),
                eq(Boolean.TRUE),
                eq(Boolean.TRUE),
                eq(Boolean.TRUE),
                eq(Boolean.TRUE),
                eq(0),
                eq(8)
        )).thenReturn(java.util.List.of(house));

        HouseListFilterReqDTO reqDTO = new HouseListFilterReqDTO();
        reqDTO.setCity("\u82cf\u5dde");
        reqDTO.setRegion("\u59d1\u82cf");
        reqDTO.setRentType(1);
        reqDTO.setMinPriceYuan(1500);
        reqDTO.setMaxPriceYuan(3500);
        reqDTO.setNearSubway(true);
        reqDTO.setPrivateBathroom(true);
        reqDTO.setHasBalcony(true);
        reqDTO.setCivilWaterElectric(true);
        reqDTO.setPage(1);
        reqDTO.setSize(8);

        HouseSearchResultVO result = houseService.filterList(reqDTO);

        assertEquals(1, result.getHouses().size());
        assertEquals("\u82cf\u5dde", result.getHouses().get(0).getCity());
        assertEquals("\u59d1\u82cf", result.getHouses().get(0).getRegion());
        assertEquals(Boolean.TRUE, result.getHouses().get(0).getNearSubway());
        assertEquals("DB_FILTER", result.getFallbackSource());
        verify(houseMapper).selectListFilterPage(
                "\u82cf\u5dde",
                "\u59d1\u82cf",
                1,
                150000,
                350000,
                Boolean.TRUE,
                Boolean.TRUE,
                Boolean.TRUE,
                Boolean.TRUE,
                0,
                8
        );
    }
}

package cn.yy.myrent.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.HouseListFilterReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.service.smartguide.SmartGuideRecommendationService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseVO;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    void filterListShouldQueryEsWithFeatureFlagsAndMapFields() {
        HouseDoc doc = new HouseDoc();
        doc.setId(2L);
        doc.setPublisherUserId(1001L);
        doc.setTitle("\u59d1\u82cf\u533a\u54c1\u8d28\u4e00\u5c45");
        doc.setCity("\u82cf\u5dde");
        doc.setRegion("\u59d1\u82cf");
        doc.setNearSubway(true);
        doc.setPrivateBathroom(true);
        doc.setHasBalcony(true);
        doc.setCivilWaterElectric(true);
        doc.setPrice(320000);
        doc.setDepositAmount(100000);
        doc.setStatus(1);

        @SuppressWarnings("unchecked")
        SearchHit<HouseDoc> hit = (SearchHit<HouseDoc>) mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        @SuppressWarnings("unchecked")
        SearchHits<HouseDoc> hits = (SearchHits<HouseDoc>) mock(SearchHits.class);
        when(hits.iterator()).thenReturn(java.util.List.of(hit).iterator());
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class))).thenReturn(hits);

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
        assertEquals("ES_FILTER", result.getFallbackSource());

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(HouseDoc.class));

        NativeQuery nativeQuery = queryCaptor.getValue();
        assertNotNull(nativeQuery);

        Pageable pageable = nativeQuery.getPageable();
        assertNotNull(pageable);
        assertEquals(0, pageable.getPageNumber());
        assertEquals(8, pageable.getPageSize());

        Query esQuery = nativeQuery.getQuery();
        assertTrue(esQuery.isBool());
        java.util.List<Query> filters = esQuery.bool().filter();
        assertTrue(filters.stream().anyMatch(q -> q.isTerm() && "status".equals(q.term().field())));
        assertTrue(filters.stream().anyMatch(q -> q.isTerm() && "city".equals(q.term().field())));
        assertTrue(filters.stream().anyMatch(q -> q.isTerm() && "region".equals(q.term().field())));
        assertTrue(filters.stream().anyMatch(q -> q.isTerm() && "rentType".equals(q.term().field())));
        assertTrue(filters.stream().anyMatch(q -> q.isTerm() && "nearSubway".equals(q.term().field())));
        assertTrue(filters.stream().anyMatch(q -> q.isTerm() && "privateBathroom".equals(q.term().field())));
        assertTrue(filters.stream().anyMatch(q -> q.isTerm() && "hasBalcony".equals(q.term().field())));
        assertTrue(filters.stream().anyMatch(q -> q.isTerm() && "civilWaterElectric".equals(q.term().field())));
    }

    @Test
    void filterListShouldFallbackToDbWhenEsFails() {
        ReflectionTestUtils.setField(houseService, "baseMapper", houseMapper);

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class)))
                .thenThrow(new RuntimeException("ES down"));

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
        assertEquals("DB_FILTER", result.getFallbackSource());
        assertEquals(Boolean.TRUE, result.getEsDown());
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

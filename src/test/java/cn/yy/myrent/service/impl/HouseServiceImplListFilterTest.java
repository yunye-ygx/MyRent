package cn.yy.myrent.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.HouseListFilterReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.discovery.HouseRankingService;
import cn.yy.myrent.service.discovery.HouseRankingServiceImpl;
import cn.yy.myrent.service.discovery.HouseRecallCandidate;
import cn.yy.myrent.service.discovery.HouseRecallEvidence;
import cn.yy.myrent.service.discovery.HouseRecallMatchTier;
import cn.yy.myrent.service.discovery.HouseRecallProfile;
import cn.yy.myrent.service.discovery.HouseRecallQuery;
import cn.yy.myrent.service.discovery.HouseRecallResult;
import cn.yy.myrent.service.discovery.HouseRecallService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.service.smartguide.SmartGuideRecommendationService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
    private HouseRecallService houseRecallService;

    @Mock
    private HouseMapper houseMapper;

    private final HouseRankingService houseRankingService = new HouseRankingServiceImpl();

    @Test
    void convertHouseToVoShouldCarryFeatureFlags() {
        HouseServiceImpl houseService = newHouseService();
        House house = new House();
        house.setId(1L);
        house.setTitle("姑苏区精装一居");
        house.setCity("苏州");
        house.setRegion("姑苏");
        house.setNearSubway(1);
        house.setPrivateBathroom(1);
        house.setHasBalcony(0);
        house.setCivilWaterElectric(1);
        house.setSupportStudentDepositFree(1);
        house.setPrice(280000);
        house.setDepositAmount(100000);
        house.setStatus(1);

        HouseVO vo = ReflectionTestUtils.invokeMethod(houseService, "convertHouseToVo", house);

        assertEquals("苏州", vo.getCity());
        assertEquals("姑苏", vo.getRegion());
        assertEquals(Boolean.TRUE, vo.getNearSubway());
        assertEquals(Boolean.TRUE, vo.getPrivateBathroom());
        assertEquals(Boolean.FALSE, vo.getHasBalcony());
        assertEquals(Boolean.TRUE, vo.getCivilWaterElectric());
        assertEquals(Boolean.TRUE, vo.getSupportStudentDepositFree());
    }

    @Test
    void filterListShouldRankFullCandidateSetBeforePagingAndKeepDbCountAsAuthoritativeTotal() {
        HouseServiceImpl houseService = newHouseService();
        ReflectionTestUtils.setField(houseService, "baseMapper", houseMapper);
        when(houseRecallService.recall(any(HouseRecallQuery.class))).thenReturn(new HouseRecallResult(
                List.of(
                        new HouseRecallCandidate(buildHouse(18L, "苏州", "姑苏", 3200, LocalDateTime.of(2026, 4, 10, 10, 0)),
                                HouseRecallMatchTier.FILTER_ONLY,
                                HouseRecallEvidence.builder()
                                        .exactConstraintMatched(true)
                                        .nearSubwayMatched(true)
                                        .supportStudentDepositFreeMatched(true)
                                        .build()),
                        new HouseRecallCandidate(buildHouse(19L, "苏州", "姑苏", 3000, LocalDateTime.of(2026, 4, 12, 10, 0)),
                                HouseRecallMatchTier.FILTER_ONLY,
                                HouseRecallEvidence.builder()
                                        .exactConstraintMatched(true)
                                        .nearSubwayMatched(true)
                                        .privateBathroomMatched(true)
                                        .hasBalconyMatched(true)
                                        .civilWaterElectricMatched(true)
                                        .supportStudentDepositFreeMatched(true)
                                        .build())
                ),
                true,
                false
        ));
        when(houseMapper.selectCount(any())).thenReturn(1205L);

        HouseListFilterReqDTO reqDTO = buildFilterReq();
        reqDTO.setPage(1);
        reqDTO.setSize(1);

        HouseSearchResultVO result = houseService.filterList(reqDTO);

        assertEquals(1, result.getHouses().size());
        assertEquals(1205L, result.getTotal());
        assertEquals(19L, result.getHouses().get(0).getId());
        assertEquals("苏州", result.getHouses().get(0).getCity());
        assertEquals("姑苏", result.getHouses().get(0).getRegion());
        assertEquals(Boolean.TRUE, result.getHouses().get(0).getNearSubway());
        assertEquals(Boolean.TRUE, result.getHouses().get(0).getSupportStudentDepositFree());
        assertEquals(
                List.of("\u8fd1\u5730\u94c1\u6761\u4ef6\u547d\u4e2d", "\u72ec\u7acb\u536b\u6d74\u6761\u4ef6\u547d\u4e2d"),
                result.getHouses().get(0).getSearchReasons()
        );
        assertEquals(
                List.of("NEAR_SUBWAY_MATCH", "PRIVATE_BATHROOM_MATCH", "HAS_BALCONY_MATCH",
                        "CIVIL_WATER_ELECTRIC_MATCH", "SUPPORT_STUDENT_DEPOSIT_FREE_MATCH"),
                result.getHouses().get(0).getSearchReasonCodes()
        );
        assertEquals("ES_FILTER", result.getFallbackSource());
        assertEquals(Boolean.FALSE, result.getEsDown());

        ArgumentCaptor<HouseRecallQuery> queryCaptor = ArgumentCaptor.forClass(HouseRecallQuery.class);
        verify(houseRecallService).recall(queryCaptor.capture());
        assertEquals("苏州", queryCaptor.getValue().city());
        assertEquals("姑苏", queryCaptor.getValue().region());
        assertEquals(1, queryCaptor.getValue().rentType());
        assertEquals(1500, queryCaptor.getValue().minPriceYuan());
        assertEquals(3500, queryCaptor.getValue().maxPriceYuan());
        assertEquals(1, queryCaptor.getValue().page());
        assertEquals(1, queryCaptor.getValue().size());
        assertEquals(HouseRecallProfile.LIST_FILTER, queryCaptor.getValue().recallProfile());
    }

    @Test
    void filterListShouldKeepDbFallbackFieldsWhenRecallDegraded() {
        HouseServiceImpl houseService = newHouseService();
        ReflectionTestUtils.setField(houseService, "baseMapper", houseMapper);
        when(houseRecallService.recall(any(HouseRecallQuery.class))).thenReturn(new HouseRecallResult(
                List.of(new HouseRecallCandidate(
                        buildHouse(2L, "苏州", "姑苏", 3200, LocalDateTime.of(2026, 4, 10, 10, 0)),
                        HouseRecallMatchTier.FILTER_ONLY,
                        HouseRecallEvidence.builder().exactConstraintMatched(true).build()
                )),
                false,
                true
        ));
        when(houseMapper.selectCount(any())).thenReturn(33L);

        HouseSearchResultVO result = houseService.filterList(buildFilterReq());

        assertEquals(1, result.getHouses().size());
        assertEquals(33L, result.getTotal());
        assertEquals("DB_FILTER", result.getFallbackSource());
        assertEquals(Boolean.TRUE, result.getEsDown());
    }

    @Test
    void filterRecallEsAndDbCandidatesShouldPreserveSameRankingRelevantFields() {
        HouseServiceImpl houseService = newHouseService();
        House dbHouse = buildHouse(30L, "苏州", "姑苏", 2800, LocalDateTime.of(2026, 4, 8, 10, 0));
        dbHouse.setNearSubway(1);
        dbHouse.setPrivateBathroom(1);
        dbHouse.setHasBalcony(1);
        dbHouse.setCivilWaterElectric(1);
        dbHouse.setSupportStudentDepositFree(1);
        dbHouse.setRentType(2);

        HouseDoc esDoc = new HouseDoc();
        esDoc.setId(dbHouse.getId());
        esDoc.setPublisherUserId(dbHouse.getPublisherUserId());
        esDoc.setTitle(dbHouse.getTitle());
        esDoc.setCity(dbHouse.getCity());
        esDoc.setRegion(dbHouse.getRegion());
        esDoc.setNearSubway(true);
        esDoc.setPrivateBathroom(true);
        esDoc.setHasBalcony(true);
        esDoc.setCivilWaterElectric(true);
        esDoc.setSupportStudentDepositFree(true);
        esDoc.setRentType(2);
        esDoc.setPrice(dbHouse.getPrice());
        esDoc.setDepositAmount(dbHouse.getDepositAmount());
        esDoc.setStatus(dbHouse.getStatus());
        esDoc.setCreateTime(dbHouse.getCreateTime());

        House esHouse = ReflectionTestUtils.invokeMethod(
                new cn.yy.myrent.service.discovery.HouseRecallServiceImpl(
                        elasticsearchOperations,
                        houseMapper,
                        locationResolveService,
                        null,
                        null
                ),
                "convertDocToHouse",
                esDoc
        );

        HouseVO dbVo = ReflectionTestUtils.invokeMethod(houseService, "convertHouseToVo", dbHouse);
        HouseVO esVo = ReflectionTestUtils.invokeMethod(houseService, "convertHouseToVo", esHouse);

        assertEquals(dbHouse.getRentType(), esHouse.getRentType());
        assertEquals(dbHouse.getPrice(), esHouse.getPrice());
        assertEquals(dbHouse.getCreateTime(), esHouse.getCreateTime());
        assertEquals(dbHouse.getNearSubway(), esHouse.getNearSubway());
        assertEquals(dbHouse.getPrivateBathroom(), esHouse.getPrivateBathroom());
        assertEquals(dbHouse.getHasBalcony(), esHouse.getHasBalcony());
        assertEquals(dbHouse.getCivilWaterElectric(), esHouse.getCivilWaterElectric());
        assertEquals(dbHouse.getSupportStudentDepositFree(), esHouse.getSupportStudentDepositFree());
        assertEquals(dbVo.getNearSubway(), esVo.getNearSubway());
        assertEquals(dbVo.getPrivateBathroom(), esVo.getPrivateBathroom());
        assertEquals(dbVo.getHasBalcony(), esVo.getHasBalcony());
        assertEquals(dbVo.getCivilWaterElectric(), esVo.getCivilWaterElectric());
        assertEquals(dbVo.getSupportStudentDepositFree(), esVo.getSupportStudentDepositFree());
    }

    private HouseServiceImpl newHouseService() {
        return new HouseServiceImpl(
                elasticsearchOperations,
                stringRedisTemplate,
                smartGuideRecommendationService,
                houseHotService,
                locationResolveService,
                userService,
                houseRecallService,
                houseRankingService
        );
    }

    private HouseListFilterReqDTO buildFilterReq() {
        HouseListFilterReqDTO reqDTO = new HouseListFilterReqDTO();
        reqDTO.setCity("苏州");
        reqDTO.setRegion("姑苏");
        reqDTO.setRentType(1);
        reqDTO.setMinPriceYuan(1500);
        reqDTO.setMaxPriceYuan(3500);
        reqDTO.setNearSubway(true);
        reqDTO.setPrivateBathroom(true);
        reqDTO.setHasBalcony(true);
        reqDTO.setCivilWaterElectric(true);
        reqDTO.setSupportStudentDepositFree(true);
        reqDTO.setPage(1);
        reqDTO.setSize(8);
        return reqDTO;
    }

    private House buildHouse(Long id, String city, String region, int priceYuan, LocalDateTime createTime) {
        House house = new House();
        house.setId(id);
        house.setPublisherUserId(1000L + id);
        house.setTitle("房源" + id);
        house.setCity(city);
        house.setRegion(region);
        house.setNearSubway(1);
        house.setPrivateBathroom(1);
        house.setHasBalcony(1);
        house.setCivilWaterElectric(1);
        house.setSupportStudentDepositFree(1);
        house.setRentType(1);
        house.setPrice(priceYuan * 100);
        house.setDepositAmount(100000);
        house.setStatus(1);
        house.setCreateTime(createTime);
        return house;
    }
}

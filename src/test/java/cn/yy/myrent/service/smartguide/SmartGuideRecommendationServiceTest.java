package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.discovery.HouseRankQuery;
import cn.yy.myrent.service.discovery.HouseRankResult;
import cn.yy.myrent.service.discovery.HouseRankedItem;
import cn.yy.myrent.service.discovery.HouseRankingProfile;
import cn.yy.myrent.service.discovery.HouseRankingService;
import cn.yy.myrent.service.discovery.HouseReasonCode;
import cn.yy.myrent.service.discovery.HouseRecallCandidate;
import cn.yy.myrent.service.discovery.HouseRecallEvidence;
import cn.yy.myrent.service.discovery.HouseRecallMatchTier;
import cn.yy.myrent.service.discovery.HouseRecallProfile;
import cn.yy.myrent.service.discovery.HouseRecallQuery;
import cn.yy.myrent.service.discovery.HouseRecallResult;
import cn.yy.myrent.service.discovery.HouseRecallService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.vo.SmartGuideItemVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartGuideRecommendationServiceTest {

    @Mock
    private HouseRecallService houseRecallService;

    @Mock
    private HouseRankingService houseRankingService;

    @Mock
    private LocationResolveService locationResolveService;

    @InjectMocks
    private SmartGuideRecommendationService service;

    @Test
    void recommendShouldUseSharedRecallAndAiRecommendDefaultRanking() {
        SmartGuideReqDTO req = request();
        House house = house(101L, 3000, 0);
        HouseRecallCandidate candidate = new HouseRecallCandidate(
                house,
                HouseRecallMatchTier.EXACT,
                HouseRecallEvidence.builder()
                        .exactConstraintMatched(true)
                        .locationDistanceMeters(800.0d)
                        .build()
        );
        when(locationResolveService.resolveRequired("Yuyuan"))
                .thenReturn(new LocationResolveService.ResolvedLocation("Yuyuan", 31.227, 121.492));
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(candidate), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(
                        List.of(new HouseRankedItem(house, 321.0d, null, List.of(HouseReasonCode.RENT_MODE_MATCH))),
                        List.of(new HouseRankedItem(house, 321.0d, null, List.of(HouseReasonCode.RENT_MODE_MATCH))),
                        1
                ));

        service.recommend(req);

        ArgumentCaptor<HouseRecallQuery> recallCaptor = ArgumentCaptor.forClass(HouseRecallQuery.class);
        verify(houseRecallService).recall(recallCaptor.capture());
        assertEquals(HouseRecallProfile.SMART_GUIDE, recallCaptor.getValue().recallProfile());
        assertEquals(Integer.valueOf(200), recallCaptor.getValue().size());
        assertEquals("WHOLE", recallCaptor.getValue().rentMode());

        ArgumentCaptor<HouseRankQuery> rankCaptor = ArgumentCaptor.forClass(HouseRankQuery.class);
        verify(houseRankingService).rank(any(), rankCaptor.capture());
        assertEquals(HouseRankingProfile.AI_RECOMMEND_DEFAULT, rankCaptor.getValue().rankingProfile());
        assertEquals("1", rankCaptor.getValue().rentMode());
        assertEquals(Integer.valueOf(3000), rankCaptor.getValue().budgetYuan());
    }

    @Test
    void relaxedBudgetPathShouldProduceRelaxedTipAndFlag() {
        SmartGuideReqDTO req = request();
        House exactHouse = house(201L, 2200, 0);
        House relaxedHouse = house(202L, 3400, 0);
        HouseRecallCandidate exactCandidate = new HouseRecallCandidate(
                exactHouse,
                HouseRecallMatchTier.EXACT,
                HouseRecallEvidence.builder()
                        .exactConstraintMatched(true)
                        .locationDistanceMeters(1000.0d)
                        .build()
        );
        HouseRecallCandidate relaxedCandidate = new HouseRecallCandidate(
                relaxedHouse,
                HouseRecallMatchTier.EXACT,
                HouseRecallEvidence.builder()
                        .relaxedBudgetApplied(true)
                        .relaxedRadiusApplied(true)
                        .locationDistanceMeters(1500.0d)
                        .build()
        );
        when(locationResolveService.resolveRequired("Yuyuan"))
                .thenReturn(new LocationResolveService.ResolvedLocation("Yuyuan", 31.227, 121.492));
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(exactCandidate, relaxedCandidate), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(
                        List.of(
                                new HouseRankedItem(exactHouse, 300.0d, null, List.of()),
                                new HouseRankedItem(relaxedHouse, 280.0d, null, List.of(HouseReasonCode.RELAXED_BUDGET_APPLIED))
                        ),
                        List.of(
                                new HouseRankedItem(exactHouse, 300.0d, null, List.of()),
                                new HouseRankedItem(relaxedHouse, 280.0d, null, List.of(HouseReasonCode.RELAXED_BUDGET_APPLIED))
                        ),
                        2
                ));

        SmartGuideResultVO result = service.recommend(req);

        assertTrue(Boolean.TRUE.equals(result.getRelaxedBudget()));
        assertEquals(Integer.valueOf(3400), result.getRelaxedBudgetYuan());
        assertNotNull(result.getTipMessage());
        assertEquals(
                "\u5b8c\u5168\u7b26\u5408\u6761\u4ef6\u7684\u623f\u6e90\u8f83\u5c11\uff0c\u5df2\u8865\u5145\u653e\u5bbd\u6761\u4ef6\u540e\u7684\u5907\u9009\u7ed3\u679c\u3002",
                result.getTipMessage()
        );
        assertFalse(Boolean.TRUE.equals(result.getMatchedExpectation()));
    }

    @Test
    void relaxedBudgetYuanShouldStillIncreaseWhenRelaxedCandidateIsTrimmedByRanking() {
        SmartGuideReqDTO req = request();
        House exactHouse = house(211L, 2800, 0);
        House relaxedHouse = house(212L, 3600, 0);
        HouseRecallCandidate exactCandidate = new HouseRecallCandidate(
                exactHouse,
                HouseRecallMatchTier.EXACT,
                HouseRecallEvidence.builder()
                        .exactConstraintMatched(true)
                        .locationDistanceMeters(900.0d)
                        .build()
        );
        HouseRecallCandidate relaxedCandidate = new HouseRecallCandidate(
                relaxedHouse,
                HouseRecallMatchTier.EXACT,
                HouseRecallEvidence.builder()
                        .relaxedBudgetApplied(true)
                        .locationDistanceMeters(1800.0d)
                        .build()
        );
        when(locationResolveService.resolveRequired("Yuyuan"))
                .thenReturn(new LocationResolveService.ResolvedLocation("Yuyuan", 31.227, 121.492));
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(exactCandidate, relaxedCandidate), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(
                        List.of(new HouseRankedItem(exactHouse, 310.0d, null, List.of())),
                        List.of(new HouseRankedItem(exactHouse, 310.0d, null, List.of())),
                        1
                ));

        SmartGuideResultVO result = service.recommend(req);

        assertTrue(Boolean.TRUE.equals(result.getRelaxedBudget()));
        assertTrue(result.getRelaxedBudgetYuan() > req.getBudgetYuan());
        assertEquals(Integer.valueOf(3600), result.getRelaxedBudgetYuan());
    }

    @Test
    void rankedReasonCodesShouldMapToOutwardReasons() {
        SmartGuideReqDTO req = request();
        House house = house(301L, 2950, 100000);
        HouseRecallCandidate candidate = new HouseRecallCandidate(
                house,
                HouseRecallMatchTier.EXACT,
                HouseRecallEvidence.builder()
                        .exactConstraintMatched(true)
                        .locationDistanceMeters(1200.0d)
                        .build()
        );
        when(locationResolveService.resolveRequired("Yuyuan"))
                .thenReturn(new LocationResolveService.ResolvedLocation("Yuyuan", 31.227, 121.492));
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(candidate), false, true));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(
                        List.of(new HouseRankedItem(
                                house,
                                456.789d,
                                null,
                                List.of(
                                        HouseReasonCode.BUDGET_CLOSE_MATCH,
                                        HouseReasonCode.LOCATION_DISTANCE_ADVANTAGE,
                                        HouseReasonCode.RENT_MODE_MATCH
                                )
                        )),
                        List.of(new HouseRankedItem(
                                house,
                                456.789d,
                                null,
                                List.of(
                                        HouseReasonCode.BUDGET_CLOSE_MATCH,
                                        HouseReasonCode.LOCATION_DISTANCE_ADVANTAGE,
                                        HouseReasonCode.RENT_MODE_MATCH
                                )
                        )),
                        1
                ));

        SmartGuideResultVO result = service.recommend(req);

        SmartGuideItemVO item = result.getRecommendations().get(0);
        assertEquals(List.of(
                "\u6708\u79df\u8d34\u8fd1\u9884\u7b97",
                "\u8ddd\u76ee\u6807\u5730\u70b9\u7ea6 1.2km",
                "\u79df\u4f4f\u65b9\u5f0f\u5339\u914d"
        ), item.getReasons());
        assertEquals(
                "\u7531\u4e8e ES \u9884\u7b5b\u6682\u4e0d\u53ef\u7528\uff0c\u5f53\u524d\u7ed3\u679c\u5df2\u964d\u7ea7\u4e3a DB \u4e8c\u6b21\u7b5b\u9009\u3002",
                result.getTipMessage()
        );
    }

    private SmartGuideReqDTO request() {
        SmartGuideReqDTO req = new SmartGuideReqDTO();
        req.setBudgetYuan(3000);
        req.setBudgetScope("RENT_ONLY");
        req.setRentMode("WHOLE");
        req.setLocationName("Yuyuan");
        req.setPage(1);
        req.setSize(10);
        return req;
    }

    private House house(Long id, int priceYuan, int depositYuan) {
        House house = new House();
        house.setId(id);
        house.setPublisherUserId(900L + id);
        house.setTitle("house-" + id);
        house.setStatus(1);
        house.setPrice(priceYuan * 100);
        house.setDepositAmount(depositYuan);
        return house;
    }
}

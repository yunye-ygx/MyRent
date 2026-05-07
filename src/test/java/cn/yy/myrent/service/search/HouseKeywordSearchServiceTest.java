package cn.yy.myrent.service.search;

import cn.yy.myrent.dto.HouseKeywordSearchReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.discovery.HouseRankResult;
import cn.yy.myrent.service.discovery.HouseRankedItem;
import cn.yy.myrent.service.discovery.HouseRankingService;
import cn.yy.myrent.service.discovery.HouseRankingServiceImpl;
import cn.yy.myrent.service.discovery.HouseRecallCandidate;
import cn.yy.myrent.service.discovery.HouseRecallEvidence;
import cn.yy.myrent.service.discovery.HouseRecallMatchTier;
import cn.yy.myrent.service.discovery.HouseRecallProfile;
import cn.yy.myrent.service.discovery.HouseRecallQuery;
import cn.yy.myrent.service.discovery.HouseRecallResult;
import cn.yy.myrent.service.discovery.HouseRecallService;
import cn.yy.myrent.service.discovery.HouseReasonCode;
import cn.yy.myrent.service.discovery.HouseScoreBreakdown;
import cn.yy.myrent.vo.HouseSearchResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseKeywordSearchServiceTest {

    @Mock
    private HouseRecallService houseRecallService;

    @Mock
    private IUserService userService;

    private final HouseRankingService houseRankingService = new HouseRankingServiceImpl();

    @Test
    void searchShouldDelegateToRecallAndKeepRankingOrderWhileTotalReflectsFullCandidateSet() {
        House locationOnly = buildHouse(11L, 1001L, "location", LocalDateTime.of(2026, 4, 25, 10, 0));
        House bothMatched = buildHouse(12L, 1002L, "both", LocalDateTime.of(2026, 4, 25, 11, 0));
        House textOnly = buildHouse(13L, 1003L, "text", LocalDateTime.of(2026, 4, 25, 9, 0));
        House tail = buildHouse(14L, 1004L, "tail", LocalDateTime.of(2026, 4, 20, 9, 0));

        when(houseRecallService.recall(any(HouseRecallQuery.class))).thenReturn(new HouseRecallResult(
                List.of(
                        new HouseRecallCandidate(locationOnly, HouseRecallMatchTier.LOCATION_ONLY,
                                HouseRecallEvidence.builder()
                                        .locationMatched(true)
                                        .locationDistanceMeters(120.0d)
                                        .locationRank(0)
                                        .build()),
                        new HouseRecallCandidate(bothMatched, HouseRecallMatchTier.EXACT,
                                HouseRecallEvidence.builder()
                                        .locationMatched(true)
                                        .textMatched(true)
                                        .locationDistanceMeters(260.0d)
                                        .locationRank(1)
                                        .textRank(0)
                                        .textScore(2.1f)
                                        .build()),
                        new HouseRecallCandidate(textOnly, HouseRecallMatchTier.TEXT_ONLY,
                                HouseRecallEvidence.builder()
                                        .textMatched(true)
                                        .textRank(1)
                                        .textScore(1.4f)
                                        .build()),
                        new HouseRecallCandidate(tail, HouseRecallMatchTier.TEXT_ONLY,
                                HouseRecallEvidence.builder()
                                        .textMatched(true)
                                        .textRank(20)
                                        .textScore(0.2f)
                                        .build())
                ),
                true,
                false
        ));
        when(userService.listByIds(List.of(1002L, 1001L))).thenReturn(List.of(user(1002L, "B"), user(1001L, "A")));

        HouseKeywordSearchService houseKeywordSearchService =
                new HouseKeywordSearchService(houseRecallService, houseRankingService, userService);
        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("天河公园单间");
        reqDTO.setPage(1);
        reqDTO.setSize(2);

        HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

        assertEquals(2, result.getHouses().size());
        assertEquals(4L, result.getTotal());
        assertEquals(12L, result.getHouses().get(0).getId());
        assertEquals(11L, result.getHouses().get(1).getId());
        assertEquals(
                List.of("\u540c\u65f6\u547d\u4e2d\u5173\u952e\u8bcd\u4e0e\u4f4d\u7f6e", "\u8ddd\u76ee\u6807\u5730\u70b9\u7ea6 0.3km"),
                result.getHouses().get(0).getSearchReasons()
        );
        assertEquals(
                List.of("RECALL_LOCATION_MATCH", "RECALL_TEXT_MATCH", "LOCATION_DISTANCE_ADVANTAGE"),
                result.getHouses().get(0).getSearchReasonCodes()
        );
        assertEquals("KEYWORD_SEARCH", result.getFallbackSource());
        assertEquals(Boolean.FALSE, result.getEsDown());
        assertNull(result.getTipMessage());

        ArgumentCaptor<HouseRecallQuery> queryCaptor = ArgumentCaptor.forClass(HouseRecallQuery.class);
        verify(houseRecallService).recall(queryCaptor.capture());
        assertEquals("天河公园单间", queryCaptor.getValue().keyword());
        assertEquals(1, queryCaptor.getValue().page());
        assertEquals(2, queryCaptor.getValue().size());
        assertEquals(HouseRecallProfile.KEYWORD_SEARCH, queryCaptor.getValue().recallProfile());
    }

    @Test
    void searchShouldExposeDegradedStatusFromRecall() {
        when(houseRecallService.recall(any(HouseRecallQuery.class))).thenReturn(new HouseRecallResult(
                List.of(new HouseRecallCandidate(
                        buildHouse(31L, 2001L, "only", LocalDateTime.of(2026, 4, 25, 9, 0)),
                        HouseRecallMatchTier.LOCATION_ONLY,
                        HouseRecallEvidence.builder().locationMatched(true).locationDistanceMeters(88.0d).build()
                )),
                false,
                true
        ));
        when(userService.listByIds(List.of(2001L))).thenReturn(List.of(user(2001L, "C")));

        HouseKeywordSearchService houseKeywordSearchService =
                new HouseKeywordSearchService(houseRecallService, houseRankingService, userService);
        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("体育西路");
        reqDTO.setPage(1);
        reqDTO.setSize(1);

        HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

        assertEquals(1, result.getHouses().size());
        assertEquals(1L, result.getTotal());
        assertEquals(Boolean.TRUE, result.getEsDown());
        assertEquals("KEYWORD_SEARCH_DEGRADED", result.getFallbackSource());
    }

    @Test
    void searchShouldBuildDistanceReasonFromRecallEvidenceInsteadOfRankingScore() {
        House bothMatched = buildHouse(41L, 3001L, "both", LocalDateTime.of(2026, 4, 25, 11, 0));
        HouseRecallResult recallResult = new HouseRecallResult(
                List.of(new HouseRecallCandidate(
                        bothMatched,
                        HouseRecallMatchTier.EXACT,
                        HouseRecallEvidence.builder()
                                .locationMatched(true)
                                .textMatched(true)
                                .locationDistanceMeters(1260.0d)
                                .locationRank(0)
                                .textRank(0)
                                .textScore(2.0f)
                                .build()
                )),
                true,
                false
        );
        HouseRankingService rankingService = (candidates, query) -> new HouseRankResult(
                List.of(new HouseRankedItem(
                        bothMatched,
                        999.0d,
                        HouseScoreBreakdown.builder()
                                .locationDistanceScore(0.0d)
                                .build(),
                        List.of(HouseReasonCode.RECALL_LOCATION_MATCH,
                                HouseReasonCode.RECALL_TEXT_MATCH,
                                HouseReasonCode.LOCATION_DISTANCE_ADVANTAGE)
                )),
                List.of(new HouseRankedItem(
                        bothMatched,
                        999.0d,
                        HouseScoreBreakdown.builder()
                                .locationDistanceScore(0.0d)
                                .build(),
                        List.of(HouseReasonCode.RECALL_LOCATION_MATCH,
                                HouseReasonCode.RECALL_TEXT_MATCH,
                                HouseReasonCode.LOCATION_DISTANCE_ADVANTAGE)
                )),
                1L
        );
        when(houseRecallService.recall(any(HouseRecallQuery.class))).thenReturn(recallResult);
        when(userService.listByIds(List.of(3001L))).thenReturn(List.of(user(3001L, "D")));

        HouseKeywordSearchService houseKeywordSearchService =
                new HouseKeywordSearchService(houseRecallService, rankingService, userService);
        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("体育西路");
        reqDTO.setPage(1);
        reqDTO.setSize(1);

        HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

        assertEquals(
                List.of("同时命中关键词与位置", "距目标地点约 1.3km"),
                result.getHouses().get(0).getSearchReasons()
        );
    }

    @Test
    void searchShouldFailSoftWhenPublisherEnrichmentThrows() {
        House bothMatched = buildHouse(51L, 4001L, "both", LocalDateTime.of(2026, 4, 25, 11, 0));
        when(houseRecallService.recall(any(HouseRecallQuery.class))).thenReturn(new HouseRecallResult(
                List.of(new HouseRecallCandidate(
                        bothMatched,
                        HouseRecallMatchTier.EXACT,
                        HouseRecallEvidence.builder()
                                .locationMatched(true)
                                .textMatched(true)
                                .locationDistanceMeters(260.0d)
                                .locationRank(0)
                                .textRank(0)
                                .textScore(2.0f)
                                .build()
                )),
                true,
                false
        ));
        doThrow(new RuntimeException("lookup failed")).when(userService).listByIds(List.of(4001L));

        HouseKeywordSearchService houseKeywordSearchService =
                new HouseKeywordSearchService(houseRecallService, houseRankingService, userService);
        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("天河公园");
        reqDTO.setPage(1);
        reqDTO.setSize(1);

        HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

        assertEquals(1, result.getHouses().size());
        assertEquals(51L, result.getHouses().get(0).getId());
        assertEquals("未知发布者", result.getHouses().get(0).getPublisherName());
        assertEquals("KEYWORD_SEARCH", result.getFallbackSource());
        assertEquals(Boolean.FALSE, result.getEsDown());
    }

    @Test
    void searchShouldReturnReadableTipWhenRecallReturnsNoCandidates() {
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(), false, true));

        HouseKeywordSearchService houseKeywordSearchService =
                new HouseKeywordSearchService(houseRecallService, houseRankingService, userService);
        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("天河公园");
        reqDTO.setPage(1);
        reqDTO.setSize(10);

        HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

        assertTrue(result.getHouses().isEmpty());
        assertEquals(0L, result.getTotal());
        assertEquals(Boolean.TRUE, result.getEsDown());
        assertEquals("KEYWORD_SEARCH_DEGRADED", result.getFallbackSource());
        assertEquals("当前未找到匹配房源", result.getTipMessage());
    }

    private House buildHouse(Long id, Long publisherUserId, String title, LocalDateTime createTime) {
        House house = new House();
        house.setId(id);
        house.setPublisherUserId(publisherUserId);
        house.setTitle(title);
        house.setCity("广州");
        house.setRegion("天河");
        house.setPrice(300000);
        house.setDepositAmount(300000);
        house.setStatus(1);
        house.setCreateTime(createTime);
        return house;
    }

    private User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }
}

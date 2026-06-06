package cn.yy.myrent.service.ai.chat.tools;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.discovery.HouseRankQuery;
import cn.yy.myrent.service.discovery.HouseRankResult;
import cn.yy.myrent.service.discovery.HouseRankedItem;
import cn.yy.myrent.service.discovery.HouseRankingService;
import cn.yy.myrent.service.discovery.HouseReasonCode;
import cn.yy.myrent.service.discovery.HouseRecallCandidate;
import cn.yy.myrent.service.discovery.HouseRecallEvidence;
import cn.yy.myrent.service.discovery.HouseRecallMatchTier;
import cn.yy.myrent.service.discovery.HouseRecallQuery;
import cn.yy.myrent.service.discovery.HouseRecallResult;
import cn.yy.myrent.service.discovery.HouseRecallService;
import cn.yy.myrent.service.location.LocationResolveService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchHousesToolTest {

    @Mock
    private HouseRecallService houseRecallService;

    @Mock
    private HouseRankingService houseRankingService;

    @Mock
    private LocationResolveService locationResolveService;

    @InjectMocks
    private SearchHousesTool searchHousesTool;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchHousesShouldRejectNegativeBudget() throws Exception {
        searchHousesTool = new SearchHousesTool(houseRecallService, houseRankingService, locationResolveService, objectMapper);

        String result = searchHousesTool.searchHouses("陆家嘴", -5000, "WHOLE", null, null, null, null, 5);
        JsonNode root = objectMapper.readTree(result);

        assertFalse(root.get("ok").asBoolean());
        assertEquals("INVALID_BUDGET", root.get("errorCode").asText());
        verify(houseRecallService, never()).recall(any(HouseRecallQuery.class));
        verify(houseRankingService, never()).rank(any(), any(HouseRankQuery.class));
    }

    @Test
    void searchHousesShouldRejectUnsupportedRentMode() throws Exception {
        searchHousesTool = new SearchHousesTool(houseRecallService, houseRankingService, locationResolveService, objectMapper);

        String result = searchHousesTool.searchHouses("陆家嘴", 3500, "BIG", null, null, null, null, 5);
        JsonNode root = objectMapper.readTree(result);

        assertFalse(root.get("ok").asBoolean());
        assertEquals("INVALID_RENT_MODE", root.get("errorCode").asText());
        verify(houseRecallService, never()).recall(any(HouseRecallQuery.class));
    }

    @Test
    void searchHousesShouldReturnStructuredReasonsForRankedHouses() throws Exception {
        searchHousesTool = new SearchHousesTool(houseRecallService, houseRankingService, locationResolveService, objectMapper);
        when(locationResolveService.resolveRequired("陆家嘴"))
                .thenReturn(new LocationResolveService.ResolvedLocation("陆家嘴", 31.0d, 121.0d));
        House house = house(101L);
        HouseRecallCandidate candidate = new HouseRecallCandidate(
                house,
                HouseRecallMatchTier.EXACT,
                HouseRecallEvidence.builder()
                        .locationMatched(true)
                        .locationDistanceMeters(1200.0d)
                        .build()
        );
        HouseRankedItem rankedItem = new HouseRankedItem(
                house,
                456.789d,
                null,
                List.of(
                        HouseReasonCode.BUDGET_CLOSE_MATCH,
                        HouseReasonCode.LOCATION_DISTANCE_ADVANTAGE,
                        HouseReasonCode.NEAR_SUBWAY_MATCH
                )
        );
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(candidate), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(List.of(rankedItem), List.of(rankedItem), 1));

        String result = searchHousesTool.searchHouses("陆家嘴", 3500, "WHOLE", true, null, null, null, 5);
        JsonNode root = objectMapper.readTree(result);

        assertTrue(root.get("ok").asBoolean());
        assertEquals(1, root.get("count").asInt());
        JsonNode firstHouse = root.get("houses").get(0);
        assertTrue(firstHouse.has("reasons"));
        assertTrue(firstHouse.get("reasons").isArray());
        assertTrue(firstHouse.get("reasons").size() > 0);
        assertTrue(firstHouse.has("reasonCodes"));
        assertTrue(firstHouse.get("reasonCodes").isArray());
        assertEquals("BUDGET_CLOSE_MATCH", firstHouse.get("reasonCodes").get(0).asText());
        assertTrue(firstHouse.has("score"));

        ArgumentCaptor<HouseRankQuery> rankQueryCaptor = ArgumentCaptor.forClass(HouseRankQuery.class);
        verify(houseRankingService).rank(any(), rankQueryCaptor.capture());
        assertEquals("1", rankQueryCaptor.getValue().rentMode());
    }

    private House house(Long id) {
        House house = new House();
        house.setId(id);
        house.setTitle("陆家嘴精装一居");
        house.setPrice(350000);
        house.setRentType(1);
        house.setNearSubway(1);
        house.setPrivateBathroom(1);
        house.setCreateTime(LocalDateTime.now());
        return house;
    }
}

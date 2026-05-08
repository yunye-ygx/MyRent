package cn.yy.myrent.service.search;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.HouseKeywordSearchReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.location.LocationResolveService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseKeywordSearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private LocationResolveService locationResolveService;

    @Mock
    private IUserService userService;

    @Mock
    private HouseHotService houseHotService;

    @InjectMocks
    private HouseKeywordSearchService houseKeywordSearchService;

    @Test
    void searchShouldMergeDualRecallAndDropUnavailableDbRows() {
        HouseDoc locationDoc = new HouseDoc();
        locationDoc.setId(11L);
        locationDoc.setStatus(1);
        locationDoc.setTitle("体育西路地铁口单间");

        HouseDoc sharedDoc = new HouseDoc();
        sharedDoc.setId(12L);
        sharedDoc.setStatus(1);
        sharedDoc.setTitle("天河公园精装单间");

        HouseDoc textDoc = new HouseDoc();
        textDoc.setId(13L);
        textDoc.setStatus(1);
        textDoc.setTitle("天河公园主卧");

        @SuppressWarnings("unchecked")
        SearchHit<HouseDoc> locationHitOne = (SearchHit<HouseDoc>) mock(SearchHit.class);
        @SuppressWarnings("unchecked")
        SearchHit<HouseDoc> locationHitTwo = (SearchHit<HouseDoc>) mock(SearchHit.class);
        @SuppressWarnings("unchecked")
        SearchHit<HouseDoc> textHitOne = (SearchHit<HouseDoc>) mock(SearchHit.class);
        @SuppressWarnings("unchecked")
        SearchHit<HouseDoc> textHitTwo = (SearchHit<HouseDoc>) mock(SearchHit.class);

        when(locationHitOne.getContent()).thenReturn(locationDoc);
        when(locationHitOne.getSortValues()).thenReturn(List.of(120.0));
        when(locationHitTwo.getContent()).thenReturn(sharedDoc);
        when(locationHitTwo.getSortValues()).thenReturn(List.of(260.0));
        when(textHitOne.getContent()).thenReturn(sharedDoc);
        when(textHitOne.getScore()).thenReturn(2.1f);
        when(textHitTwo.getContent()).thenReturn(textDoc);
        when(textHitTwo.getScore()).thenReturn(1.4f);

        @SuppressWarnings("unchecked")
        SearchHits<HouseDoc> locationHits = (SearchHits<HouseDoc>) mock(SearchHits.class);
        @SuppressWarnings("unchecked")
        SearchHits<HouseDoc> textHits = (SearchHits<HouseDoc>) mock(SearchHits.class);
        when(locationHits.iterator()).thenReturn(List.of(locationHitOne, locationHitTwo).iterator());
        when(textHits.iterator()).thenReturn(List.of(textHitOne, textHitTwo).iterator());

        when(locationResolveService.resolveRequired("天河公园单间"))
                .thenReturn(new LocationResolveService.ResolvedLocation("天河公园", 23.145d, 113.333d));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class)))
                .thenAnswer(invocation -> isLocationQuery(invocation.getArgument(0)) ? locationHits : textHits);

        House availableLocation = new House();
        availableLocation.setId(11L);
        availableLocation.setPublisherUserId(1001L);
        availableLocation.setTitle("体育西路地铁口单间");
        availableLocation.setCity("广州");
        availableLocation.setRegion("天河");
        availableLocation.setPrice(300000);
        availableLocation.setDepositAmount(300000);
        availableLocation.setStatus(1);
        availableLocation.setCreateTime(LocalDateTime.of(2026, 4, 25, 10, 0));

        House dualHit = new House();
        dualHit.setId(12L);
        dualHit.setPublisherUserId(1002L);
        dualHit.setTitle("天河公园精装单间");
        dualHit.setCity("广州");
        dualHit.setRegion("天河");
        dualHit.setPrice(320000);
        dualHit.setDepositAmount(320000);
        dualHit.setStatus(1);
        dualHit.setCreateTime(LocalDateTime.of(2026, 4, 25, 11, 0));

        House unavailableTextOnly = new House();
        unavailableTextOnly.setId(13L);
        unavailableTextOnly.setPublisherUserId(1003L);
        unavailableTextOnly.setTitle("天河公园主卧");
        unavailableTextOnly.setStatus(2);

        when(houseMapper.selectBatchIds(List.of(11L, 12L, 13L)))
                .thenReturn(List.of(dualHit, unavailableTextOnly, availableLocation));

        User publisherOne = new User();
        publisherOne.setId(1001L);
        publisherOne.setName("房东A");
        User publisherTwo = new User();
        publisherTwo.setId(1002L);
        publisherTwo.setName("房东B");
        when(userService.listByIds(List.of(1002L, 1001L))).thenReturn(List.of(publisherTwo, publisherOne));

        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("天河公园单间");
        reqDTO.setPage(1);
        reqDTO.setSize(2);

        HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

        assertEquals(2, result.getHouses().size());
        assertEquals(2L, result.getTotal());
        assertEquals(12L, result.getHouses().get(0).getId());
        assertEquals(11L, result.getHouses().get(1).getId());
        assertEquals("KEYWORD_SEARCH", result.getFallbackSource());
        assertEquals(Boolean.FALSE, result.getEsDown());
    }

    @Test
    void searchShouldMarkDegradedWhenTextRecallFailsButLocationRecallSucceeds() {
        HouseDoc locationDoc = new HouseDoc();
        locationDoc.setId(31L);
        locationDoc.setStatus(1);
        locationDoc.setTitle("体育西路地铁口单间");

        @SuppressWarnings("unchecked")
        SearchHit<HouseDoc> locationHit = (SearchHit<HouseDoc>) mock(SearchHit.class);
        when(locationHit.getContent()).thenReturn(locationDoc);
        when(locationHit.getSortValues()).thenReturn(List.of(88.0));

        @SuppressWarnings("unchecked")
        SearchHits<HouseDoc> locationHits = (SearchHits<HouseDoc>) mock(SearchHits.class);
        when(locationHits.iterator()).thenReturn(List.of(locationHit).iterator());

        when(locationResolveService.resolveRequired("体育西路"))
                .thenReturn(new LocationResolveService.ResolvedLocation("体育西路", 23.132d, 113.321d));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class)))
                .thenAnswer(invocation -> {
                    NativeQuery query = invocation.getArgument(0);
                    if (isLocationQuery(query)) {
                        return locationHits;
                    }
                    throw new RuntimeException("ES text path down");
                });

        House dbHouse = new House();
        dbHouse.setId(31L);
        dbHouse.setPublisherUserId(2001L);
        dbHouse.setTitle("体育西路地铁口单间");
        dbHouse.setCity("广州");
        dbHouse.setRegion("天河");
        dbHouse.setPrice(280000);
        dbHouse.setDepositAmount(280000);
        dbHouse.setStatus(1);
        dbHouse.setCreateTime(LocalDateTime.of(2026, 4, 25, 9, 0));
        when(houseMapper.selectBatchIds(List.of(31L))).thenReturn(List.of(dbHouse));

        User publisher = new User();
        publisher.setId(2001L);
        publisher.setName("房东C");
        when(userService.listByIds(List.of(2001L))).thenReturn(List.of(publisher));

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
    void searchShouldOversampleEachRecallPathWithSizeTimesThree() {
        @SuppressWarnings("unchecked")
        SearchHits<HouseDoc> emptyHits = (SearchHits<HouseDoc>) mock(SearchHits.class);
        when(emptyHits.iterator()).thenReturn(List.<SearchHit<HouseDoc>>of().iterator());

        when(locationResolveService.resolveRequired("天河公园")).thenThrow(new IllegalArgumentException("not found"));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class))).thenReturn(emptyHits);

        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("天河公园");
        reqDTO.setPage(1);
        reqDTO.setSize(4);

        houseKeywordSearchService.search(reqDTO);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(HouseDoc.class));

        Pageable pageable = queryCaptor.getValue().getPageable();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(12, pageable.getPageSize());
    }

    @Test
    void searchShouldReturnEmptyWhenBothRecallPathsFail() {
        when(locationResolveService.resolveRequired("天河公园")).thenThrow(new RuntimeException("location path down"));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class)))
                .thenThrow(new RuntimeException("text path down"));

        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("天河公园");
        reqDTO.setPage(1);
        reqDTO.setSize(10);

        HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

        assertTrue(result.getHouses().isEmpty());
        assertEquals(0L, result.getTotal());
        assertEquals(Boolean.TRUE, result.getEsDown());
        assertEquals("KEYWORD_SEARCH_DEGRADED", result.getFallbackSource());
    }

    @Test
    void searchShouldFallbackToCityHotHousesWhenRecallIsEmptyAndCityIsPresent() {
        @SuppressWarnings("unchecked")
        SearchHits<HouseDoc> emptyHits = (SearchHits<HouseDoc>) mock(SearchHits.class);
        when(emptyHits.iterator()).thenReturn(List.<SearchHit<HouseDoc>>of().iterator());

        when(locationResolveService.resolveRequired("苏州园区")).thenThrow(new IllegalArgumentException("not found"));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class))).thenReturn(emptyHits);

        HouseVO hotHouse = new HouseVO();
        hotHouse.setId(88L);
        hotHouse.setCity("苏州");
        hotHouse.setTitle("园区可租热房");
        when(houseHotService.hasHotRankingCache("苏州")).thenReturn(false);
        when(houseHotService.queryHotHouses("苏州", 0, 2)).thenReturn(List.of(hotHouse));

        HouseKeywordSearchReqDTO reqDTO = new HouseKeywordSearchReqDTO();
        reqDTO.setKeyword("苏州园区");
        reqDTO.setCity("苏州");
        reqDTO.setPage(1);
        reqDTO.setSize(2);

        HouseSearchResultVO result = houseKeywordSearchService.search(reqDTO);

        assertEquals(1, result.getHouses().size());
        assertEquals(1L, result.getTotal());
        assertEquals(88L, result.getHouses().get(0).getId());
        assertEquals("REDIS_HOT", result.getFallbackSource());
        assertEquals("当前未找到匹配房源，已为你展示当前城市热门在租房源", result.getTipMessage());
        verify(houseHotService).rebuildHotRanking("苏州");
        verify(houseHotService).queryHotHouses("苏州", 0, 2);
    }

    private boolean isLocationQuery(NativeQuery nativeQuery) {
        Query query = nativeQuery.getQuery();
        return query != null
                && query.isBool()
                && query.bool().filter() != null
                && !query.bool().filter().isEmpty();
    }
}

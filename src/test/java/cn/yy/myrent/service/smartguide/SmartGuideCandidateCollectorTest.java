package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.location.LocationResolveService;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartGuideCandidateCollectorTest {

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private LocationResolveService locationResolveService;

    @InjectMocks
    private SmartGuideCandidateCollector collector;

    @Test
    void collectShouldSupportPreviewQueriesWithoutBudgetOrRentMode() {
        when(locationResolveService.resolveRequired("Yuyuan"))
                .thenReturn(new LocationResolveService.ResolvedLocation("Yuyuan", 31.227, 121.492));
        when(houseMapper.selectSmartGuideCandidateIds(any(), anyInt(), any(), anyBoolean(),
                any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(101L, 102L));
        when(houseMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(
                new House().setId(101L).setTitle("Yuyuan whole rent").setPrice(420000).setRentType(1),
                new House().setId(102L).setTitle("Yuyuan shared rent").setPrice(260000).setRentType(2)
        ));

        SmartGuideCandidateBundle bundle = collector.collect(SmartGuideCandidateQuery.builder()
                .locationName("Yuyuan")
                .size(12)
                .build());

        assertEquals("Yuyuan", bundle.locationName());
        assertEquals(31.227, bundle.targetLatitude());
        assertEquals(121.492, bundle.targetLongitude());
        assertEquals(2, bundle.candidates().size());
        assertTrue(bundle.candidates().stream().map(House::getId).toList().containsAll(List.of(101L, 102L)));

        ArgumentCaptor<Integer> rentTypeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> maxCostCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(houseMapper).selectSmartGuideCandidateIds(any(), anyInt(), rentTypeCaptor.capture(), anyBoolean(),
                maxCostCaptor.capture(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
        assertNull(rentTypeCaptor.getValue());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), maxCostCaptor.getValue());
    }

    @Test
    void collectShouldNotApplyRentKeywordFilterInEsWhenPreviewQueryOmitsRentMode() {
        when(locationResolveService.resolveRequired("Yuyuan"))
                .thenReturn(new LocationResolveService.ResolvedLocation("Yuyuan", 31.227, 121.492));

        HouseDoc docOne = new HouseDoc();
        docOne.setId(301L);
        HouseDoc docTwo = new HouseDoc();
        docTwo.setId(302L);

        @SuppressWarnings("unchecked")
        SearchHit<HouseDoc> hitOne = (SearchHit<HouseDoc>) mock(SearchHit.class);
        @SuppressWarnings("unchecked")
        SearchHit<HouseDoc> hitTwo = (SearchHit<HouseDoc>) mock(SearchHit.class);
        when(hitOne.getContent()).thenReturn(docOne);
        when(hitTwo.getContent()).thenReturn(docTwo);

        @SuppressWarnings("unchecked")
        SearchHits<HouseDoc> hits = (SearchHits<HouseDoc>) mock(SearchHits.class);
        when(hits.iterator()).thenReturn(List.of(hitOne, hitTwo).iterator());
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class))).thenReturn(hits);

        when(houseMapper.selectSmartGuideCandidateIds(eq(List.of(301L, 302L)), anyInt(), any(), anyBoolean(),
                any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(301L, 302L));
        when(houseMapper.selectBatchIds(List.of(301L, 302L))).thenReturn(List.of(
                new House().setId(301L).setTitle("Yuyuan whole rent").setPrice(420000).setRentType(1),
                new House().setId(302L).setTitle("Yuyuan shared rent").setPrice(260000).setRentType(2)
        ));

        SmartGuideCandidateBundle bundle = collector.collect(SmartGuideCandidateQuery.builder()
                .locationName("Yuyuan")
                .size(12)
                .build());

        assertEquals(2, bundle.candidates().size());

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(HouseDoc.class));
        Query esQuery = queryCaptor.getValue().getQuery();
        assertTrue(esQuery.isBool());
        List<Query> mustQueries = esQuery.bool().must();
        assertTrue(mustQueries.stream().noneMatch(q -> q.isTerm() && "rentType".equals(q.term().field())));
        assertTrue(mustQueries.stream().noneMatch(Query::isMatch));
    }

    @Test
    void collectShouldApplyBudgetAndRentModeWhenSearchQueryProvidesThem() {
        when(locationResolveService.resolveRequired("Yuyuan"))
                .thenReturn(new LocationResolveService.ResolvedLocation("Yuyuan", 31.227, 121.492));
        when(houseMapper.selectSmartGuideCandidateIds(any(), anyInt(), any(), anyBoolean(),
                any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(201L));
        when(houseMapper.selectBatchIds(List.of(201L))).thenReturn(List.of(
                new House().setId(201L).setTitle("Yuyuan one bedroom").setPrice(350000).setRentType(1)
        ));

        SmartGuideCandidateBundle bundle = collector.collect(SmartGuideCandidateQuery.builder()
                .locationName("Yuyuan")
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .rentMode("WHOLE")
                .size(10)
                .build());

        assertEquals(1, bundle.candidates().size());

        ArgumentCaptor<Integer> rentTypeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> maxCostCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(houseMapper).selectSmartGuideCandidateIds(any(), anyInt(), rentTypeCaptor.capture(), anyBoolean(),
                maxCostCaptor.capture(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
        assertEquals(Integer.valueOf(1), rentTypeCaptor.getValue());
        assertEquals(Integer.valueOf(350000), maxCostCaptor.getValue());
    }
}

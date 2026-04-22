package cn.yy.myrent.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.HouseSuggestReqDTO;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.service.smartguide.SmartGuideRecommendationService;
import cn.yy.myrent.vo.HouseSuggestItemVO;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseServiceImplSuggestTest {

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

    @InjectMocks
    private HouseServiceImpl houseService;

    @Test
    void suggestShouldQueryEsByTitleAndAvailableStatusAndMapFields() {
        HouseDoc doc = new HouseDoc();
        doc.setId(10L);
        doc.setTitle("title-10");
        // ES doc stores money in cents; suggest response should return display yuan.
        doc.setPrice(3000 * 100);

        @SuppressWarnings("unchecked")
        SearchHit<HouseDoc> hit = (SearchHit<HouseDoc>) mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        @SuppressWarnings("unchecked")
        SearchHits<HouseDoc> hits = (SearchHits<HouseDoc>) mock(SearchHits.class);
        when(hits.iterator()).thenReturn(List.of(hit).iterator());
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class))).thenReturn(hits);

        HouseSuggestReqDTO req = new HouseSuggestReqDTO();
        req.setKeyword("k");
        req.setSize(5);

        List<HouseSuggestItemVO> result = houseService.suggest(req);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
        assertEquals("title-10", result.get(0).getTitle());
        assertEquals(3000, result.get(0).getPrice());

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(HouseDoc.class));

        NativeQuery nativeQuery = queryCaptor.getValue();
        assertNotNull(nativeQuery);

        Pageable pageable = nativeQuery.getPageable();
        assertNotNull(pageable);
        assertEquals(0, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());

        Query esQuery = nativeQuery.getQuery();
        assertTrue(esQuery.isBool());

        List<Query> must = esQuery.bool().must();
        assertTrue(must.stream().anyMatch(q -> q.isTerm() && "status".equals(q.term().field())));
        assertTrue(must.stream().anyMatch(q -> q.isMatch() && "title".equals(q.match().field())));
    }

    @Test
    void suggestShouldReturnEmptyListWhenEsFails() {
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class)))
                .thenThrow(new RuntimeException("ES down"));

        HouseSuggestReqDTO req = new HouseSuggestReqDTO();
        req.setKeyword("abc");
        req.setSize(5);

        List<HouseSuggestItemVO> result = houseService.suggest(req);
        assertTrue(result.isEmpty());
        verify(elasticsearchOperations).search(any(NativeQuery.class), eq(HouseDoc.class));
    }
}

package cn.yy.myrent.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.service.smartguide.SmartGuideRecommendationService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class HouseServiceImpl extends ServiceImpl<HouseMapper, House> implements IHouseService {

    private static final Logger log = LoggerFactory.getLogger(HouseServiceImpl.class);

    private static final long ES_QUERY_TIMEOUT_MS = 1200;
    private static final int HOUSE_STATUS_AVAILABLE = 1;

    private static final String FALLBACK_SOURCE_ES = "ES";
    private static final String FALLBACK_SOURCE_REDIS_HOT = "REDIS_HOT";
    private static final String FALLBACK_SOURCE_DB_HOT = "DB_HOT";

    private static final String TIP_ES_DOWN = "附近房源加载异常，已为你展示热门房源";
    private static final String TIP_OUT_OF_RANGE = "当前范围内暂无可租房源";

    private final ElasticsearchOperations elasticsearchOperations;
    private final StringRedisTemplate stringRedisTemplate;
    private final SmartGuideRecommendationService smartGuideRecommendationService;
    private final HouseHotService houseHotService;
    private final LocationResolveService locationResolveService;

    @Override
    public HouseSearchResultVO searchNearbyHouse(SearchHouseReqDTO reqDTO) {
        SearchPoint searchPoint = resolveSearchPoint(reqDTO);
        double lat = searchPoint.latitude();
        double lon = searchPoint.longitude();
        double radiusMeters = parseRadiusMeters(reqDTO.getRadius());
        String distanceStr = ((int) radiusMeters) + "m";
        String city = reqDTO.getCity();

        int pageIndex = (reqDTO.getPage() != null ? reqDTO.getPage() : 1) - 1;
        int pageSize = reqDTO.getSize() != null ? reqDTO.getSize() : 10;

        try {
            List<HouseVO> esResult = CompletableFuture
                    .supplyAsync(() -> searchInEs(lat, lon, distanceStr, pageIndex, pageSize))
                    .get(ES_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (esResult.isEmpty()) {
                log.info("ES nearby search finished but no house matched, lat={}, lon={}, radius={}m, pageIndex={}, pageSize={}",
                        lat, lon, radiusMeters, pageIndex, pageSize);
                return buildSearchResult(esResult, false, FALLBACK_SOURCE_ES, TIP_OUT_OF_RANGE);
            }
            return buildSearchResult(esResult, false, FALLBACK_SOURCE_ES, null);
        } catch (TimeoutException te) {
            log.warn("ES nearby search timed out ({}ms), fallback strategy enabled, lat={}, lon={}, radius={}m",
                    ES_QUERY_TIMEOUT_MS, lat, lon, radiusMeters);
        } catch (Exception e) {
            log.error("ES nearby search failed, fallback strategy enabled, lat={}, lon={}, radius={}m",
                    lat, lon, radiusMeters, e);
        }

        return searchWhenEsUnavailable(city, pageIndex, pageSize);
    }

    private SearchPoint resolveSearchPoint(SearchHouseReqDTO reqDTO) {
        if (reqDTO.getLatitude() != null && reqDTO.getLongitude() != null) {
            return new SearchPoint(reqDTO.getLatitude(), reqDTO.getLongitude());
        }
        if (StringUtils.hasText(reqDTO.getLocationName())) {
            LocationResolveService.ResolvedLocation resolvedLocation =
                    locationResolveService.resolveRequired(reqDTO.getLocationName());
            return new SearchPoint(resolvedLocation.latitude(), resolvedLocation.longitude());
        }
        throw new IllegalArgumentException("latitude/longitude or locationName is required");
    }

    @Override
    public HouseSearchResultVO hotHouses(Integer page, Integer size) {
        int pageIndex = (page != null ? page : 1) - 1;
        int pageSize = size != null ? size : 10;
        try {
            List<HouseVO> hotHouses = searchHotFromRedis(null, pageIndex, pageSize);
            return buildSearchResult(hotHouses, false, FALLBACK_SOURCE_REDIS_HOT, null);
        } catch (Exception e) {
            log.error("hot-house query via Redis failed, fallback to DB, pageIndex={}, pageSize={}", pageIndex, pageSize, e);
            return buildSearchResult(searchHotFromDb(pageIndex, pageSize), false, FALLBACK_SOURCE_DB_HOT, null);
        }
    }

    @Override
    public SmartGuideResultVO smartGuide(SmartGuideReqDTO reqDTO) {
        return smartGuideRecommendationService.recommend(reqDTO);
    }

    private HouseSearchResultVO searchWhenEsUnavailable(String city, int pageIndex, int pageSize) {
        try {
            List<HouseVO> redisRecommended = searchHotFromRedis(city, pageIndex, pageSize);
            if (!redisRecommended.isEmpty()) {
                log.info("ES unavailable, Redis hot fallback hit, city={}, pageIndex={}, pageSize={}, count={}",
                        city, pageIndex, pageSize, redisRecommended.size());
                return buildSearchResult(redisRecommended, true, FALLBACK_SOURCE_REDIS_HOT, TIP_ES_DOWN);
            }
            log.warn("ES unavailable, Redis hot fallback returned empty, city={}, pageIndex={}, pageSize={}",
                    city, pageIndex, pageSize);
        } catch (Exception e) {
            log.error("ES unavailable, Redis hot fallback failed, city={}, pageIndex={}, pageSize={}",
                    city, pageIndex, pageSize, e);
        }

        List<HouseVO> dbRecommended = searchHotFromDb(pageIndex, pageSize);
        return buildSearchResult(dbRecommended, true, FALLBACK_SOURCE_DB_HOT, TIP_ES_DOWN);
    }

    private HouseSearchResultVO buildSearchResult(List<HouseVO> houses,
                                                  boolean esDown,
                                                  String fallbackSource,
                                                  String tipMessage) {
        HouseSearchResultVO result = new HouseSearchResultVO();
        result.setHouses(houses);
        result.setEsDown(esDown);
        result.setFallbackSource(fallbackSource);
        result.setTipMessage(tipMessage);
        return result;
    }

    private List<HouseVO> searchInEs(double lat, double lon, String distanceStr, int pageIndex, int pageSize) {
        Query boolQuery = Query.of(q -> q.bool(b -> b
                .must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)))
                .filter(f -> f.geoDistance(g -> g
                        .field("location")
                        .distance(distanceStr)
                        .location(loc -> loc.latlon(ll -> ll.lat(lat).lon(lon)))
                ))
        ));
        SortOptions geoSort = SortOptions.of(s -> s.geoDistance(g -> g
                .field("location")
                .location(loc -> loc.latlon(ll -> ll.lat(lat).lon(lon)))
                .order(SortOrder.Asc)
                .unit(DistanceUnit.Meters)
        ));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolQuery)
                .withSort(geoSort)
                .withPageable(PageRequest.of(pageIndex, pageSize))
                .build();
        SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);

        List<HouseVO> voList = new ArrayList<>();
        for (SearchHit<HouseDoc> hit : hits) {
            HouseDoc doc = hit.getContent();
            HouseVO vo = convertDocToVo(doc);
            Object[] sortValues = hit.getSortValues().toArray();
            if (sortValues.length > 0) {
                double distanceInMeters = (Double) sortValues[0];
                vo.setDistance(formatDistance(distanceInMeters));
            }
            voList.add(vo);
        }

        log.info("ES nearby search success, count={}, pageIndex={}, pageSize={}", voList.size(), pageIndex, pageSize);
        return voList;
    }

    private List<HouseVO> searchHotFromRedis(String city, int pageIndex, int pageSize) {
        if (stringRedisTemplate.getConnectionFactory() == null) {
            throw new IllegalStateException("Redis connection factory is not configured");
        }

        if (!houseHotService.hasHotRankingCache()) {
            log.info("hot ranking cache is empty, trigger rebuild, city={}, pageIndex={}, pageSize={}",
                    city, pageIndex, pageSize);
            houseHotService.rebuildHotRanking();
        }

        List<HouseVO> hotHouses = houseHotService.queryHotHouses(pageIndex, pageSize);
        log.info("Redis hot-house query finished, city={}, pageIndex={}, pageSize={}, count={}",
                city, pageIndex, pageSize, hotHouses.size());
        return hotHouses;
    }

    private List<HouseVO> searchHotFromDb(int pageIndex, int pageSize) {
        Page<House> page = new Page<>(pageIndex + 1L, pageSize);
        Page<House> housePage = this.lambdaQuery()
                .eq(House::getStatus, HOUSE_STATUS_AVAILABLE)
                .orderByDesc(House::getCreateTime)
                .orderByDesc(House::getId)
                .page(page);

        List<HouseVO> voList = new ArrayList<>();
        for (House house : housePage.getRecords()) {
            voList.add(convertHouseToVo(house));
        }

        log.info("DB hot fallback finished, pageIndex={}, pageSize={}, count={}",
                pageIndex, pageSize, voList.size());
        return voList;
    }

    private HouseVO convertDocToVo(HouseDoc doc) {
        HouseVO vo = new HouseVO();
        vo.setId(doc.getId());
        vo.setPublisherUserId(doc.getPublisherUserId());
        vo.setTitle(doc.getTitle());
        vo.setStatus(doc.getStatus());
        if (doc.getPrice() != null) {
            BigDecimal priceYuan = BigDecimal.valueOf(doc.getPrice())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            vo.setPrice(priceYuan);
        }
        if (doc.getDepositAmount() != null) {
            BigDecimal depositYuan = BigDecimal.valueOf(doc.getDepositAmount())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            vo.setDepositAmount(depositYuan);
        }
        return vo;
    }

    private HouseVO convertHouseToVo(House house) {
        HouseVO vo = new HouseVO();
        vo.setId(house.getId());
        vo.setPublisherUserId(house.getPublisherUserId());
        vo.setTitle(house.getTitle());
        vo.setStatus(house.getStatus());
        if (house.getPrice() != null) {
            BigDecimal priceYuan = BigDecimal.valueOf(house.getPrice())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            vo.setPrice(priceYuan);
        }
        if (house.getDepositAmount() != null) {
            BigDecimal depositYuan = BigDecimal.valueOf(house.getDepositAmount())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            vo.setDepositAmount(depositYuan);
        }
        return vo;
    }

    private double parseRadiusMeters(String radiusStr) {
        if (radiusStr == null || radiusStr.isEmpty()) {
            return 5000D;
        }
        String lower = radiusStr.toLowerCase().trim();
        if (lower.endsWith("km")) {
            String number = lower.substring(0, lower.length() - 2);
            return Double.parseDouble(number) * 1000;
        }
        return Double.parseDouble(lower);
    }

    private String formatDistance(double meters) {
        if (meters < 1000) {
            return (int) meters + "m";
        }
        BigDecimal km = BigDecimal.valueOf(meters).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
        return km + "km";
    }

    private record SearchPoint(double latitude, double longitude) {
    }
}

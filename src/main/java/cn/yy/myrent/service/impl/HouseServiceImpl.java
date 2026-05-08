package cn.yy.myrent.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.HouseListFilterReqDTO;
import cn.yy.myrent.dto.HouseSuggestReqDTO;
import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.service.smartguide.SmartGuideRecommendationService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseSuggestItemVO;
import cn.yy.myrent.vo.HouseVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

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
    private static final String FILTER_SOURCE_ES = "ES_FILTER";
    private static final String FILTER_SOURCE_DB = "DB_FILTER";

    private static final String TIP_ES_DOWN = "附近房源加载异常，已为你展示热门房源";
    private static final String TIP_OUT_OF_RANGE = "当前范围内暂无可租房源";

    private final ElasticsearchOperations elasticsearchOperations;
    private final StringRedisTemplate stringRedisTemplate;
    private final SmartGuideRecommendationService smartGuideRecommendationService;
    private final HouseHotService houseHotService;
    private final LocationResolveService locationResolveService;
    private final IUserService userService;

    @Override
    public List<HouseSuggestItemVO> suggest(HouseSuggestReqDTO reqDTO) {
        String keyword = reqDTO == null ? null : reqDTO.getKeyword();
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        int size = 5;
        if (reqDTO != null && reqDTO.getSize() != null) {
            size = reqDTO.getSize();
        }
        size = Math.min(Math.max(size, 1), 5);

        try {
            Query boolQuery = Query.of(q -> q.bool(b -> b
                    .must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)))
                    .must(m -> m.match(mm -> mm.field("title").query(keyword)))
            ));

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(boolQuery)
                    .withPageable(PageRequest.of(0, size))
                    .build();

            SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
            List<HouseSuggestItemVO> result = new ArrayList<>();
            for (SearchHit<HouseDoc> hit : hits) {
                HouseDoc doc = hit.getContent();
                if (doc == null) {
                    continue;
                }
                result.add(new HouseSuggestItemVO(doc.getId(), doc.getTitle(), centsToYuan(doc.getPrice())));
            }
            return result;
        } catch (Exception e) {
            log.warn("ES suggest failed, keyword={}, size={}", keyword, size, e);
            return List.of();
        }
    }

    private static Integer centsToYuan(Integer cents) {
        if (cents == null) {
            return null;
        }
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .intValue();
    }

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
                return buildSearchResult(esResult, null, false, FALLBACK_SOURCE_ES, TIP_OUT_OF_RANGE);
            }
            return buildSearchResult(esResult, null, false, FALLBACK_SOURCE_ES, null);
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
    public HouseSearchResultVO hotHouses(String city, Integer page, Integer size) {
        int pageIndex = (page != null ? page : 1) - 1;
        int pageSize = size != null ? size : 10;
        try {
            List<HouseVO> hotHouses = searchHotFromRedis(city, pageIndex, pageSize);
            return buildSearchResult(hotHouses, null, false, FALLBACK_SOURCE_REDIS_HOT, null);
        } catch (Exception e) {
            log.error("hot-house query via Redis failed, fallback to DB, city={}, pageIndex={}, pageSize={}",
                    city, pageIndex, pageSize, e);
            return buildSearchResult(searchHotFromDb(city, pageIndex, pageSize), null, false, FALLBACK_SOURCE_DB_HOT, null);
        }
    }

    @Override
    public SmartGuideResultVO smartGuide(SmartGuideReqDTO reqDTO) {
        return smartGuideRecommendationService.recommend(reqDTO);
    }

    @Override
    public HouseSearchResultVO filterList(HouseListFilterReqDTO reqDTO) {
        int page = reqDTO == null || reqDTO.getPage() == null ? 1 : reqDTO.getPage();
        int size = reqDTO == null || reqDTO.getSize() == null ? 8 : reqDTO.getSize();
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 50);
        int pageIndex = page - 1;
        int offset = (page - 1) * size;

        log.info("根据条件筛选房源，优先查询 ES");
        try {
            PagedHouseResult houses = searchFilterInEs(reqDTO, pageIndex, size);
            return buildSearchResult(houses.houses(), houses.total(), false, FILTER_SOURCE_ES, null);
        } catch (Exception e) {
            log.warn("ES filter query failed, fallback to DB, page={}, size={}", page, size, e);
        }

        List<HouseVO> dbHouses = filterListFromDb(reqDTO, offset, size);
        return buildSearchResult(dbHouses, countFilteredHousesInDb(reqDTO), true, FILTER_SOURCE_DB, null);
    }

    private HouseSearchResultVO searchWhenEsUnavailable(String city, int pageIndex, int pageSize) {
        try {
            List<HouseVO> redisRecommended = searchHotFromRedis(city, pageIndex, pageSize);
            if (!redisRecommended.isEmpty()) {
                log.info("ES unavailable, Redis hot fallback hit, city={}, pageIndex={}, pageSize={}, count={}",
                        city, pageIndex, pageSize, redisRecommended.size());
                return buildSearchResult(redisRecommended, null, true, FALLBACK_SOURCE_REDIS_HOT, TIP_ES_DOWN);
            }
            log.warn("ES unavailable, Redis hot fallback returned empty, city={}, pageIndex={}, pageSize={}",
                    city, pageIndex, pageSize);
        } catch (Exception e) {
            log.error("ES unavailable, Redis hot fallback failed, city={}, pageIndex={}, pageSize={}",
                    city, pageIndex, pageSize, e);
        }

        List<HouseVO> dbRecommended = searchHotFromDb(city, pageIndex, pageSize);
        return buildSearchResult(dbRecommended, null, true, FALLBACK_SOURCE_DB_HOT, TIP_ES_DOWN);
    }

    private HouseSearchResultVO buildSearchResult(List<HouseVO> houses,
                                                  Long total,
                                                  boolean esDown,
                                                  String fallbackSource,
                                                  String tipMessage) {
        HouseSearchResultVO result = new HouseSearchResultVO();
        result.setTotal(total);
        result.setHouses(enrichPublisherNamesSafely(houses));
        result.setEsDown(esDown);
        result.setFallbackSource(fallbackSource);
        result.setTipMessage(tipMessage);
        return result;
    }

    private List<HouseVO> enrichPublisherNamesSafely(List<HouseVO> houses) {
        if (houses == null || houses.isEmpty()) {
            return houses;
        }

        try {
            return enrichPublisherNames(houses);
        } catch (Exception e) {
            log.error("batch enrich publisher names failed, houseCount={}", houses.size(), e);
            fillUnknownPublisherNames(houses);
            return houses;
        }
    }

    private List<HouseVO> enrichPublisherNames(List<HouseVO> houses) {
        List<Long> publisherUserIds = houses.stream()
                .map(HouseVO::getPublisherUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (publisherUserIds.isEmpty()) {
            fillUnknownPublisherNames(houses);
            return houses;
        }

        Map<Long, String> userNameMap = userService.listByIds(publisherUserIds).stream()
                .filter(Objects::nonNull)
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(
                        User::getId,
                        user -> StringUtils.hasText(user.getName()) ? user.getName() : "未知发布人",
                        (left, right) -> left
                ));

        for (HouseVO house : houses) {
            if (house == null) {
                continue;
            }
            Long publisherUserId = house.getPublisherUserId();
            if (publisherUserId == null) {
                house.setPublisherName("未知发布人");
                continue;
            }
            house.setPublisherName(userNameMap.getOrDefault(publisherUserId, "未知发布人"));
        }
        return houses;
    }

    private void fillUnknownPublisherNames(List<HouseVO> houses) {
        for (HouseVO house : houses) {
            if (house != null && !StringUtils.hasText(house.getPublisherName())) {
                house.setPublisherName("未知发布人");
            }
        }
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

    private PagedHouseResult searchFilterInEs(HouseListFilterReqDTO reqDTO, int pageIndex, int pageSize) {
        Integer minPriceCent = yuanToCent(reqDTO == null ? null : reqDTO.getMinPriceYuan());
        Integer maxPriceCent = yuanToCent(reqDTO == null ? null : reqDTO.getMaxPriceYuan());

        Query boolQuery = Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)));
            if (reqDTO != null) {
                if (StringUtils.hasText(reqDTO.getCity())) {
                    b.filter(f -> f.term(t -> t.field("city").value(reqDTO.getCity())));
                }
                if (StringUtils.hasText(reqDTO.getRegion())) {
                    b.filter(f -> f.term(t -> t.field("region").value(reqDTO.getRegion())));
                }
                if (reqDTO.getRentType() != null) {
                    b.filter(f -> f.term(t -> t.field("rentType").value(reqDTO.getRentType())));
                }
                if (minPriceCent != null) {
                    b.filter(f -> f.range(r -> r.number(n -> n.field("price").gte((double) minPriceCent))));
                }
                if (maxPriceCent != null) {
                    b.filter(f -> f.range(r -> r.number(n -> n.field("price").lte((double) maxPriceCent))));
                }
                if (Boolean.TRUE.equals(reqDTO.getNearSubway())) {
                    b.filter(f -> f.term(t -> t.field("nearSubway").value(true)));
                }
                if (Boolean.TRUE.equals(reqDTO.getPrivateBathroom())) {
                    b.filter(f -> f.term(t -> t.field("privateBathroom").value(true)));
                }
                if (Boolean.TRUE.equals(reqDTO.getHasBalcony())) {
                    b.filter(f -> f.term(t -> t.field("hasBalcony").value(true)));
                }
                if (Boolean.TRUE.equals(reqDTO.getCivilWaterElectric())) {
                    b.filter(f -> f.term(t -> t.field("civilWaterElectric").value(true)));
                }
                if (Boolean.TRUE.equals(reqDTO.getSupportStudentDepositFree())) {
                    b.filter(f -> f.term(t -> t.field("supportStudentDepositFree").value(true)));
                }
            }
            return b;
        }));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolQuery)
                .withSort(SortOptions.of(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc))))
                .withSort(SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Desc))))
                .withPageable(PageRequest.of(pageIndex, pageSize))
                .build();

        SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
        List<HouseVO> voList = new ArrayList<>();
        for (SearchHit<HouseDoc> hit : hits) {
            HouseDoc doc = hit.getContent();
            if (doc != null) {
                voList.add(convertDocToVo(doc));
            }
        }
        log.info("ES filter query success, count={}, pageIndex={}, pageSize={}", voList.size(), pageIndex, pageSize);
        return new PagedHouseResult(voList, hits.getTotalHits());
    }

    private List<HouseVO> searchHotFromRedis(String city, int pageIndex, int pageSize) {
        if (stringRedisTemplate.getConnectionFactory() == null) {
            throw new IllegalStateException("Redis connection factory is not configured");
        }

        if (!houseHotService.hasHotRankingCache(city)) {
            log.info("hot ranking cache is empty, trigger rebuild, city={}, pageIndex={}, pageSize={}",
                    city, pageIndex, pageSize);
            houseHotService.rebuildHotRanking(city);
        }

        List<HouseVO> hotHouses = houseHotService.queryHotHouses(city, pageIndex, pageSize);
        log.info("Redis hot-house query finished, city={}, pageIndex={}, pageSize={}, count={}",
                city, pageIndex, pageSize, hotHouses.size());
        return hotHouses;
    }

    private List<HouseVO> searchHotFromDb(String city, int pageIndex, int pageSize) {
        Page<House> page = new Page<>(pageIndex + 1L, pageSize);
        var query = this.lambdaQuery()
                .eq(House::getStatus, HOUSE_STATUS_AVAILABLE);
        if (StringUtils.hasText(city)) {
            query.eq(House::getCity, city);
        }
        Page<House> housePage = query
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

    private List<HouseVO> filterListFromDb(HouseListFilterReqDTO reqDTO, int offset, int size) {
        List<House> records = baseMapper.selectListFilterPage(
                reqDTO == null ? null : reqDTO.getCity(),
                reqDTO == null ? null : reqDTO.getRegion(),
                reqDTO == null ? null : reqDTO.getRentType(),
                yuanToCent(reqDTO == null ? null : reqDTO.getMinPriceYuan()),
                yuanToCent(reqDTO == null ? null : reqDTO.getMaxPriceYuan()),
                reqDTO == null ? null : reqDTO.getNearSubway(),
                reqDTO == null ? null : reqDTO.getPrivateBathroom(),
                reqDTO == null ? null : reqDTO.getHasBalcony(),
                reqDTO == null ? null : reqDTO.getCivilWaterElectric(),
                reqDTO == null ? null : reqDTO.getSupportStudentDepositFree(),
                offset,
                size
        );

        List<HouseVO> houses = new ArrayList<>();
        for (House house : records) {
            houses.add(convertHouseToVo(house));
        }
        return houses;
    }

    private long countFilteredHousesInDb(HouseListFilterReqDTO reqDTO) {
        QueryWrapper<House> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", HOUSE_STATUS_AVAILABLE);
        if (reqDTO == null) {
            return baseMapper.selectCount(queryWrapper);
        }
        if (StringUtils.hasText(reqDTO.getCity())) {
            queryWrapper.eq("city", reqDTO.getCity());
        }
        if (StringUtils.hasText(reqDTO.getRegion())) {
            queryWrapper.eq("region", reqDTO.getRegion());
        }
        if (reqDTO.getRentType() != null) {
            queryWrapper.eq("rent_type", reqDTO.getRentType());
        }
        Integer minPriceCent = yuanToCent(reqDTO.getMinPriceYuan());
        if (minPriceCent != null) {
            queryWrapper.ge("price", minPriceCent);
        }
        Integer maxPriceCent = yuanToCent(reqDTO.getMaxPriceYuan());
        if (maxPriceCent != null) {
            queryWrapper.le("price", maxPriceCent);
        }
        if (Boolean.TRUE.equals(reqDTO.getNearSubway())) {
            queryWrapper.eq("near_subway", 1);
        }
        if (Boolean.TRUE.equals(reqDTO.getPrivateBathroom())) {
            queryWrapper.eq("private_bathroom", 1);
        }
        if (Boolean.TRUE.equals(reqDTO.getHasBalcony())) {
            queryWrapper.eq("has_balcony", 1);
        }
        if (Boolean.TRUE.equals(reqDTO.getCivilWaterElectric())) {
            queryWrapper.eq("civil_water_electric", 1);
        }
        if (Boolean.TRUE.equals(reqDTO.getSupportStudentDepositFree())) {
            queryWrapper.eq("support_student_deposit_free", 1);
        }
        return baseMapper.selectCount(queryWrapper);
    }

    private HouseVO convertDocToVo(HouseDoc doc) {
        HouseVO vo = new HouseVO();
        vo.setId(doc.getId());
        vo.setPublisherUserId(doc.getPublisherUserId());
        vo.setTitle(doc.getTitle());
        vo.setCity(doc.getCity());
        vo.setRegion(doc.getRegion());
        vo.setNearSubway(Boolean.TRUE.equals(doc.getNearSubway()));
        vo.setPrivateBathroom(Boolean.TRUE.equals(doc.getPrivateBathroom()));
        vo.setHasBalcony(Boolean.TRUE.equals(doc.getHasBalcony()));
        vo.setCivilWaterElectric(Boolean.TRUE.equals(doc.getCivilWaterElectric()));
        vo.setSupportStudentDepositFree(Boolean.TRUE.equals(doc.getSupportStudentDepositFree()));
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
        vo.setCity(house.getCity());
        vo.setRegion(house.getRegion());
        vo.setNearSubway(house.getNearSubway() != null && house.getNearSubway() == 1);
        vo.setPrivateBathroom(house.getPrivateBathroom() != null && house.getPrivateBathroom() == 1);
        vo.setHasBalcony(house.getHasBalcony() != null && house.getHasBalcony() == 1);
        vo.setCivilWaterElectric(house.getCivilWaterElectric() != null && house.getCivilWaterElectric() == 1);
        vo.setSupportStudentDepositFree(house.getSupportStudentDepositFree() != null
                && house.getSupportStudentDepositFree() == 1);
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

    private Integer yuanToCent(Integer yuan) {
        if (yuan == null) {
            return null;
        }
        return Math.max(yuan, 0) * 100;
    }

    private record SearchPoint(double latitude, double longitude) {
    }

    private record PagedHouseResult(List<HouseVO> houses, long total) {
    }
}

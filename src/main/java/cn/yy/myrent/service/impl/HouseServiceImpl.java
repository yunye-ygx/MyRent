package cn.yy.myrent.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.score.SmartGuideScoreCalculator;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseVO;
import cn.yy.myrent.vo.SmartGuideItemVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class HouseServiceImpl extends ServiceImpl<HouseMapper, House> implements IHouseService {

    private static final Logger log = LoggerFactory.getLogger(HouseServiceImpl.class);

    private static final long ES_QUERY_TIMEOUT_MS = 1200;

    private static final String FALLBACK_SOURCE_ES = "ES";
    private static final String FALLBACK_SOURCE_REDIS_HOT = "REDIS_HOT";
    private static final String FALLBACK_SOURCE_DB_CITY_HOT = "DB_CITY_HOT";

    private static final String BUDGET_SCOPE_RENT_ONLY = "RENT_ONLY";
    private static final String BUDGET_SCOPE_TOTAL = "TOTAL";
    private static final String RENT_MODE_WHOLE = "WHOLE";
    private static final String RENT_MODE_SHARED = "SHARED";
    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final int HOUSE_STATUS_LOCKED = 2;
    private static final int SMART_GUIDE_MAX_CANDIDATES = 200;
    private static final int SMART_GUIDE_ES_PREFILTER_SIZE = 300;
    private static final int RELAXED_BUDGET_DELTA_YUAN = 500;

    private static final String TIP_ES_DOWN = "附近房源加载异常，已为你展示推荐房源";
    private static final String TIP_OUT_OF_RANGE = "当前房源不在搜索范围内";
    private static final String TIP_SMART_GUIDE_ES_DEGRADED = "ES预筛选暂不可用，已降级到DB方案，状态和价格均以DB为准";

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SmartGuideScoreCalculator smartGuideScoreCalculator;

    @Override
    public HouseSearchResultVO searchNearbyHouse(SearchHouseReqDTO reqDTO) {
        double lat = reqDTO.getLatitude();
        double lon = reqDTO.getLongitude();
        double radiusMeters = parseRadiusMeters(reqDTO.getRadius());
        String distanceStr = ((int) radiusMeters) + "m";
        String city = reqDTO.getCity();

        int pageIndex = (reqDTO.getPage() != null ? reqDTO.getPage() : 1) - 1;
        int pageSize = reqDTO.getSize() != null ? reqDTO.getSize() : 10;

        try {
            List<HouseVO> esResult = CompletableFuture.supplyAsync(() -> searchInEs(lat, lon, distanceStr, pageIndex, pageSize))
                    .get(ES_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (esResult.isEmpty()) {
                log.info("ES搜索成功但范围内无房源，lat={}, lon={}, radius={}m, pageIndex={}, pageSize={}",
                        lat,
                        lon,
                        radiusMeters,
                        pageIndex,
                        pageSize);
                return buildSearchResult(esResult, false, FALLBACK_SOURCE_ES, TIP_OUT_OF_RANGE);
            }
            return buildSearchResult(esResult, false, FALLBACK_SOURCE_ES, null);
        } catch (TimeoutException te) {
            log.warn("ES搜索超时({}ms)，进入兜底策略，lat={}, lon={}, radius={}m", ES_QUERY_TIMEOUT_MS, lat, lon, radiusMeters);
        } catch (Exception e) {
            log.error("ES搜索异常，进入兜底策略，lat={}, lon={}, radius={}m", lat, lon, radiusMeters, e);
        }

        return searchWhenEsUnavailable(city, pageIndex, pageSize);
    }

    @Override
    public SmartGuideResultVO smartGuide(SmartGuideReqDTO reqDTO) {
        validateSmartGuideReq(reqDTO);

        int page = reqDTO.getPage() == null ? 1 : reqDTO.getPage();
        int size = reqDTO.getSize() == null ? 10 : reqDTO.getSize();
        int budgetCent = reqDTO.getBudgetYuan() * 100;
        SmartGuidePrefilterResult prefilterResult = querySmartGuideCandidateIdsFromEs(reqDTO);

        List<House> exactCandidates = querySmartGuideCandidatesFromDb(reqDTO, budgetCent, prefilterResult.getCandidateIds());
        if (!prefilterResult.isEsAvailable()) {
            exactCandidates = querySmartGuideCandidatesFallback(reqDTO, budgetCent);
        }

        SmartGuideResultVO result = new SmartGuideResultVO();
        result.setOriginalBudgetYuan(reqDTO.getBudgetYuan());

        List<House> candidates = exactCandidates;
        int scoredBudgetYuan = reqDTO.getBudgetYuan();
        if (exactCandidates.isEmpty()) {
            int relaxedBudgetYuan = reqDTO.getBudgetYuan() + RELAXED_BUDGET_DELTA_YUAN;
            candidates = querySmartGuideCandidatesFromDb(reqDTO, relaxedBudgetYuan * 100, prefilterResult.getCandidateIds());
            if (!prefilterResult.isEsAvailable()) {
                candidates = querySmartGuideCandidatesFallback(reqDTO, relaxedBudgetYuan * 100);
            }
            scoredBudgetYuan = relaxedBudgetYuan;
            result.setRelaxedBudget(Boolean.TRUE);
            result.setRelaxedBudgetYuan(relaxedBudgetYuan);
            result.setTipMessage("No exact result, budget +500 recommendations are shown.");
        } else {
            result.setRelaxedBudget(Boolean.FALSE);
            result.setTipMessage(prefilterResult.isEsAvailable()
                    ? "Matched listings found and ranked by score."
                    : TIP_SMART_GUIDE_ES_DEGRADED);
        }

        final int finalScoredBudgetYuan = scoredBudgetYuan;
        final String rentKeyword = RENT_MODE_WHOLE.equals(normalizeEnumValue(reqDTO.getRentMode())) ? "整租" : "合租";
        List<SmartGuideItemVO> ranked = candidates.stream()
                .map(house -> buildSmartGuideItem(house, reqDTO, finalScoredBudgetYuan, rentKeyword))
                .sorted(Comparator.comparing(SmartGuideItemVO::getScore).reversed())
                .collect(Collectors.toList());

        int start = Math.max((page - 1) * size, 0);
        if (start >= ranked.size()) {
            result.setRecommendations(new ArrayList<>());
            return result;
        }
        int end = Math.min(start + size, ranked.size());
        result.setRecommendations(ranked.subList(start, end));
        if (!prefilterResult.isEsAvailable()) {
            result.setTipMessage(TIP_SMART_GUIDE_ES_DEGRADED);
        }
        return result;
    }

    private HouseSearchResultVO searchWhenEsUnavailable(String city, int pageIndex, int pageSize) {
        try {
            List<HouseVO> redisRecommended = searchHotFromRedis(city, pageIndex, pageSize);
            if (!redisRecommended.isEmpty()) {
                log.info("ES异常时Redis热门推荐成功，city={}, pageIndex={}, pageSize={}, count={}",
                        city,
                        pageIndex,
                        pageSize,
                        redisRecommended.size());
                return buildSearchResult(redisRecommended, true, FALLBACK_SOURCE_REDIS_HOT, TIP_ES_DOWN);
            }
            log.warn("ES异常时Redis热门推荐为空，city={}, pageIndex={}, pageSize={}", city, pageIndex, pageSize);
        } catch (Exception e) {
            log.error("ES异常时Redis热门推荐失败，city={}, pageIndex={}, pageSize={}", city, pageIndex, pageSize, e);
        }

        List<HouseVO> dbRecommended = searchHotFromDbByCity(city, pageIndex, pageSize);
        return buildSearchResult(dbRecommended, true, FALLBACK_SOURCE_DB_CITY_HOT, TIP_ES_DOWN);
    }

    private HouseSearchResultVO buildSearchResult(List<HouseVO> houses, boolean esDown, String fallbackSource, String tipMessage) {
        HouseSearchResultVO result = new HouseSearchResultVO();
        result.setHouses(houses);
        result.setEsDown(esDown);
        result.setFallbackSource(fallbackSource);
        result.setTipMessage(tipMessage);
        return result;
    }

    //查询ES返回房源
    private List<HouseVO> searchInEs(double lat, double lon, String distanceStr, int pageIndex, int pageSize) {
        Query boolQuery = Query.of(q -> q.bool(b -> b
                .must(m -> m.term(t -> t.field("status").value(1)))
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
        log.info("ES搜索成功，返回{}条，pageIndex={}, pageSize={}", voList.size(), pageIndex, pageSize);
        return voList;
    }

    private List<HouseVO> searchHotFromRedis(String city, int pageIndex, int pageSize) {
        String cityKey = StringUtils.hasText(city) ? city.trim() : "DEFAULT_CITY";
        String cacheKey = "house:hot:" + cityKey;

        // TODO 热门房源计算能力完成后，从Redis中按热度读取城市热门房源并组装 HouseVO。
        log.warn("Redis热门房源查询逻辑待实现，cacheKey={}, pageIndex={}, pageSize={}", cacheKey, pageIndex, pageSize);

        if (stringRedisTemplate.getConnectionFactory() == null) {
            throw new IllegalStateException("Redis连接工厂不存在");
        }
        return new ArrayList<>();
    }

    private List<HouseVO> searchHotFromDbByCity(String city, int pageIndex, int pageSize) {
        Page<House> page = new Page<>(pageIndex + 1L, pageSize);
        Page<House> housePage = this.lambdaQuery()
                .eq(House::getStatus, 1)
                .like(StringUtils.hasText(city), House::getTitle, city)
                .orderByDesc(House::getId)
                .page(page);

        List<HouseVO> voList = new ArrayList<>();
        for (House house : housePage.getRecords()) {
            voList.add(convertHouseToVo(house));
        }

        log.info("DB城市热门推荐完成，city={}, pageIndex={}, pageSize={}, count={}",
                city,
                pageIndex,
                pageSize,
                voList.size());
        return voList;
    }

    //校验传递的参数是否为空或者非法
    private void validateSmartGuideReq(SmartGuideReqDTO reqDTO) {
        String budgetScope = normalizeEnumValue(reqDTO.getBudgetScope());
        if (!BUDGET_SCOPE_RENT_ONLY.equals(budgetScope) && !BUDGET_SCOPE_TOTAL.equals(budgetScope)) {
            throw new IllegalArgumentException("budgetScope only supports RENT_ONLY or TOTAL");
        }

        String rentMode = normalizeEnumValue(reqDTO.getRentMode());
        if (!RENT_MODE_WHOLE.equals(rentMode) && !RENT_MODE_SHARED.equals(rentMode)) {
            throw new IllegalArgumentException("rentMode only supports WHOLE or SHARED");
        }

        boolean hasStationLat = reqDTO.getStationLatitude() != null;
        boolean hasStationLon = reqDTO.getStationLongitude() != null;
        if (hasStationLat != hasStationLon) {
            throw new IllegalArgumentException("stationLatitude and stationLongitude must be sent together");
        }
    }


    //快速从 es 中找出 标题中同时包含指定地铁站名和租房类型（整租/合租）的房源 ID 列表
    private SmartGuidePrefilterResult querySmartGuideCandidateIdsFromEs(SmartGuideReqDTO reqDTO) {
        String rentKeyword = RENT_MODE_WHOLE.equals(normalizeEnumValue(reqDTO.getRentMode())) ? "整租" : "合租";
        try {
            List<Long> candidateIds = CompletableFuture.supplyAsync(() -> {
                        Query boolQuery = Query.of(q -> q.bool(b -> b
                                .must(m -> m.match(mm -> mm.field("title").query(reqDTO.getCommuteMetroStation())))
                                .must(m -> m.match(mm -> mm.field("title").query(rentKeyword)))
                        ));

                        NativeQuery nativeQuery = NativeQuery.builder()
                                .withQuery(boolQuery)
                                .withPageable(PageRequest.of(0, SMART_GUIDE_ES_PREFILTER_SIZE))
                                .build();
                        SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
                        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
                        for (SearchHit<HouseDoc> hit : hits) {
                            HouseDoc doc = hit.getContent();
                            if (doc != null && doc.getId() != null) {
                                idSet.add(doc.getId());
                            }
                        }
                        return new ArrayList<>(idSet);
                    })
                    .get(ES_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            log.info("smartGuide ES预筛选完成，candidateCount={}", candidateIds.size());
            return SmartGuidePrefilterResult.esAvailable(candidateIds);
        } catch (TimeoutException te) {
            log.warn("smartGuide ES预筛选超时({}ms)，降级DB", ES_QUERY_TIMEOUT_MS);
        } catch (Exception e) {
            log.error("smartGuide ES预筛选异常，降级DB", e);
        }
        return SmartGuidePrefilterResult.esUnavailable();
    }

    //进行二次过滤筛选出可租的房源
    private List<House> querySmartGuideCandidatesFromDb(SmartGuideReqDTO reqDTO, int budgetCent, List<Long> esCandidateIds) {
        if (esCandidateIds == null || esCandidateIds.isEmpty()) {
            return Collections.emptyList();
        }
        String rentKeyword = RENT_MODE_WHOLE.equals(normalizeEnumValue(reqDTO.getRentMode())) ? "整租" : "合租";
        return this.lambdaQuery()
                .in(House::getId, esCandidateIds)
                .in(House::getStatus, HOUSE_STATUS_AVAILABLE, HOUSE_STATUS_LOCKED)
                .le(House::getPrice, budgetCent)
                .like(StringUtils.hasText(reqDTO.getCommuteMetroStation()), House::getTitle, reqDTO.getCommuteMetroStation())
                .like(House::getTitle, rentKeyword)
                .orderByAsc(House::getPrice)
                .last("limit " + SMART_GUIDE_MAX_CANDIDATES)
                .list();
    }

    private List<House> querySmartGuideCandidatesFallback(SmartGuideReqDTO reqDTO, int budgetCent) {
        String rentKeyword = RENT_MODE_WHOLE.equals(normalizeEnumValue(reqDTO.getRentMode())) ? "整租" : "合租";
        return this.lambdaQuery()
                .in(House::getStatus, HOUSE_STATUS_AVAILABLE, HOUSE_STATUS_LOCKED)
                .le(House::getPrice, budgetCent)
                .like(StringUtils.hasText(reqDTO.getCommuteMetroStation()), House::getTitle, reqDTO.getCommuteMetroStation())
                .like(House::getTitle, rentKeyword)
                .orderByAsc(House::getPrice)
                .last("limit " + SMART_GUIDE_MAX_CANDIDATES)
                .list();
    }

    private static class SmartGuidePrefilterResult {

        private final boolean esAvailable;

        private final List<Long> candidateIds;

        private SmartGuidePrefilterResult(boolean esAvailable, List<Long> candidateIds) {
            this.esAvailable = esAvailable;
            this.candidateIds = candidateIds;
        }

        static SmartGuidePrefilterResult esAvailable(List<Long> candidateIds) {
            return new SmartGuidePrefilterResult(true, candidateIds == null ? Collections.emptyList() : candidateIds);
        }

        static SmartGuidePrefilterResult esUnavailable() {
            return new SmartGuidePrefilterResult(false, Collections.emptyList());
        }

        boolean isEsAvailable() {
            return esAvailable;
        }

        List<Long> getCandidateIds() {
            return candidateIds;
        }
    }

    private SmartGuideItemVO buildSmartGuideItem(House house,
                                                 SmartGuideReqDTO reqDTO,
                                                 int scoredBudgetYuan,
                                                 String rentKeyword) {
        SmartGuideItemVO item = new SmartGuideItemVO();
        item.setHouseId(house.getId());
        item.setPublisherUserId(house.getPublisherUserId());
        item.setTitle(house.getTitle());
        item.setStatus(house.getStatus());
        item.setPrice(convertCentToYuan(house.getPrice()));

        SmartGuideScoreCalculator.SmartGuideScoreResult scoreResult =
                smartGuideScoreCalculator.calculate(house, reqDTO, scoredBudgetYuan * 100, rentKeyword);

        item.setDistanceToMetroKm(scoreResult.getDistanceToMetroKm());
        item.setEstimatedCommuteMinutes(scoreResult.getEstimatedCommuteMinutes());
        item.setReasons(scoreResult.getReasons());
        item.setScore(scoreResult.getScore());
        return item;
    }

    private String normalizeEnumValue(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal convertCentToYuan(Integer cent) {
        if (cent == null) {
            return null;
        }
        return new BigDecimal(cent).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    private HouseVO convertDocToVo(HouseDoc doc) {
        HouseVO vo = new HouseVO();
        vo.setId(doc.getId());
        vo.setPublisherUserId(doc.getPublisherUserId());
        vo.setTitle(doc.getTitle());
        vo.setStatus(doc.getStatus());
        if (doc.getPrice() != null) {
            BigDecimal priceYuan = new BigDecimal(doc.getPrice())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            vo.setPrice(priceYuan);
        }
        if (doc.getDepositAmount() != null) {
            BigDecimal depositYuan = new BigDecimal(doc.getDepositAmount())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
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
            BigDecimal priceYuan = new BigDecimal(house.getPrice())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            vo.setPrice(priceYuan);
        }
        if (house.getDepositAmount() != null) {
            BigDecimal depositYuan = new BigDecimal(house.getDepositAmount())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            vo.setDepositAmount(depositYuan);
        }
        return vo;
    }

    private double parseRadiusMeters(String radiusStr) {
        if (radiusStr == null || radiusStr.isEmpty()) {
            return 5000d;
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
        BigDecimal km = new BigDecimal(meters).divide(new BigDecimal(1000), 1, RoundingMode.HALF_UP);
        return km.toString() + "km";
    }
}


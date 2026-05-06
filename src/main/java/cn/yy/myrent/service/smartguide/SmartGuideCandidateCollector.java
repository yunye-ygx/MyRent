package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.location.LocationResolveService;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SmartGuideCandidateCollector {

    private static final Logger log = LoggerFactory.getLogger(SmartGuideCandidateCollector.class);

    private static final long ES_QUERY_TIMEOUT_MS = 1200;

    private static final String BUDGET_SCOPE_TOTAL = "TOTAL";
    private static final String RENT_MODE_WHOLE = "WHOLE";
    private static final String RENT_MODE_SHARED = "SHARED";
    private static final String RENT_KEYWORD_WHOLE = "整租";
    private static final String RENT_KEYWORD_SHARED = "合租";

    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final int SMART_GUIDE_MAX_CANDIDATES = 200;
    private static final int SMART_GUIDE_ES_PREFILTER_SIZE = 300;
    private static final int SMART_GUIDE_DB_FALLBACK_SCAN_SIZE = 500;
    private static final int DEFAULT_PREVIEW_SCAN_LIMIT = 24;
    private static final int RELAX_BUDGET_DELTA_FEW_RESULT_YUAN = 300;
    private static final int RELAX_BUDGET_DELTA_EMPTY_RESULT_YUAN = 500;
    private static final double DEFAULT_PREVIEW_RADIUS_KM = 5.0d;
    private static final double SEARCH_RADIUS_EXACT_KM = 3.0d;
    private static final double SEARCH_RADIUS_RELAXED_FEW_RESULT_KM = 5.0d;
    private static final double SEARCH_RADIUS_RELAXED_EMPTY_RESULT_KM = 8.0d;

    private final ElasticsearchOperations elasticsearchOperations;
    private final HouseMapper houseMapper;
    private final LocationResolveService locationResolveService;

    public SmartGuideCandidateBundle collect(SmartGuideCandidateQuery query) {
        ResolvedCandidateQuery resolvedQuery = resolveQuery(query);
        int limit = query.size() == null ? DEFAULT_PREVIEW_SCAN_LIMIT : query.size();
        StageSearchResult stageResult = searchStage(resolvedQuery, resolvedQuery.budgetYuan(), DEFAULT_PREVIEW_RADIUS_KM, false, limit);
        return new SmartGuideCandidateBundle(
                resolvedQuery.locationName(),
                resolvedQuery.targetLatitude(),
                resolvedQuery.targetLongitude(),
                stageResult.esAvailable(),
                stageResult.candidates()
        );
    }

    public RecommendationCollectionResult collectRecommendation(SmartGuideCandidateQuery query) {
        ResolvedCandidateQuery resolvedQuery = resolveQuery(query);
        int targetSize = query.size() == null ? SMART_GUIDE_MAX_CANDIDATES : query.size();

        StageSearchResult exactStage = searchStage(resolvedQuery, resolvedQuery.budgetYuan(), SEARCH_RADIUS_EXACT_KM, false, targetSize);
        if (exactStage.candidates().size() >= targetSize) {
            List<House> exactCandidates = limitCandidates(exactStage.candidates(), targetSize);
            SmartGuideCandidateBundle bundle = new SmartGuideCandidateBundle(
                    resolvedQuery.locationName(),
                    resolvedQuery.targetLatitude(),
                    resolvedQuery.targetLongitude(),
                    exactStage.esAvailable(),
                    exactCandidates
            );
            return new RecommendationCollectionResult(bundle, SmartGuideCandidateResult.exact(exactCandidates, resolvedQuery.budgetYuan()));
        }

        int relaxedBudgetDelta = exactStage.candidates().isEmpty()
                ? RELAX_BUDGET_DELTA_EMPTY_RESULT_YUAN
                : RELAX_BUDGET_DELTA_FEW_RESULT_YUAN;
        double relaxedRadiusKm = exactStage.candidates().isEmpty()
                ? SEARCH_RADIUS_RELAXED_EMPTY_RESULT_KM
                : SEARCH_RADIUS_RELAXED_FEW_RESULT_KM;
        int relaxedBudgetYuan = resolvedQuery.budgetYuan() + relaxedBudgetDelta;

        StageSearchResult relaxedStage = searchStage(
                resolvedQuery,
                relaxedBudgetYuan,
                relaxedRadiusKm,
                exactStage.esQueryTimedOut(),
                targetSize
        );
        List<House> mergedCandidates = limitCandidates(mergeCandidates(exactStage.candidates(), relaxedStage.candidates()), targetSize);
        SmartGuideCandidateBundle bundle = new SmartGuideCandidateBundle(
                resolvedQuery.locationName(),
                resolvedQuery.targetLatitude(),
                resolvedQuery.targetLongitude(),
                exactStage.esAvailable() && relaxedStage.esAvailable(),
                mergedCandidates
        );
        SmartGuideCandidateResult candidateResult = SmartGuideCandidateResult.relaxed(
                mergedCandidates,
                relaxedBudgetYuan,
                Math.min(exactStage.candidates().size(), targetSize)
        );
        return new RecommendationCollectionResult(bundle, candidateResult);
    }

    private ResolvedCandidateQuery resolveQuery(SmartGuideCandidateQuery query) {
        if (query == null || !StringUtils.hasText(query.locationName())) {
            throw new IllegalArgumentException("locationName cannot be blank");
        }
        LocationResolveService.ResolvedLocation location = locationResolveService.resolveRequired(query.locationName());
        String budgetScope = normalizeEnumValue(query.budgetScope());
        String rentMode = normalizeEnumValue(query.rentMode());
        return new ResolvedCandidateQuery(
                location.name(),
                location.latitude(),
                location.longitude(),
                query.budgetYuan(),
                budgetScope,
                rentMode,
                resolveRentKeyword(rentMode)
        );
    }

    private StageSearchResult searchStage(ResolvedCandidateQuery query,
                                          Integer filterBudgetYuan,
                                          double radiusKm,
                                          boolean skipEsPrefilter,
                                          int limit) {
        if (skipEsPrefilter) {
            log.info("Smart guide skip ES prefilter for relaxed stage because exact stage timed out, location={}, budget={}, radiusKm={}",
                    query.locationName(), filterBudgetYuan, radiusKm);
            List<House> candidates = queryCandidatesFallback(query, filterBudgetYuan, radiusKm, limit);
            return new StageSearchResult(candidates, false, false);
        }

        SmartGuidePrefilterResult prefilterResult = queryCandidateIdsFromEs(query, filterBudgetYuan, radiusKm);
        List<House> candidates;
        if (prefilterResult.esAvailable()) {
            candidates = queryCandidatesFromDb(query, filterBudgetYuan, radiusKm, prefilterResult.candidateIds(), limit);
            if (candidates.isEmpty()) {
                log.warn("Smart guide ES returned no effective candidates, fallback to DB scan, location={}, budget={}, radiusKm={}",
                        query.locationName(), filterBudgetYuan, radiusKm);
                candidates = queryCandidatesFallback(query, filterBudgetYuan, radiusKm, limit);
            }
        } else {
            candidates = queryCandidatesFallback(query, filterBudgetYuan, radiusKm, limit);
        }
        return new StageSearchResult(candidates, prefilterResult.esAvailable(), prefilterResult.esQueryTimedOut());
    }

    private SmartGuidePrefilterResult queryCandidateIdsFromEs(ResolvedCandidateQuery query,
                                                              Integer filterBudgetYuan,
                                                              double radiusKm) {
        if (elasticsearchOperations == null) {
            return SmartGuidePrefilterResult.esUnavailable();
        }
        try {
            List<Long> candidateIds = CompletableFuture.supplyAsync(() -> {
                        Integer coarseBudgetCent = resolveMaxComparableCostCent(filterBudgetYuan);
                        Integer rentTypeCode = resolveRentTypeCode(query.rentMode());
                        Query boolQuery = Query.of(q -> q.bool(b -> {
                            b.must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)));
                            if (rentTypeCode != null) {
                                b.must(m -> m.term(t -> t.field("rentType").value(rentTypeCode)));
                            } else if (StringUtils.hasText(query.rentKeyword())) {
                                b.must(m -> m.match(mm -> mm.field("title").query(query.rentKeyword())));
                            }
                            if (coarseBudgetCent != null) {
                                String budgetField = query.totalCostScope() ? "totalCost" : "price";
                                b.must(m -> m.range(r -> r.number(n -> n.field(budgetField).lte((double) coarseBudgetCent))));
                            }
                            b.filter(f -> f.geoDistance(g -> g
                                    .field("location")
                                    .distance(radiusKm + "km")
                                    .location(loc -> loc.latlon(ll -> ll
                                            .lat(query.targetLatitude())
                                            .lon(query.targetLongitude())))
                            ));
                            return b;
                        }));

                        SortOptions geoSort = SortOptions.of(s -> s.geoDistance(g -> g
                                .field("location")
                                .location(loc -> loc.latlon(ll -> ll
                                        .lat(query.targetLatitude())
                                        .lon(query.targetLongitude())))
                                .order(SortOrder.Asc)
                                .unit(DistanceUnit.Meters)
                        ));

                        NativeQuery nativeQuery = NativeQuery.builder()
                                .withQuery(boolQuery)
                                .withSort(geoSort)
                                .withPageable(PageRequest.of(0, SMART_GUIDE_ES_PREFILTER_SIZE))
                                .build();
                        SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
                        if (hits == null) {
                            return List.<Long>of();
                        }

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

            log.info("Smart guide ES prefilter finished, location={}, budget={}, radiusKm={}, candidateCount={}",
                    query.locationName(), filterBudgetYuan, radiusKm, candidateIds.size());
            return SmartGuidePrefilterResult.esAvailable(candidateIds);
        } catch (TimeoutException te) {
            log.warn("Smart guide ES prefilter timed out ({}ms), downgrade to DB", ES_QUERY_TIMEOUT_MS);
            return SmartGuidePrefilterResult.timedOut();
        } catch (Exception e) {
            log.error("Smart guide ES prefilter failed, downgrade to DB", e);
            return SmartGuidePrefilterResult.esUnavailable();
        }
    }

    private List<House> queryCandidatesFromDb(ResolvedCandidateQuery query,
                                              Integer filterBudgetYuan,
                                              double radiusKm,
                                              List<Long> candidateIds,
                                              int limit) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            log.warn("Smart guide ES candidateIds empty, will fallback to DB scan, location={}, budget={}, radiusKm={}",
                    query.locationName(), filterBudgetYuan, radiusKm);
            return List.of();
        }

        List<Long> filteredIds = new ArrayList<>(
                queryCandidateIdsFromDb(query, filterBudgetYuan, radiusKm, candidateIds, limit)
        );
        Map<Long, Integer> orderMap = new LinkedHashMap<>();
        for (int i = 0; i < candidateIds.size(); i++) {
            orderMap.put(candidateIds.get(i), i);
        }
        filteredIds.sort(Comparator.comparingInt(id -> orderMap.getOrDefault(id, Integer.MAX_VALUE)));
        return loadHousesByIdsInOrder(filteredIds);
    }

    private List<House> queryCandidatesFallback(ResolvedCandidateQuery query,
                                                Integer filterBudgetYuan,
                                                double radiusKm,
                                                int limit) {
        List<Long> filteredIds = queryCandidateIdsFromDb(
                query,
                filterBudgetYuan,
                radiusKm,
                null,
                Math.min(Math.max(limit, 1), SMART_GUIDE_DB_FALLBACK_SCAN_SIZE)
        );
        return loadHousesByIdsInOrder(filteredIds);
    }

    private List<Long> queryCandidateIdsFromDb(ResolvedCandidateQuery query,
                                               Integer filterBudgetYuan,
                                               double radiusKm,
                                               List<Long> candidateIds,
                                               int limit) {
        BoundingBox boundingBox = buildBoundingBox(query.targetLatitude(), query.targetLongitude(), radiusKm);
        return houseMapper.selectSmartGuideCandidateIds(
                candidateIds,
                HOUSE_STATUS_AVAILABLE,
                resolveRentTypeCode(query.rentMode()),
                query.totalCostScope(),
                resolveMaxComparableCostCent(filterBudgetYuan),
                query.targetLatitude(),
                query.targetLongitude(),
                boundingBox.minLatitude(),
                boundingBox.maxLatitude(),
                boundingBox.minLongitude(),
                boundingBox.maxLongitude(),
                radiusKm,
                Math.min(Math.max(limit, 1), SMART_GUIDE_MAX_CANDIDATES)
        );
    }

    private List<House> loadHousesByIdsInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<House> dbHouses = houseMapper.selectBatchIds(ids);
        Map<Long, House> houseMap = dbHouses.stream()
                .filter(house -> house != null && house.getId() != null)
                .collect(Collectors.toMap(House::getId, house -> house, (left, right) -> left, LinkedHashMap::new));

        List<House> orderedHouses = new ArrayList<>(ids.size());
        for (Long id : ids) {
            House house = houseMap.get(id);
            if (house != null) {
                orderedHouses.add(house);
            }
        }
        return orderedHouses;
    }

    private List<House> mergeCandidates(List<House> exactCandidates, List<House> relaxedCandidates) {
        LinkedHashMap<Long, House> merged = new LinkedHashMap<>();
        for (House house : exactCandidates) {
            if (house != null && house.getId() != null) {
                merged.put(house.getId(), house);
            }
        }
        for (House house : relaxedCandidates) {
            if (house != null && house.getId() != null) {
                merged.putIfAbsent(house.getId(), house);
            }
        }
        return merged.values().stream()
                .limit(SMART_GUIDE_MAX_CANDIDATES)
                .collect(Collectors.toList());
    }

    private List<House> limitCandidates(List<House> candidates, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(candidates.subList(0, Math.min(candidates.size(), limit)));
    }

    private Integer resolveRentTypeCode(String rentMode) {
        String normalizedRentMode = normalizeEnumValue(rentMode);
        if (RENT_MODE_WHOLE.equals(normalizedRentMode)) {
            return 1;
        }
        if (RENT_MODE_SHARED.equals(normalizedRentMode)) {
            return 2;
        }
        return null;
    }

    private String resolveRentKeyword(String rentMode) {
        String normalizedRentMode = normalizeEnumValue(rentMode);
        if (RENT_MODE_WHOLE.equals(normalizedRentMode)) {
            return RENT_KEYWORD_WHOLE;
        }
        if (RENT_MODE_SHARED.equals(normalizedRentMode)) {
            return RENT_KEYWORD_SHARED;
        }
        return null;
    }

    private String normalizeEnumValue(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Integer resolveMaxComparableCostCent(Integer budgetYuan) {
        return budgetYuan == null ? Integer.MAX_VALUE : budgetYuan * 100;
    }

    private BoundingBox buildBoundingBox(double latitude, double longitude, double radiusKm) {
        double latitudeDelta = radiusKm / 111.0d;
        double safeCos = Math.max(Math.cos(Math.toRadians(latitude)), 0.01d);
        double longitudeDelta = radiusKm / (111.0d * safeCos);
        double minLatitude = Math.max(latitude - latitudeDelta, -90.0d);
        double maxLatitude = Math.min(latitude + latitudeDelta, 90.0d);
        double minLongitude = Math.max(longitude - longitudeDelta, -180.0d);
        double maxLongitude = Math.min(longitude + longitudeDelta, 180.0d);
        return new BoundingBox(minLatitude, maxLatitude, minLongitude, maxLongitude);
    }

    public record RecommendationCollectionResult(
            SmartGuideCandidateBundle bundle,
            SmartGuideCandidateResult candidateResult
    ) {
    }

    private record ResolvedCandidateQuery(
            String locationName,
            double targetLatitude,
            double targetLongitude,
            Integer budgetYuan,
            String budgetScope,
            String rentMode,
            String rentKeyword
    ) {
        private boolean totalCostScope() {
            return BUDGET_SCOPE_TOTAL.equals(budgetScope);
        }
    }

    private record BoundingBox(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
    }

    private record StageSearchResult(List<House> candidates, boolean esAvailable, boolean esQueryTimedOut) {
    }
}

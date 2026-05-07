package cn.yy.myrent.service.discovery;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateBundle;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateCollector;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateQuery;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateResult;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HouseRecallServiceImpl implements HouseRecallService {

    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final double KEYWORD_LOCATION_RADIUS_KM = 10.0d;
    private static final int KEYWORD_BATCH_SIZE = 100;
    private static final int FILTER_BATCH_SIZE = 200;
    private static final int SMART_GUIDE_DEFAULT_SIZE = 24;

    private final ElasticsearchOperations elasticsearchOperations;
    private final HouseMapper houseMapper;
    private final LocationResolveService locationResolveService;
    private final SmartGuideCandidateCollector smartGuideCandidateCollector;

    @Override
    public HouseRecallResult recall(HouseRecallQuery query) {
        HouseRecallProfile profile = query == null || query.recallProfile() == null
                ? HouseRecallProfile.KEYWORD_SEARCH
                : query.recallProfile();
        return switch (profile) {
            case KEYWORD_SEARCH -> recallKeywordSearch(query);
            case LIST_FILTER -> recallListFilter(query);
            case SMART_GUIDE, AI_RECOMMEND -> recallSmartGuideCompatible(query);
        };
    }

    private HouseRecallResult recallKeywordSearch(HouseRecallQuery query) {
        String keyword = query == null || query.keyword() == null ? "" : query.keyword().trim();

        CompletableFuture<KeywordRecallEnvelope> locationFuture =
                CompletableFuture.supplyAsync(() -> searchKeywordByLocation(keyword));
        CompletableFuture<KeywordRecallEnvelope> textFuture =
                CompletableFuture.supplyAsync(() -> searchKeywordByText(keyword));

        KeywordRecallEnvelope locationEnvelope = locationFuture.join();
        KeywordRecallEnvelope textEnvelope = textFuture.join();
        Map<Long, KeywordRecallEvidence> mergedEvidence = mergeKeywordEvidence(
                locationEnvelope.evidence(),
                textEnvelope.evidence()
        );
        List<HouseRecallCandidate> candidates = loadKeywordCandidatesInOrder(mergedEvidence);
        boolean degraded = locationEnvelope.degraded() || textEnvelope.degraded();
        boolean esAvailable = !degraded;
        return new HouseRecallResult(candidates, esAvailable, degraded);
    }

    private HouseRecallResult recallListFilter(HouseRecallQuery query) {
        try {
            FilterSearchResult filterSearchResult = searchFilterInEs(query);
            return new HouseRecallResult(filterSearchResult.candidates(), true, false);
        } catch (Exception ex) {
            log.warn("list-filter ES recall failed, fallback to DB", ex);
        }

        return new HouseRecallResult(searchFilterInDb(query), false, true);
    }

    private HouseRecallResult recallSmartGuideCompatible(HouseRecallQuery query) {
        SmartGuideCandidateCollector.RecommendationCollectionResult collectionResult = smartGuideCandidateCollector.collectRecommendation(
                SmartGuideCandidateQuery.builder()
                        .locationName(query == null ? null : query.locationName())
                        .budgetYuan(query == null ? null : query.budgetYuan())
                        .budgetScope(query == null ? null : query.budgetScope())
                        .rentMode(query == null ? null : query.rentMode())
                        .size(query == null || query.size() == null ? SMART_GUIDE_DEFAULT_SIZE : query.size())
                        .build()
        );

        SmartGuideCandidateBundle bundle = collectionResult.bundle();
        SmartGuideCandidateResult candidateResult = collectionResult.candidateResult();
        List<HouseRecallCandidate> candidates = buildSmartGuideCandidates(query, bundle, candidateResult);
        boolean esAvailable = bundle.esAvailable();
        return new HouseRecallResult(candidates, esAvailable, !esAvailable);
    }

    private List<HouseRecallCandidate> buildSmartGuideCandidates(HouseRecallQuery query,
                                                                 SmartGuideCandidateBundle bundle,
                                                                 SmartGuideCandidateResult candidateResult) {
        if (bundle == null || bundle.candidates() == null || bundle.candidates().isEmpty()) {
            return List.of();
        }
        int exactMatchCount = candidateResult == null ? 0 : Math.max(candidateResult.exactMatchCount(), 0);
        List<HouseRecallCandidate> candidates = new ArrayList<>(bundle.candidates().size());
        for (int index = 0; index < bundle.candidates().size(); index++) {
            House house = bundle.candidates().get(index);
            if (house == null) {
                continue;
            }
            Double distanceMeters = calculateDistanceMeters(
                    bundle.targetLatitude(),
                    bundle.targetLongitude(),
                    house
            );
            boolean exactConstraintMatched = index < exactMatchCount;
            boolean relaxedBudgetApplied = !exactConstraintMatched && exceedsBudget(query, house);
            boolean relaxedRadiusApplied = !exactConstraintMatched && exceedsExactRadius(distanceMeters);
            HouseRecallMatchTier matchTier = resolveSmartGuideMatchTier(
                    exactConstraintMatched,
                    relaxedBudgetApplied,
                    relaxedRadiusApplied
            );
            candidates.add(new HouseRecallCandidate(
                    house,
                    matchTier,
                    HouseRecallEvidence.builder()
                            .locationMatched(true)
                            .locationDistanceMeters(distanceMeters)
                            .exactConstraintMatched(exactConstraintMatched)
                            .relaxedBudgetApplied(relaxedBudgetApplied)
                            .relaxedRadiusApplied(relaxedRadiusApplied)
                            .nearSubwayMatched(house.getNearSubway() != null && house.getNearSubway() == 1)
                            .privateBathroomMatched(house.getPrivateBathroom() != null && house.getPrivateBathroom() == 1)
                            .hasBalconyMatched(house.getHasBalcony() != null && house.getHasBalcony() == 1)
                            .civilWaterElectricMatched(house.getCivilWaterElectric() != null && house.getCivilWaterElectric() == 1)
                            .supportStudentDepositFreeMatched(house.getSupportStudentDepositFree() != null && house.getSupportStudentDepositFree() == 1)
                            .build()
            ));
        }
        return candidates;
    }

    private HouseRecallMatchTier resolveSmartGuideMatchTier(boolean exactConstraintMatched,
                                                            boolean relaxedBudgetApplied,
                                                            boolean relaxedRadiusApplied) {
        if (exactConstraintMatched) {
            return HouseRecallMatchTier.EXACT;
        }
        if (relaxedBudgetApplied && relaxedRadiusApplied) {
            return HouseRecallMatchTier.RELAXED_BUDGET_AND_RADIUS;
        }
        if (relaxedBudgetApplied) {
            return HouseRecallMatchTier.RELAXED_BUDGET;
        }
        if (relaxedRadiusApplied) {
            return HouseRecallMatchTier.RELAXED_RADIUS;
        }
        return HouseRecallMatchTier.EXACT;
    }

    private boolean exceedsBudget(HouseRecallQuery query, House house) {
        if (query == null || query.budgetYuan() == null || house == null) {
            return false;
        }
        int comparableCostCent = resolveComparableCostCent(house, "TOTAL".equalsIgnoreCase(query.budgetScope()));
        return comparableCostCent > query.budgetYuan() * 100;
    }

    private boolean exceedsExactRadius(Double distanceMeters) {
        return distanceMeters != null && distanceMeters > 3000.0d;
    }

    private int resolveComparableCostCent(House house, boolean totalCostScope) {
        if (house == null || house.getPrice() == null) {
            return 0;
        }
        int price = Math.max(house.getPrice(), 0);
        if (!totalCostScope) {
            return price;
        }
        if (house.getTotalCost() != null) {
            return Math.max(house.getTotalCost(), 0);
        }
        int deposit = house.getDepositAmount() == null ? 0 : Math.max(house.getDepositAmount(), 0);
        return price + deposit;
    }

    private Double calculateDistanceMeters(double targetLatitude, double targetLongitude, House house) {
        if (house == null || house.getLatitude() == null || house.getLongitude() == null) {
            return null;
        }
        return haversineMeters(
                targetLatitude,
                targetLongitude,
                house.getLatitude().doubleValue(),
                house.getLongitude().doubleValue()
        );
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double radiusMeters = 6371000.0d;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return radiusMeters * c;
    }

    private KeywordRecallEnvelope searchKeywordByLocation(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return KeywordRecallEnvelope.normal(new LinkedHashMap<>());
        }

        try {
            LocationResolveService.ResolvedLocation resolvedLocation = locationResolveService.resolveRequired(keyword);
            Query query = Query.of(q -> q.bool(b -> b
                    .must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)))
                    .filter(f -> f.geoDistance(g -> g
                            .field("location")
                            .distance(KEYWORD_LOCATION_RADIUS_KM + "km")
                            .location(loc -> loc.latlon(ll -> ll
                                    .lat(resolvedLocation.latitude())
                                    .lon(resolvedLocation.longitude())))))
            ));

            Map<Long, KeywordRecallEvidence> evidenceMap = new LinkedHashMap<>();
            int pageIndex = 0;
            int rank = 0;
            while (true) {
                NativeQuery nativeQuery = NativeQuery.builder()
                        .withQuery(query)
                        .withSort(SortOptions.of(s -> s.geoDistance(g -> g
                                .field("location")
                                .location(loc -> loc.latlon(ll -> ll
                                        .lat(resolvedLocation.latitude())
                                        .lon(resolvedLocation.longitude())))
                                .order(SortOrder.Asc)
                                .unit(DistanceUnit.Meters))))
                        .withPageable(PageRequest.of(pageIndex, KEYWORD_BATCH_SIZE))
                        .build();

                SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
                if (hits.isEmpty()) {
                    break;
                }
                for (SearchHit<HouseDoc> hit : hits) {
                    HouseDoc doc = hit.getContent();
                    if (doc == null || doc.getId() == null || evidenceMap.containsKey(doc.getId())) {
                        continue;
                    }
                    Double distanceMeters = null;
                    if (hit.getSortValues() != null && !hit.getSortValues().isEmpty()) {
                        Object firstSortValue = hit.getSortValues().get(0);
                        if (firstSortValue instanceof Number number) {
                            distanceMeters = number.doubleValue();
                        }
                    }
                    evidenceMap.put(doc.getId(), new KeywordRecallEvidence(doc.getId(), true, false, distanceMeters, rank, null, null));
                    rank += 1;
                }
                if (hits.getSearchHits().size() < KEYWORD_BATCH_SIZE) {
                    break;
                }
                pageIndex += 1;
            }
            return KeywordRecallEnvelope.normal(evidenceMap);
        } catch (IllegalArgumentException ignored) {
            return KeywordRecallEnvelope.normal(new LinkedHashMap<>());
        } catch (Exception ex) {
            log.warn("keyword location recall failed, keyword={}", keyword, ex);
            return KeywordRecallEnvelope.degraded(new LinkedHashMap<>());
        }
    }

    private KeywordRecallEnvelope searchKeywordByText(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return KeywordRecallEnvelope.normal(new LinkedHashMap<>());
        }

        try {
            Query query = Query.of(q -> q.bool(b -> b
                    .must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)))
                    .must(m -> m.match(mm -> mm.field("title").query(keyword)))
            ));

            Map<Long, KeywordRecallEvidence> evidenceMap = new LinkedHashMap<>();
            int pageIndex = 0;
            int rank = 0;
            while (true) {
                NativeQuery nativeQuery = NativeQuery.builder()
                        .withQuery(query)
                        .withSort(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))))
                        .withSort(SortOptions.of(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc))))
                        .withPageable(PageRequest.of(pageIndex, KEYWORD_BATCH_SIZE))
                        .build();

                SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
                if (hits.isEmpty()) {
                    break;
                }
                for (SearchHit<HouseDoc> hit : hits) {
                    HouseDoc doc = hit.getContent();
                    if (doc == null || doc.getId() == null || evidenceMap.containsKey(doc.getId())) {
                        continue;
                    }
                    evidenceMap.put(doc.getId(), new KeywordRecallEvidence(doc.getId(), false, true, null, null, rank, hit.getScore()));
                    rank += 1;
                }
                if (hits.getSearchHits().size() < KEYWORD_BATCH_SIZE) {
                    break;
                }
                pageIndex += 1;
            }
            return KeywordRecallEnvelope.normal(evidenceMap);
        } catch (Exception ex) {
            log.warn("keyword text recall failed, keyword={}", keyword, ex);
            return KeywordRecallEnvelope.degraded(new LinkedHashMap<>());
        }
    }

    private Map<Long, KeywordRecallEvidence> mergeKeywordEvidence(Map<Long, KeywordRecallEvidence> locationEvidence,
                                                                  Map<Long, KeywordRecallEvidence> textEvidence) {
        Map<Long, KeywordRecallEvidence> merged = new LinkedHashMap<>();
        Set<Long> orderedIds = new LinkedHashSet<>();
        orderedIds.addAll(locationEvidence.keySet());
        orderedIds.addAll(textEvidence.keySet());
        for (Long id : orderedIds) {
            KeywordRecallEvidence location = locationEvidence.get(id);
            KeywordRecallEvidence text = textEvidence.get(id);
            merged.put(id, mergeKeywordEvidence(location, text));
        }
        return merged;
    }

    private KeywordRecallEvidence mergeKeywordEvidence(KeywordRecallEvidence location, KeywordRecallEvidence text) {
        if (location == null) {
            return text;
        }
        if (text == null) {
            return location;
        }
        return new KeywordRecallEvidence(
                location.houseId(),
                location.locationMatched() || text.locationMatched(),
                location.textMatched() || text.textMatched(),
                location.locationDistanceMeters() != null ? location.locationDistanceMeters() : text.locationDistanceMeters(),
                location.locationRank() != null ? location.locationRank() : text.locationRank(),
                location.textRank() != null ? location.textRank() : text.textRank(),
                location.textScore() != null ? location.textScore() : text.textScore()
        );
    }

    private List<HouseRecallCandidate> loadKeywordCandidatesInOrder(Map<Long, KeywordRecallEvidence> mergedEvidence) {
        if (mergedEvidence == null || mergedEvidence.isEmpty()) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>(mergedEvidence.keySet());
        Map<Long, House> houseMap = houseMapper.selectBatchIds(ids).stream()
                .filter(Objects::nonNull)
                .filter(house -> house.getId() != null)
                .filter(house -> house.getStatus() != null && house.getStatus() == HOUSE_STATUS_AVAILABLE)
                .collect(Collectors.toMap(House::getId, house -> house, (left, right) -> left));

        List<HouseRecallCandidate> candidates = new ArrayList<>();
        for (Long id : ids) {
            House house = houseMap.get(id);
            if (house == null) {
                continue;
            }
            KeywordRecallEvidence evidence = mergedEvidence.get(id);
            candidates.add(new HouseRecallCandidate(
                    house,
                    resolveKeywordMatchTier(evidence),
                    HouseRecallEvidence.builder()
                            .locationMatched(evidence.locationMatched())
                            .textMatched(evidence.textMatched())
                            .locationDistanceMeters(evidence.locationDistanceMeters())
                            .locationRank(evidence.locationRank())
                            .textRank(evidence.textRank())
                            .textScore(evidence.textScore())
                            .build()
            ));
        }
        return candidates;
    }

    private HouseRecallMatchTier resolveKeywordMatchTier(KeywordRecallEvidence evidence) {
        if (evidence == null) {
            return HouseRecallMatchTier.TEXT_ONLY;
        }
        if (evidence.locationMatched() && evidence.textMatched()) {
            return HouseRecallMatchTier.EXACT;
        }
        if (evidence.locationMatched()) {
            return HouseRecallMatchTier.LOCATION_ONLY;
        }
        return HouseRecallMatchTier.TEXT_ONLY;
    }

    private FilterSearchResult searchFilterInEs(HouseRecallQuery query) {
        Query boolQuery = Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)));
            if (query != null) {
                if (StringUtils.hasText(query.city())) {
                    b.filter(f -> f.term(t -> t.field("city").value(query.city())));
                }
                if (StringUtils.hasText(query.region())) {
                    b.filter(f -> f.term(t -> t.field("region").value(query.region())));
                }
                if (query.rentType() != null) {
                    b.filter(f -> f.term(t -> t.field("rentType").value(query.rentType())));
                }
                Integer minPriceCent = resolveMinPriceCent(query);
                if (minPriceCent != null) {
                    b.filter(f -> f.range(r -> r.number(n -> n.field("price").gte((double) minPriceCent))));
                }
                Integer maxPriceCent = resolveMaxPriceCent(query);
                if (maxPriceCent != null) {
                    b.filter(f -> f.range(r -> r.number(n -> n.field("price").lte((double) maxPriceCent))));
                }
                if (Boolean.TRUE.equals(query.nearSubway())) {
                    b.filter(f -> f.term(t -> t.field("nearSubway").value(true)));
                }
                if (Boolean.TRUE.equals(query.privateBathroom())) {
                    b.filter(f -> f.term(t -> t.field("privateBathroom").value(true)));
                }
                if (Boolean.TRUE.equals(query.hasBalcony())) {
                    b.filter(f -> f.term(t -> t.field("hasBalcony").value(true)));
                }
                if (Boolean.TRUE.equals(query.civilWaterElectric())) {
                    b.filter(f -> f.term(t -> t.field("civilWaterElectric").value(true)));
                }
                if (Boolean.TRUE.equals(query.supportStudentDepositFree())) {
                    b.filter(f -> f.term(t -> t.field("supportStudentDepositFree").value(true)));
                }
            }
            return b;
        }));

        List<HouseRecallCandidate> candidates = new ArrayList<>();
        int pageIndex = 0;
        while (true) {
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(boolQuery)
                    .withSort(SortOptions.of(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc))))
                    .withSort(SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Desc))))
                    .withPageable(PageRequest.of(pageIndex, FILTER_BATCH_SIZE))
                    .build();

            SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
            if (hits.isEmpty()) {
                break;
            }
            for (SearchHit<HouseDoc> hit : hits) {
                HouseDoc doc = hit.getContent();
                if (doc == null) {
                    continue;
                }
                House house = convertDocToHouse(doc);
                candidates.add(new HouseRecallCandidate(
                        house,
                        HouseRecallMatchTier.FILTER_ONLY,
                        buildFilterEvidence(query, house)
                ));
            }
            if (hits.getSearchHits().size() < FILTER_BATCH_SIZE) {
                break;
            }
            pageIndex += 1;
        }
        return new FilterSearchResult(candidates);
    }

    private List<HouseRecallCandidate> searchFilterInDb(HouseRecallQuery query) {
        List<House> houses = houseMapper.selectList(buildFilterCountQuery(query)
                .orderByDesc("create_time")
                .orderByDesc("id"));

        return houses.stream()
                .filter(Objects::nonNull)
                .map(house -> new HouseRecallCandidate(
                        house,
                        HouseRecallMatchTier.FILTER_ONLY,
                        buildFilterEvidence(query, house)
                ))
                .collect(Collectors.toList());
    }

    private com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<House> buildFilterCountQuery(HouseRecallQuery query) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<House> queryWrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.eq("status", HOUSE_STATUS_AVAILABLE);
        if (query == null) {
            return queryWrapper;
        }
        if (StringUtils.hasText(query.city())) {
            queryWrapper.eq("city", query.city());
        }
        if (StringUtils.hasText(query.region())) {
            queryWrapper.eq("region", query.region());
        }
        if (query.rentType() != null) {
            queryWrapper.eq("rent_type", query.rentType());
        }
        Integer minPriceCent = resolveMinPriceCent(query);
        if (minPriceCent != null) {
            queryWrapper.ge("price", minPriceCent);
        }
        Integer maxPriceCent = resolveMaxPriceCent(query);
        if (maxPriceCent != null) {
            queryWrapper.le("price", maxPriceCent);
        }
        if (Boolean.TRUE.equals(query.nearSubway())) {
            queryWrapper.eq("near_subway", 1);
        }
        if (Boolean.TRUE.equals(query.privateBathroom())) {
            queryWrapper.eq("private_bathroom", 1);
        }
        if (Boolean.TRUE.equals(query.hasBalcony())) {
            queryWrapper.eq("has_balcony", 1);
        }
        if (Boolean.TRUE.equals(query.civilWaterElectric())) {
            queryWrapper.eq("civil_water_electric", 1);
        }
        if (Boolean.TRUE.equals(query.supportStudentDepositFree())) {
            queryWrapper.eq("support_student_deposit_free", 1);
        }
        return queryWrapper;
    }

    private HouseRecallEvidence buildFilterEvidence(HouseRecallQuery query, House house) {
        return HouseRecallEvidence.builder()
                .exactConstraintMatched(true)
                .nearSubwayMatched(!Boolean.TRUE.equals(query == null ? null : query.nearSubway())
                        || (house.getNearSubway() != null && house.getNearSubway() == 1))
                .privateBathroomMatched(!Boolean.TRUE.equals(query == null ? null : query.privateBathroom())
                        || (house.getPrivateBathroom() != null && house.getPrivateBathroom() == 1))
                .hasBalconyMatched(!Boolean.TRUE.equals(query == null ? null : query.hasBalcony())
                        || (house.getHasBalcony() != null && house.getHasBalcony() == 1))
                .civilWaterElectricMatched(!Boolean.TRUE.equals(query == null ? null : query.civilWaterElectric())
                        || (house.getCivilWaterElectric() != null && house.getCivilWaterElectric() == 1))
                .supportStudentDepositFreeMatched(!Boolean.TRUE.equals(query == null ? null : query.supportStudentDepositFree())
                        || (house.getSupportStudentDepositFree() != null && house.getSupportStudentDepositFree() == 1))
                .build();
    }

    private Integer resolveMinPriceCent(HouseRecallQuery query) {
        if (query == null || query.minPriceYuan() == null || query.minPriceYuan() < 0) {
            return null;
        }
        return query.minPriceYuan() * 100;
    }

    private Integer resolveMaxPriceCent(HouseRecallQuery query) {
        if (query == null || query.maxPriceYuan() == null || query.maxPriceYuan() < 0) {
            return null;
        }
        return query.maxPriceYuan() * 100;
    }

    private House convertDocToHouse(HouseDoc doc) {
        House house = new House();
        house.setId(doc.getId());
        house.setPublisherUserId(doc.getPublisherUserId());
        house.setTitle(doc.getTitle());
        house.setCity(doc.getCity());
        house.setRegion(doc.getRegion());
        house.setNearSubway(Boolean.TRUE.equals(doc.getNearSubway()) ? 1 : 0);
        house.setPrivateBathroom(Boolean.TRUE.equals(doc.getPrivateBathroom()) ? 1 : 0);
        house.setHasBalcony(Boolean.TRUE.equals(doc.getHasBalcony()) ? 1 : 0);
        house.setCivilWaterElectric(Boolean.TRUE.equals(doc.getCivilWaterElectric()) ? 1 : 0);
        house.setSupportStudentDepositFree(Boolean.TRUE.equals(doc.getSupportStudentDepositFree()) ? 1 : 0);
        house.setRentType(doc.getRentType());
        house.setPrice(doc.getPrice());
        house.setDepositAmount(doc.getDepositAmount());
        house.setStatus(doc.getStatus());
        house.setCreateTime(doc.getCreateTime());
        return house;
    }

    private record KeywordRecallEnvelope(Map<Long, KeywordRecallEvidence> evidence, boolean degraded) {
        private static KeywordRecallEnvelope normal(Map<Long, KeywordRecallEvidence> evidence) {
            return new KeywordRecallEnvelope(evidence, false);
        }

        private static KeywordRecallEnvelope degraded(Map<Long, KeywordRecallEvidence> evidence) {
            return new KeywordRecallEnvelope(evidence, true);
        }
    }

    private record KeywordRecallEvidence(Long houseId,
                                         boolean locationMatched,
                                         boolean textMatched,
                                         Double locationDistanceMeters,
                                         Integer locationRank,
                                         Integer textRank,
                                         Float textScore) {
    }

    private record FilterSearchResult(List<HouseRecallCandidate> candidates) {
    }
}

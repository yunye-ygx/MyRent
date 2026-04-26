package cn.yy.myrent.service.search;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.HouseKeywordSearchReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseVO;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
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
public class HouseKeywordSearchService {

    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int OVERSAMPLE_MULTIPLIER = 3;
    private static final double LOCATION_RADIUS_KM = 10.0d;

    private final ElasticsearchOperations elasticsearchOperations;
    private final HouseMapper houseMapper;
    private final LocationResolveService locationResolveService;
    private final IUserService userService;

    public HouseSearchResultVO search(HouseKeywordSearchReqDTO reqDTO) {
        String keyword = reqDTO == null || reqDTO.getKeyword() == null ? "" : reqDTO.getKeyword().trim();
        int page = reqDTO == null || reqDTO.getPage() == null ? DEFAULT_PAGE : Math.max(reqDTO.getPage(), 1);
        int size = reqDTO == null || reqDTO.getSize() == null
                ? DEFAULT_SIZE
                : Math.min(Math.max(reqDTO.getSize(), 1), MAX_SIZE);
        int recallSize = size * OVERSAMPLE_MULTIPLIER;

        log.info("开始执行多路召回任务，keyword：{}，page：{}，size：{}", keyword, page, size);
        CompletableFuture<RecallEnvelope> locationFuture =
                CompletableFuture.supplyAsync(() -> searchByLocation(keyword, recallSize));
        CompletableFuture<RecallEnvelope> textFuture =
                CompletableFuture.supplyAsync(() -> searchByText(keyword, recallSize));

        RecallEnvelope locationEnvelope = locationFuture.join();
        RecallEnvelope textEnvelope = textFuture.join();


        Map<Long, RecallEvidence> mergedEvidence = mergeEvidence(locationEnvelope.evidence(), textEnvelope.evidence());
        log.info("合并地点搜索和文本搜索的结果，并进行去重，{}", mergedEvidence.size());

        List<House> availableHouses = loadAvailableHousesInRecallOrder(mergedEvidence.keySet());
        log.info("从数据库中加载真正可用房源信息，{}", availableHouses.size());

        List<RankedHouse> rankedHouses = availableHouses.stream()
                .map(house -> new RankedHouse(house, buildScore(mergedEvidence.get(house.getId()))))
                .sorted(Comparator
                        .comparingDouble(RankedHouse::score).reversed()
                        .thenComparing(item -> item.house().getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.house().getId(), Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        log.info("对房源进行排序，并计算评分，{}", rankedHouses.size());

        List<House> pageRecords = slicePage(rankedHouses, page, size);

        HouseSearchResultVO result = new HouseSearchResultVO();
        result.setTotal((long) rankedHouses.size());
        result.setHouses(enrichPublisherNames(toHouseVos(pageRecords)));
        boolean degraded = locationEnvelope.degraded() || textEnvelope.degraded();
        result.setEsDown(degraded);
        result.setFallbackSource(degraded ? "KEYWORD_SEARCH_DEGRADED" : "KEYWORD_SEARCH");
        result.setTipMessage(pageRecords.isEmpty() ? "当前未找到匹配房源" : null);
        return result;
    }

    private RecallEnvelope searchByLocation(String keyword, int recallSize) {
        if (!StringUtils.hasText(keyword)) {
            return RecallEnvelope.normal(new LinkedHashMap<>());
        }

        try {
            LocationResolveService.ResolvedLocation resolvedLocation = locationResolveService.resolveRequired(keyword);
            Query query = Query.of(q -> q.bool(b -> b
                    .must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)))
                    .filter(f -> f.geoDistance(g -> g
                            .field("location")
                            .distance(LOCATION_RADIUS_KM + "km")
                            .location(loc -> loc.latlon(ll -> ll
                                    .lat(resolvedLocation.latitude())
                                    .lon(resolvedLocation.longitude())))))
            ));

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withSort(SortOptions.of(s -> s.geoDistance(g -> g
                            .field("location")
                            .location(loc -> loc.latlon(ll -> ll
                                    .lat(resolvedLocation.latitude())
                                    .lon(resolvedLocation.longitude())))
                            .order(SortOrder.Asc)
                            .unit(DistanceUnit.Meters))))
                    .withPageable(PageRequest.of(0, recallSize))
                    .build();

            SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
            Map<Long, RecallEvidence> evidenceMap = new LinkedHashMap<>();
            int rank = 0;
            for (SearchHit<HouseDoc> hit : hits) {
                HouseDoc doc = hit.getContent();
                if (doc == null || doc.getId() == null) {
                    continue;
                }
                Double distanceMeters = null;
                if (hit.getSortValues() != null && !hit.getSortValues().isEmpty()) {
                    Object firstSortValue = hit.getSortValues().get(0);
                    if (firstSortValue instanceof Number number) {
                        distanceMeters = number.doubleValue();
                    }
                }
                evidenceMap.put(doc.getId(), new RecallEvidence(doc.getId(), true, false, distanceMeters, rank, null, null));
                rank += 1;
            }
            return RecallEnvelope.normal(evidenceMap);
        } catch (IllegalArgumentException ignored) {
            return RecallEnvelope.normal(new LinkedHashMap<>());
        } catch (Exception ex) {
            log.warn("keyword location recall failed, keyword={}", keyword, ex);
            return RecallEnvelope.degraded(new LinkedHashMap<>());
        }
    }

    private RecallEnvelope searchByText(String keyword, int recallSize) {
        if (!StringUtils.hasText(keyword)) {
            return RecallEnvelope.normal(new LinkedHashMap<>());
        }

        try {
            Query query = Query.of(q -> q.bool(b -> b
                    .must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)))
                    .must(m -> m.match(mm -> mm.field("title").query(keyword)))
            ));

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withSort(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))))
                    .withSort(SortOptions.of(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc))))
                    .withPageable(PageRequest.of(0, recallSize))
                    .build();

            SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
            Map<Long, RecallEvidence> evidenceMap = new LinkedHashMap<>();
            int rank = 0;
            for (SearchHit<HouseDoc> hit : hits) {
                HouseDoc doc = hit.getContent();
                if (doc == null || doc.getId() == null) {
                    continue;
                }
                evidenceMap.put(doc.getId(), new RecallEvidence(doc.getId(), false, true, null, null, rank, hit.getScore()));
                rank += 1;
            }
            return RecallEnvelope.normal(evidenceMap);
        } catch (Exception ex) {
            log.warn("keyword text recall failed, keyword={}", keyword, ex);
            return RecallEnvelope.degraded(new LinkedHashMap<>());
        }
    }

    private Map<Long, RecallEvidence> mergeEvidence(Map<Long, RecallEvidence> locationEvidence,
                                                    Map<Long, RecallEvidence> textEvidence) {
        Map<Long, RecallEvidence> merged = new LinkedHashMap<>();
        Set<Long> orderedIds = new LinkedHashSet<>();
        orderedIds.addAll(locationEvidence.keySet());
        orderedIds.addAll(textEvidence.keySet());
        for (Long id : orderedIds) {
            RecallEvidence location = locationEvidence.get(id);
            RecallEvidence text = textEvidence.get(id);
            merged.put(id, mergeEvidence(location, text));
        }
        return merged;
    }

    private RecallEvidence mergeEvidence(RecallEvidence location, RecallEvidence text) {
        if (location == null) {
            return text;
        }
        if (text == null) {
            return location;
        }
        return new RecallEvidence(
                location.houseId(),
                location.locationMatched() || text.locationMatched(),
                location.textMatched() || text.textMatched(),
                location.locationDistanceMeters() != null ? location.locationDistanceMeters() : text.locationDistanceMeters(),
                location.locationRank() != null ? location.locationRank() : text.locationRank(),
                location.textRank() != null ? location.textRank() : text.textRank(),
                location.textScore() != null ? location.textScore() : text.textScore()
        );
    }

    private List<House> loadAvailableHousesInRecallOrder(Set<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>(candidateIds);
        Map<Long, House> houseMap = houseMapper.selectBatchIds(ids).stream()
                .filter(Objects::nonNull)
                .filter(house -> house.getId() != null)
                .filter(house -> house.getStatus() != null && house.getStatus() == HOUSE_STATUS_AVAILABLE)
                .collect(Collectors.toMap(House::getId, house -> house, (left, right) -> left));

        List<House> orderedHouses = new ArrayList<>();
        for (Long id : ids) {
            House house = houseMap.get(id);
            if (house != null) {
                orderedHouses.add(house);
            }
        }
        return orderedHouses;
    }

    private List<House> slicePage(List<RankedHouse> rankedHouses, int page, int size) {
        int fromIndex = Math.max((page - 1) * size, 0);
        if (fromIndex >= rankedHouses.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + size, rankedHouses.size());
        return rankedHouses.subList(fromIndex, toIndex).stream()
                .map(RankedHouse::house)
                .collect(Collectors.toList());
    }

    private double buildScore(RecallEvidence evidence) {
        if (evidence == null) {
            return 0;
        }

        double score = 0;
        if (evidence.locationMatched()) {
            score += 1000;
        }
        if (evidence.textMatched()) {
            score += 600;
        }
        if (evidence.locationMatched() && evidence.textMatched()) {
            score += 200;
        }
        if (evidence.locationDistanceMeters() != null) {
            score += Math.max(0, 120 - evidence.locationDistanceMeters() / 20.0d);
        }
        if (evidence.textRank() != null) {
            score += Math.max(0, 80 - evidence.textRank() * 5.0d);
        }
        return score;
    }

    private List<HouseVO> toHouseVos(List<House> houses) {
        return houses.stream()
                .map(this::convertHouseToVo)
                .collect(Collectors.toList());
    }

    private List<HouseVO> enrichPublisherNames(List<HouseVO> houses) {
        if (houses.isEmpty()) {
            return houses;
        }

        List<Long> publisherIds = houses.stream()
                .map(HouseVO::getPublisherUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (publisherIds.isEmpty()) {
            return houses;
        }

        Map<Long, String> userNameMap = userService.listByIds(publisherIds).stream()
                .filter(Objects::nonNull)
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(
                        User::getId,
                        user -> StringUtils.hasText(user.getName()) ? user.getName() : "未知发布者",
                        (left, right) -> left
                ));

        houses.forEach(house -> house.setPublisherName(userNameMap.getOrDefault(house.getPublisherUserId(), "未知发布者")));
        return houses;
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
        vo.setStatus(house.getStatus());
        if (house.getPrice() != null) {
            vo.setPrice(BigDecimal.valueOf(house.getPrice()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        if (house.getDepositAmount() != null) {
            vo.setDepositAmount(BigDecimal.valueOf(house.getDepositAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return vo;
    }

    private record RecallEnvelope(Map<Long, RecallEvidence> evidence, boolean degraded) {
        private static RecallEnvelope normal(Map<Long, RecallEvidence> evidence) {
            return new RecallEnvelope(evidence, false);
        }

        private static RecallEnvelope degraded(Map<Long, RecallEvidence> evidence) {
            return new RecallEnvelope(evidence, true);
        }
    }

    private record RecallEvidence(Long houseId,
                                  boolean locationMatched,
                                  boolean textMatched,
                                  Double locationDistanceMeters,
                                  Integer locationRank,
                                  Integer textRank,
                                  Float textScore) {
    }

    private record RankedHouse(House house, double score) {
    }
}

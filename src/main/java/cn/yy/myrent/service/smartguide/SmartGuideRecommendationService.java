package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.LocationDict;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.LocationDictMapper;
import cn.yy.myrent.service.score.SmartGuideScoreCalculator;
import cn.yy.myrent.vo.SmartGuideItemVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class SmartGuideRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(SmartGuideRecommendationService.class);

    private static final long ES_QUERY_TIMEOUT_MS = 1200;

    private static final String BUDGET_SCOPE_RENT_ONLY = "RENT_ONLY";
    private static final String BUDGET_SCOPE_TOTAL = "TOTAL";
    private static final String RENT_MODE_WHOLE = "WHOLE";
    private static final String RENT_MODE_SHARED = "SHARED";

    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final int SMART_GUIDE_MAX_CANDIDATES = 200;
    private static final int SMART_GUIDE_ES_PREFILTER_SIZE = 300;
    private static final int SMART_GUIDE_DB_FALLBACK_SCAN_SIZE = 500;
    private static final int RELAX_BUDGET_DELTA_FEW_RESULT_YUAN = 300;
    private static final int RELAX_BUDGET_DELTA_EMPTY_RESULT_YUAN = 500;
    private static final int BUDGET_CLOSE_MIN_DIFF_YUAN = 500;
    private static final double BUDGET_CLOSE_RATIO = 0.15d;
    private static final double SEARCH_RADIUS_EXACT_KM = 3.0d;
    private static final double SEARCH_RADIUS_RELAXED_FEW_RESULT_KM = 5.0d;
    private static final double SEARCH_RADIUS_RELAXED_EMPTY_RESULT_KM = 8.0d;

    private static final String RENT_KEYWORD_WHOLE = "整租";
    private static final String RENT_KEYWORD_SHARED = "合租";

    private static final String TIP_ES_DEGRADED = "ES 预筛暂不可用，当前结果已降级为 DB 二次筛选。";
    private static final String TIP_MATCHED = "已找到符合条件的房源，并按综合评分排序。";
    private static final String TIP_RELAXED_FROM_EMPTY = "未找到完全符合条件的房源，已放宽预算和搜索范围展示备选结果。";
    private static final String TIP_RELAXED_FROM_FEW = "完全符合条件的房源较少，已补充放宽条件后的备选结果。";
    private static final String TIP_RELAXED_NO_EXTRA = "完全符合条件的房源较少，已尝试放宽条件，但当前结果仍然有限。";
    private static final String TIP_FEW_EXACT = "已找到符合条件的房源，但更贴近预算的结果较少。";

    private final ElasticsearchOperations elasticsearchOperations;
    private final HouseMapper houseMapper;
    private final LocationDictMapper locationDictMapper;
    private final SmartGuideScoreCalculator smartGuideScoreCalculator;

    /**
     * 智能找房总入口。
     * 整体流程是：参数校验 -> 地点解析 -> 候选召回 -> 放宽补充 -> 打分排序 -> 分页返回。
     */
    public SmartGuideResultVO recommend(SmartGuideReqDTO reqDTO) {
        validateRequest(reqDTO);
        SmartGuideQueryContext queryContext = buildQueryContext(reqDTO);
        WorkflowSearchResult workflowResult = collectCandidates(queryContext);
        return buildResult(queryContext, workflowResult);
    }

    /**
     * 把请求参数整理成统一的查询上下文。
     * 这里会顺手完成预算口径标准化、租住方式标准化，以及地点名到坐标的解析。
     */

    //把前端输入的地点与数据库的地点作比较，看看有没有这个地方，有的话就查找这个地方旁边的酒店
    private SmartGuideQueryContext buildQueryContext(SmartGuideReqDTO reqDTO) {
        int page = reqDTO.getPage() == null ? 1 : reqDTO.getPage();
        int size = reqDTO.getSize() == null ? 10 : reqDTO.getSize();
        int budgetYuan = reqDTO.getBudgetYuan();
        String budgetScope = normalizeEnumValue(reqDTO.getBudgetScope());
        String rentMode = normalizeEnumValue(reqDTO.getRentMode());
        String rentKeyword = resolveRentKeyword(rentMode);
        String requestedLocationName = resolveRequestedLocationName(reqDTO);
        ResolvedLocation resolvedLocation = resolveLocation(requestedLocationName);
        return new SmartGuideQueryContext(
                page,
                size,
                budgetYuan,
                budgetYuan * 100,
                budgetScope,
                rentMode,
                rentKeyword,
                resolvedLocation.name(),
                resolvedLocation.latitude(),
                resolvedLocation.longitude()
        );
    }

    /**
     * 收集最终候选房源。
     * 先做一轮严格条件搜索，如果数量不足一页，再执行一轮放宽策略并把结果补充进来。
     */
    private WorkflowSearchResult collectCandidates(SmartGuideQueryContext queryContext) {
        StageSearchResult exactStage = searchStage(queryContext, queryContext.budgetYuan(), SEARCH_RADIUS_EXACT_KM, false);
        if (exactStage.candidates().size() >= queryContext.size()) {
            return new WorkflowSearchResult(
                    SmartGuideCandidateResult.exact(exactStage.candidates(), queryContext.budgetYuan()),
                    exactStage.esAvailable()
            );
        }

        int relaxedBudgetDelta = exactStage.candidates().isEmpty()
                ? RELAX_BUDGET_DELTA_EMPTY_RESULT_YUAN
                : RELAX_BUDGET_DELTA_FEW_RESULT_YUAN;
        double relaxedRadiusKm = exactStage.candidates().isEmpty()
                ? SEARCH_RADIUS_RELAXED_EMPTY_RESULT_KM
                : SEARCH_RADIUS_RELAXED_FEW_RESULT_KM;
        int relaxedBudgetYuan = queryContext.budgetYuan() + relaxedBudgetDelta;

        StageSearchResult relaxedStage = searchStage(
                queryContext,
                relaxedBudgetYuan,
                relaxedRadiusKm,
                exactStage.esQueryTimedOut()
        );
        List<House> mergedCandidates = mergeCandidates(exactStage.candidates(), relaxedStage.candidates());

        SmartGuideCandidateResult candidateResult = SmartGuideCandidateResult.relaxed(
                mergedCandidates,
                relaxedBudgetYuan,
                exactStage.candidates().size()
        );
        return new WorkflowSearchResult(candidateResult, exactStage.esAvailable() && relaxedStage.esAvailable());
    }

    /**
     * 执行单轮搜索。
     * 先让 ES 召回候选 houseId，再让 DB 根据这些 houseId 做最终校验；若 ES 不可用则直接降级到 DB。
     */
    private StageSearchResult searchStage(SmartGuideQueryContext queryContext,
                                          int filterBudgetYuan,
                                          double radiusKm,
                                          boolean skipEsPrefilter) {
        if (skipEsPrefilter) {
            log.info("Smart guide skip ES prefilter for relaxed stage because exact stage timed out, location={}, budget={}, radiusKm={}",
                    queryContext.locationName(), filterBudgetYuan, radiusKm);
            List<House> candidates = queryCandidatesFallback(queryContext, filterBudgetYuan, radiusKm);
            return new StageSearchResult(candidates, false, false);
        }

        SmartGuidePrefilterResult prefilterResult = queryCandidateIdsFromEs(queryContext, filterBudgetYuan, radiusKm);
        List<House> candidates;
        if (prefilterResult.esAvailable()) {
            candidates = queryCandidatesFromDb(queryContext, filterBudgetYuan, radiusKm, prefilterResult.candidateIds());
            if (candidates.isEmpty()) {
                log.warn("Smart guide ES returned no effective candidates, fallback to DB scan, location={}, budget={}, radiusKm={}",
                        queryContext.locationName(), filterBudgetYuan, radiusKm);
                candidates = queryCandidatesFallback(queryContext, filterBudgetYuan, radiusKm);
            }
        } else {
            candidates = queryCandidatesFallback(queryContext, filterBudgetYuan, radiusKm);
        }
        return new StageSearchResult(candidates, prefilterResult.esAvailable(), prefilterResult.esQueryTimedOut());
    }

    /**
     * 组装返回结果。
     * 这里负责打分、排序、分页，并根据是否放宽和是否命中预算附近房源生成提示文案。
     */
    private SmartGuideResultVO buildResult(SmartGuideQueryContext queryContext,
                                           WorkflowSearchResult workflowResult) {
        SmartGuideCandidateResult candidateResult = workflowResult.candidateResult();
        SmartGuideResultVO result = new SmartGuideResultVO();
        result.setOriginalBudgetYuan(queryContext.budgetYuan());
        result.setRelaxedBudget(candidateResult.relaxedBudget());
        if (candidateResult.relaxedBudget()) {
            result.setRelaxedBudgetYuan(candidateResult.relaxedBudgetYuan());
        }

        List<SmartGuideItemVO> rankedItems = workflowResult.candidateResult().candidates().stream()
                .map(house -> buildItem(house, queryContext))
                .sorted(Comparator.comparing(SmartGuideItemVO::getScore).reversed())
                .collect(Collectors.toList());

        List<House> exactCandidates = candidateResult.candidates().subList(
                0,
                Math.min(candidateResult.exactMatchCount(), candidateResult.candidates().size())
        );
        boolean matchedExpectation = !exactCandidates.isEmpty()
                && hasBudgetCloseCandidate(exactCandidates, queryContext);

        result.setMatchedExpectation(matchedExpectation);
        result.setRecommendations(paginateItems(rankedItems, queryContext.page(), queryContext.size()));
        result.setTipMessage(resolveTipMessage(queryContext, workflowResult.esAvailable(), candidateResult, matchedExpectation));
        return result;
    }

    /**
     * 对排序后的房源做内存分页。
     * 候选列表已经不大，直接切片即可。
     */
    private List<SmartGuideItemVO> paginateItems(List<SmartGuideItemVO> rankedItems, int page, int size) {
        int start = Math.max((page - 1) * size, 0);
        if (start >= rankedItems.size()) {
            return new ArrayList<>();
        }
        int end = Math.min(start + size, rankedItems.size());
        return new ArrayList<>(rankedItems.subList(start, end));
    }

    /**
     * 生成给前端展示的提示语。
     * 会综合 ES 是否降级、是否放宽预算、是否只是补充式放宽等情况给出解释。
     */
    private String resolveTipMessage(SmartGuideQueryContext queryContext,
                                     boolean esAvailable,
                                     SmartGuideCandidateResult candidateResult,
                                     boolean matchedExpectation) {
        List<String> tips = new ArrayList<>(2);
        if (!esAvailable) {
            tips.add(TIP_ES_DEGRADED);
        }

        if (candidateResult.relaxedBudget()) {
            if (candidateResult.exactMatchCount() == 0 && !candidateResult.candidates().isEmpty()) {
                tips.add(TIP_RELAXED_FROM_EMPTY);
            } else if (candidateResult.candidates().size() > candidateResult.exactMatchCount()) {
                tips.add(TIP_RELAXED_FROM_FEW);
            } else {
                tips.add(TIP_RELAXED_NO_EXTRA);
            }
        } else if (!matchedExpectation && !candidateResult.candidates().isEmpty()) {
            tips.add(TIP_FEW_EXACT);
        }

        if (tips.isEmpty()) {
            tips.add(TIP_MATCHED);
        }
        return String.join(" ", tips);
    }

    /**
     * 判断严格条件下是否存在“足够贴近预算”的房源。
     * 这个结果用于区分“命中预期”还是“虽然有结果，但都离预算比较远”。
     */
    private boolean hasBudgetCloseCandidate(List<House> candidates, SmartGuideQueryContext queryContext) {
        if (candidates == null || candidates.isEmpty() || queryContext.budgetCent() <= 0) {
            return false;
        }

        int toleranceCent = Math.max(
                BUDGET_CLOSE_MIN_DIFF_YUAN * 100,
                (int) Math.round(queryContext.budgetCent() * BUDGET_CLOSE_RATIO)
        );

        for (House candidate : candidates) {
            int comparableCostCent = resolveComparableCostCent(candidate, queryContext.totalCostScope());
            if (comparableCostCent <= 0) {
                continue;
            }
            int diffCent = queryContext.budgetCent() - comparableCostCent;
            if (diffCent >= 0 && diffCent <= toleranceCent) {
                return true;
            }
        }
        return false;
    }

    /**
     * 使用 ES 做第一轮候选召回。
     * ES 只负责快速按状态、租住方式关键词、预算上限和地理范围召回 houseId，最终真实性以 DB 为准。
     */
    private SmartGuidePrefilterResult queryCandidateIdsFromEs(SmartGuideQueryContext queryContext,
                                                              int filterBudgetYuan,
                                                              double radiusKm) {
        try {
            List<Long> candidateIds = CompletableFuture.supplyAsync(() -> {
                        int coarseBudgetCent = filterBudgetYuan * 100;
                        Integer rentTypeCode = resolveRentTypeCode(queryContext.rentMode());
                        String budgetField = queryContext.totalCostScope() ? "totalCost" : "price";
                        Query boolQuery = Query.of(q -> q.bool(b -> {
                            b.must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)));
                            if (rentTypeCode != null) {
                                b.must(m -> m.term(t -> t.field("rentType").value(rentTypeCode)));
                            } else if (StringUtils.hasText(queryContext.rentKeyword())) {
                                b.must(m -> m.match(mm -> mm.field("title").query(queryContext.rentKeyword())));
                            }
                            b.must(m -> m.range(r -> r.number(n -> n.field(budgetField).lte((double) coarseBudgetCent))));
                            b.filter(f -> f.geoDistance(g -> g
                                    .field("location")
                                    .distance(radiusKm + "km")
                                    .location(loc -> loc.latlon(ll -> ll
                                            .lat(queryContext.targetLatitude())
                                            .lon(queryContext.targetLongitude())))
                            ));
                            return b;
                        }));

                        SortOptions geoSort = SortOptions.of(s -> s.geoDistance(g -> g
                                .field("location")
                                .location(loc -> loc.latlon(ll -> ll
                                        .lat(queryContext.targetLatitude())
                                        .lon(queryContext.targetLongitude())))
                                .order(SortOrder.Asc)
                                .unit(DistanceUnit.Meters)
                        ));

                        NativeQuery nativeQuery = NativeQuery.builder()
                                .withQuery(boolQuery)
                                .withSort(geoSort)
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

            log.info("Smart guide ES prefilter finished, location={}, budget={}, radiusKm={}, candidateCount={}",
                    queryContext.locationName(), filterBudgetYuan, radiusKm, candidateIds.size());
            return SmartGuidePrefilterResult.esAvailable(candidateIds);
        } catch (TimeoutException te) {
            log.warn("Smart guide ES prefilter timed out ({}ms), downgrade to DB", ES_QUERY_TIMEOUT_MS);
            return SmartGuidePrefilterResult.timedOut();
        } catch (Exception e) {
            log.error("Smart guide ES prefilter failed, downgrade to DB", e);
        }
        return SmartGuidePrefilterResult.esUnavailable();
    }

    /**
     * 根据 ES 返回的候选 houseId 去 DB 做二次查询。
     * 这里会重新校验状态、预算、距离等关键条件，防止 ES 存在同步延迟。
     */
    private List<House> queryCandidatesFromDb(SmartGuideQueryContext queryContext,
                                              int filterBudgetYuan,
                                              double radiusKm,
                                              List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            log.warn("Smart guide ES candidateIds empty, will fallback to DB scan, location={}, budget={}, radiusKm={}",
                    queryContext.locationName(), filterBudgetYuan, radiusKm);
            return List.of();
        }

        List<Long> filteredIds = queryCandidateIdsFromDb(
                queryContext,
                filterBudgetYuan,
                radiusKm,
                candidateIds,
                SMART_GUIDE_MAX_CANDIDATES
        );
        Map<Long, Integer> orderMap = new LinkedHashMap<>();
        for (int i = 0; i < candidateIds.size(); i++) {
            orderMap.put(candidateIds.get(i), i);
        }
        filteredIds.sort(Comparator.comparingInt(id -> orderMap.getOrDefault(id, Integer.MAX_VALUE)));
        return loadHousesByIdsInOrder(filteredIds);
    }

    /**
     * 当 ES 不可用时，直接走 DB 兜底扫描。
     * 先做一个相对宽松的 DB 粗筛，再在内存里复用统一的候选判断逻辑。
     */
    private List<House> queryCandidatesFallback(SmartGuideQueryContext queryContext,
                                                int filterBudgetYuan,
                                                double radiusKm) {
        List<Long> filteredIds = queryCandidateIdsFromDb(
                queryContext,
                filterBudgetYuan,
                radiusKm,
                null,
                SMART_GUIDE_DB_FALLBACK_SCAN_SIZE
        );
        return loadHousesByIdsInOrder(filteredIds);
    }

    /**
     * 判断单个房源是否满足当前轮次的过滤条件。
     * 这是 DB 二查和 DB 降级共用的一套最终判定逻辑。
     */
    private List<Long> queryCandidateIdsFromDb(SmartGuideQueryContext queryContext,
                                               int filterBudgetYuan,
                                               double radiusKm,
                                               List<Long> candidateIds,
                                               int limit) {
        BoundingBox boundingBox = buildBoundingBox(queryContext.targetLatitude(), queryContext.targetLongitude(), radiusKm);
        return houseMapper.selectSmartGuideCandidateIds(
                candidateIds,
                HOUSE_STATUS_AVAILABLE,
                resolveRentTypeCode(queryContext.rentMode()),
                queryContext.totalCostScope(),
                filterBudgetYuan * 100,
                queryContext.targetLatitude(),
                queryContext.targetLongitude(),
                boundingBox.minLatitude(),
                boundingBox.maxLatitude(),
                boundingBox.minLongitude(),
                boundingBox.maxLongitude(),
                radiusKm,
                limit
        );
    }

    /**
     * 判断房源是否匹配整租/合租条件。
     * 当前先按标题关键词做临时判断，后面如果补 rent_type 字段，这里可以直接切换。
     */
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

    /**
     * 合并严格结果与放宽结果。
     * 先保留严格命中的房源，再补充放宽阶段新增的房源，同时按 houseId 去重。
     */
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

    /**
     * 把 House 实体转换成前端需要的推荐条目对象。
     * 这里会顺手补充押金、首月总成本、距离、通勤分钟数和推荐理由。
     */
    private SmartGuideItemVO buildItem(House house, SmartGuideQueryContext queryContext) {
        SmartGuideItemVO item = new SmartGuideItemVO();
        item.setHouseId(house.getId());
        item.setPublisherUserId(house.getPublisherUserId());
        item.setTitle(house.getTitle());
        item.setStatus(house.getStatus());
        item.setPrice(convertCentToYuan(house.getPrice()));
        item.setDepositAmount(convertCentToYuan(house.getDepositAmount()));
        item.setTotalCost(convertCentToYuan(resolveComparableCostCent(house, true)));

        SmartGuideScoreCalculator.SmartGuideScoreResult scoreResult =
                smartGuideScoreCalculator.calculate(house, queryContext);
        item.setDistanceToMetroKm(scoreResult.getDistanceToMetroKm());
        item.setEstimatedCommuteMinutes(scoreResult.getEstimatedCommuteMinutes());
        item.setReasons(scoreResult.getReasons());
        item.setScore(scoreResult.getScore());
        return item;
    }

    /**
     * 校验 smart-guide 请求是否合法。
     * 当前重点校验预算口径、租住方式以及地点名称是否存在。
     */
    private void validateRequest(SmartGuideReqDTO reqDTO) {
        String budgetScope = normalizeEnumValue(reqDTO.getBudgetScope());
        if (!BUDGET_SCOPE_RENT_ONLY.equals(budgetScope) && !BUDGET_SCOPE_TOTAL.equals(budgetScope)) {
            throw new IllegalArgumentException("budgetScope only supports RENT_ONLY or TOTAL");
        }

        String rentMode = normalizeEnumValue(reqDTO.getRentMode());
        if (!RENT_MODE_WHOLE.equals(rentMode) && !RENT_MODE_SHARED.equals(rentMode)) {
            throw new IllegalArgumentException("rentMode only supports WHOLE or SHARED");
        }

        if (!StringUtils.hasText(resolveRequestedLocationName(reqDTO))) {
            throw new IllegalArgumentException("locationName cannot be blank");
        }
    }

    /**
     * 根据地点名从 location_dict 中解析出坐标。
     * 先做统一标准化，再按“完全匹配优先、包含匹配次之”的规则选出最合适的点位。
     */
    private ResolvedLocation resolveLocation(String requestedLocationName) {
        List<LocationDict> locations = locationDictMapper.selectList(Wrappers.emptyWrapper());
        if (locations == null || locations.isEmpty()) {
            throw new IllegalArgumentException("location_dict has no test data");
        }

        //用户输入的地址名称
        String normalizedInput = normalizeText(requestedLocationName);
        return locations.stream()
                .map(location -> new ResolvedLocation(
                        //数据库的地址名称
                        location.getName(),
                        location.getLatitude() == null ? null : location.getLatitude().doubleValue(),
                        location.getLongitude() == null ? null : location.getLongitude().doubleValue(),
                        calculateLocationMatchScore(normalizedInput, normalizeText(location.getName()))
                ))
                .filter(location -> location.latitude() != null && location.longitude() != null && location.matchScore() > 0)
                .max(Comparator.comparingInt(ResolvedLocation::matchScore))
                .orElseThrow(() -> new IllegalArgumentException("locationName is not found in location_dict"));
    }

    /**
     * 计算地点名匹配分。
     * 完全匹配最高，其次是包含匹配；当前实现足够支撑测试环境下的小字典表。
     */
    private int calculateLocationMatchScore(String input, String candidate) {
        if (!StringUtils.hasText(input) || !StringUtils.hasText(candidate)) {
            return 0;
        }
        if (candidate.equals(input)) {
            return 1000;
        }
        if (candidate.contains(input) || input.contains(candidate)) {
            return 800 - Math.abs(candidate.length() - input.length());
        }
        return 0;
    }

    /**
     * 兼容新老前端字段。
     * V2 优先使用 locationName，旧版仍然可以继续传 commuteMetroStation。
     */
    private String resolveRequestedLocationName(SmartGuideReqDTO reqDTO) {
        if (StringUtils.hasText(reqDTO.getLocationName())) {
            return reqDTO.getLocationName().trim();
        }
        return reqDTO.getCommuteMetroStation() == null ? "" : reqDTO.getCommuteMetroStation().trim();
    }

    /**
     * 把整租/合租枚举值转换成当前临时使用的标题关键词。
     */
    private String resolveRentKeyword(String rentMode) {
        return RENT_MODE_WHOLE.equals(normalizeEnumValue(rentMode)) ? RENT_KEYWORD_WHOLE : RENT_KEYWORD_SHARED;
    }

    /**
     * 统一标准化枚举类输入，避免大小写和首尾空格带来的判断问题。
     */
    private String normalizeEnumValue(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 统一标准化普通文本输入，便于做地点名匹配。
     */
    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 按预算口径计算房源的可比较成本。
     * RENT_ONLY 返回月租，TOTAL 返回首月总成本。
     */
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

    /**
     * 计算房源到目标地点的直线距离，单位为公里。
     */
    private Double calculateDistanceKm(House house, SmartGuideQueryContext queryContext) {
        if (house == null || house.getLatitude() == null || house.getLongitude() == null) {
            return null;
        }
        return haversine(house.getLatitude().doubleValue(),
                house.getLongitude().doubleValue(),
                queryContext.targetLatitude(),
                queryContext.targetLongitude());
    }

    /**
     * 使用 Haversine 公式计算两组经纬度的球面距离。
     */
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double radiusKm = 6371.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return radiusKm * c;
    }

    /**
     * 把数据库中的分转换成元，供返回对象展示。
     */
    private BigDecimal convertCentToYuan(Integer cent) {
        if (cent == null) {
            return null;
        }
        return new BigDecimal(cent).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    private record ResolvedLocation(String name, Double latitude, Double longitude, int matchScore) {
    }

    private record BoundingBox(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
    }

    private record StageSearchResult(List<House> candidates, boolean esAvailable, boolean esQueryTimedOut) {
    }

    private record WorkflowSearchResult(SmartGuideCandidateResult candidateResult, boolean esAvailable) {
    }
}

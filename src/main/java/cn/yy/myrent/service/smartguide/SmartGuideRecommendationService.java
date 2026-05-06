package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.service.score.SmartGuideScoreCalculator;
import cn.yy.myrent.vo.SmartGuideItemVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SmartGuideRecommendationService {

    private static final String BUDGET_SCOPE_RENT_ONLY = "RENT_ONLY";
    private static final String BUDGET_SCOPE_TOTAL = "TOTAL";
    private static final String RENT_MODE_WHOLE = "WHOLE";
    private static final String RENT_MODE_SHARED = "SHARED";

    private static final int SMART_GUIDE_MAX_CANDIDATES = 200;
    private static final int BUDGET_CLOSE_MIN_DIFF_YUAN = 500;
    private static final double BUDGET_CLOSE_RATIO = 0.15d;

    private static final String RENT_KEYWORD_WHOLE = "整租";
    private static final String RENT_KEYWORD_SHARED = "合租";

    private static final String TIP_ES_DEGRADED = "ES 预筛暂不可用，当前结果已降级为 DB 二次筛选。";
    private static final String TIP_MATCHED = "已找到符合条件的房源，并按综合评分排序。";
    private static final String TIP_RELAXED_FROM_EMPTY = "未找到完全符合条件的房源，已放宽预算和搜索范围展示备选结果。";
    private static final String TIP_RELAXED_FROM_FEW = "完全符合条件的房源较少，已补充放宽条件后的备选结果。";
    private static final String TIP_RELAXED_NO_EXTRA = "完全符合条件的房源较少，已尝试放宽条件，但当前结果仍然有限。";
    private static final String TIP_FEW_EXACT = "已找到符合条件的房源，但更贴近预算的结果较少。";

    private final SmartGuideCandidateCollector candidateCollector;
    private final LocationResolveService locationResolveService;
    private final SmartGuideScoreCalculator smartGuideScoreCalculator;

    public SmartGuideResultVO recommend(SmartGuideReqDTO reqDTO) {
        validateRequest(reqDTO);
        SmartGuideQueryContext queryContext = buildQueryContext(reqDTO);
        SmartGuideCandidateCollector.RecommendationCollectionResult collected = candidateCollector.collectRecommendation(
                SmartGuideCandidateQuery.builder()
                        .locationName(queryContext.locationName())
                        .budgetYuan(queryContext.budgetYuan())
                        .budgetScope(queryContext.budgetScope())
                        .rentMode(queryContext.rentMode())
                        .size(SMART_GUIDE_MAX_CANDIDATES)
                        .build()
        );
        WorkflowSearchResult workflowResult = new WorkflowSearchResult(
                collected.candidateResult(),
                collected.bundle().esAvailable()
        );
        return buildResult(queryContext, workflowResult);
    }

    private SmartGuideQueryContext buildQueryContext(SmartGuideReqDTO reqDTO) {
        int page = reqDTO.getPage() == null ? 1 : reqDTO.getPage();
        int size = reqDTO.getSize() == null ? 10 : reqDTO.getSize();
        int budgetYuan = reqDTO.getBudgetYuan();
        String budgetScope = normalizeEnumValue(reqDTO.getBudgetScope());
        String rentMode = normalizeEnumValue(reqDTO.getRentMode());
        String rentKeyword = resolveRentKeyword(rentMode);
        String requestedLocationName = resolveRequestedLocationName(reqDTO);
        LocationResolveService.ResolvedLocation resolvedLocation = locationResolveService.resolveRequired(requestedLocationName);
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

    private SmartGuideResultVO buildResult(SmartGuideQueryContext queryContext,
                                           WorkflowSearchResult workflowResult) {
        SmartGuideCandidateResult candidateResult = workflowResult.candidateResult();
        SmartGuideResultVO result = new SmartGuideResultVO();
        result.setOriginalBudgetYuan(queryContext.budgetYuan());
        result.setRelaxedBudget(candidateResult.relaxedBudget());
        if (candidateResult.relaxedBudget()) {
            result.setRelaxedBudgetYuan(candidateResult.relaxedBudgetYuan());
        }

        List<SmartGuideItemVO> rankedItems = candidateResult.candidates().stream()
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
        result.setTipMessage(resolveTipMessage(workflowResult.esAvailable(), candidateResult, matchedExpectation));
        return result;
    }

    private List<SmartGuideItemVO> paginateItems(List<SmartGuideItemVO> rankedItems, int page, int size) {
        int start = Math.max((page - 1) * size, 0);
        if (start >= rankedItems.size()) {
            return new ArrayList<>();
        }
        int end = Math.min(start + size, rankedItems.size());
        return new ArrayList<>(rankedItems.subList(start, end));
    }

    private String resolveTipMessage(boolean esAvailable,
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

    private String resolveRequestedLocationName(SmartGuideReqDTO reqDTO) {
        if (StringUtils.hasText(reqDTO.getLocationName())) {
            return reqDTO.getLocationName().trim();
        }
        return reqDTO.getCommuteMetroStation() == null ? "" : reqDTO.getCommuteMetroStation().trim();
    }

    private String resolveRentKeyword(String rentMode) {
        return RENT_MODE_WHOLE.equals(normalizeEnumValue(rentMode)) ? RENT_KEYWORD_WHOLE : RENT_KEYWORD_SHARED;
    }

    private String normalizeEnumValue(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
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

    private BigDecimal convertCentToYuan(Integer cent) {
        if (cent == null) {
            return null;
        }
        return new BigDecimal(cent).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    private record WorkflowSearchResult(SmartGuideCandidateResult candidateResult, boolean esAvailable) {
    }
}

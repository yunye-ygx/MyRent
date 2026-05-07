package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.discovery.HouseRankQuery;
import cn.yy.myrent.service.discovery.HouseRankResult;
import cn.yy.myrent.service.discovery.HouseRankedItem;
import cn.yy.myrent.service.discovery.HouseRankingProfile;
import cn.yy.myrent.service.discovery.HouseRankingService;
import cn.yy.myrent.service.discovery.HouseReasonCode;
import cn.yy.myrent.service.discovery.HouseRecallCandidate;
import cn.yy.myrent.service.discovery.HouseRecallEvidence;
import cn.yy.myrent.service.discovery.HouseRecallProfile;
import cn.yy.myrent.service.discovery.HouseRecallQuery;
import cn.yy.myrent.service.discovery.HouseRecallResult;
import cn.yy.myrent.service.discovery.HouseRecallService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.vo.SmartGuideItemVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static final String TIP_ES_DEGRADED =
            "\u7531\u4e8e ES \u9884\u7b5b\u6682\u4e0d\u53ef\u7528\uff0c\u5f53\u524d\u7ed3\u679c\u5df2\u964d\u7ea7\u4e3a DB \u4e8c\u6b21\u7b5b\u9009\u3002";
    private static final String TIP_MATCHED =
            "\u5df2\u627e\u5230\u7b26\u5408\u6761\u4ef6\u7684\u623f\u6e90\uff0c\u5e76\u6309\u7efc\u5408\u8bc4\u5206\u6392\u5e8f\u3002";
    private static final String TIP_RELAXED_FROM_EMPTY =
            "\u672a\u627e\u5230\u5b8c\u5168\u7b26\u5408\u6761\u4ef6\u7684\u623f\u6e90\uff0c\u5df2\u653e\u5bbd\u9884\u7b97\u548c\u641c\u7d22\u8303\u56f4\u5c55\u793a\u5907\u9009\u7ed3\u679c\u3002";
    private static final String TIP_RELAXED_FROM_FEW =
            "\u5b8c\u5168\u7b26\u5408\u6761\u4ef6\u7684\u623f\u6e90\u8f83\u5c11\uff0c\u5df2\u8865\u5145\u653e\u5bbd\u6761\u4ef6\u540e\u7684\u5907\u9009\u7ed3\u679c\u3002";
    private static final String TIP_RELAXED_NO_EXTRA =
            "\u5b8c\u5168\u7b26\u5408\u6761\u4ef6\u7684\u623f\u6e90\u8f83\u5c11\uff0c\u5df2\u5c1d\u8bd5\u653e\u5bbd\u6761\u4ef6\uff0c\u4f46\u5f53\u524d\u7ed3\u679c\u4ecd\u7136\u6709\u9650\u3002";
    private static final String TIP_FEW_EXACT =
            "\u5df2\u627e\u5230\u7b26\u5408\u6761\u4ef6\u7684\u623f\u6e90\uff0c\u4f46\u66f4\u8d34\u8fd1\u9884\u7b97\u7684\u7ed3\u679c\u8f83\u5c11\u3002";

    private static final Map<HouseReasonCode, String> REASON_TEXT_MAP = buildReasonTextMap();

    private final HouseRecallService houseRecallService;
    private final HouseRankingService houseRankingService;
    private final LocationResolveService locationResolveService;

    public SmartGuideResultVO recommend(SmartGuideReqDTO reqDTO) {
        validateRequest(reqDTO);
        SmartGuideQueryContext queryContext = buildQueryContext(reqDTO);

        HouseRecallResult recallResult = houseRecallService.recall(HouseRecallQuery.builder()
                .locationName(queryContext.locationName())
                .budgetYuan(queryContext.budgetYuan())
                .budgetScope(queryContext.budgetScope())
                .rentMode(queryContext.rentMode())
                .page(1)
                .size(SMART_GUIDE_MAX_CANDIDATES)
                .recallProfile(HouseRecallProfile.SMART_GUIDE)
                .build());

        HouseRankResult rankResult = houseRankingService.rank(
                recallResult.candidates(),
                HouseRankQuery.builder()
                        .budgetYuan(queryContext.budgetYuan())
                        .budgetScope(queryContext.budgetScope())
                        .rentMode(resolveRankingRentMode(queryContext.rentMode()))
                        .page(queryContext.page())
                        .size(queryContext.size())
                        .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                        .build()
        );

        Map<Long, HouseRecallEvidence> evidenceByHouseId = buildEvidenceByHouseId(recallResult.candidates());
        return buildResult(queryContext, recallResult, rankResult, evidenceByHouseId);
    }

    private SmartGuideQueryContext buildQueryContext(SmartGuideReqDTO reqDTO) {
        int page = reqDTO.getPage() == null ? 1 : reqDTO.getPage();
        int size = reqDTO.getSize() == null ? 10 : reqDTO.getSize();
        int budgetYuan = reqDTO.getBudgetYuan();
        String budgetScope = normalizeEnumValue(reqDTO.getBudgetScope());
        String rentMode = normalizeEnumValue(reqDTO.getRentMode());
        String requestedLocationName = resolveRequestedLocationName(reqDTO);
        LocationResolveService.ResolvedLocation resolvedLocation = locationResolveService.resolveRequired(requestedLocationName);
        return new SmartGuideQueryContext(
                page,
                size,
                budgetYuan,
                budgetYuan * 100,
                budgetScope,
                rentMode,
                null,
                resolvedLocation.name(),
                resolvedLocation.latitude(),
                resolvedLocation.longitude()
        );
    }

    private SmartGuideResultVO buildResult(SmartGuideQueryContext queryContext,
                                           HouseRecallResult recallResult,
                                           HouseRankResult rankResult,
                                           Map<Long, HouseRecallEvidence> evidenceByHouseId) {
        SmartGuideResultVO result = new SmartGuideResultVO();
        result.setOriginalBudgetYuan(queryContext.budgetYuan());

        boolean relaxedBudgetApplied = hasRelaxedBudgetApplied(recallResult.candidates());
        result.setRelaxedBudget(relaxedBudgetApplied);
        if (relaxedBudgetApplied) {
            result.setRelaxedBudgetYuan(resolveRelaxedBudgetYuan(queryContext, recallResult.candidates()));
        }

        int exactMatchCount = countExactMatches(recallResult.candidates());
        boolean matchedExpectation = hasBudgetCloseCandidate(
                collectExactMatchHouses(recallResult.candidates()),
                queryContext
        );

        result.setMatchedExpectation(matchedExpectation);
        result.setRecommendations(mapItems(rankResult.currentPageItems(), queryContext, evidenceByHouseId));
        result.setTipMessage(resolveTipMessage(
                recallResult.esAvailable(),
                relaxedBudgetApplied,
                hasRelaxedRadiusApplied(recallResult.candidates()),
                exactMatchCount,
                rankResult.rankedItems().size(),
                matchedExpectation
        ));
        return result;
    }

    private Map<Long, HouseRecallEvidence> buildEvidenceByHouseId(List<HouseRecallCandidate> candidates) {
        Map<Long, HouseRecallEvidence> evidenceByHouseId = new LinkedHashMap<>();
        for (HouseRecallCandidate candidate : candidates) {
            if (candidate == null || candidate.house() == null || candidate.house().getId() == null) {
                continue;
            }
            evidenceByHouseId.put(candidate.house().getId(), candidate.recallEvidence());
        }
        return evidenceByHouseId;
    }

    private List<SmartGuideItemVO> mapItems(List<HouseRankedItem> rankedItems,
                                            SmartGuideQueryContext queryContext,
                                            Map<Long, HouseRecallEvidence> evidenceByHouseId) {
        List<SmartGuideItemVO> items = new ArrayList<>(rankedItems.size());
        for (HouseRankedItem rankedItem : rankedItems) {
            items.add(buildItem(rankedItem, queryContext, evidenceByHouseId.get(rankedItem.house().getId())));
        }
        return items;
    }

    private String resolveTipMessage(boolean esAvailable,
                                     boolean relaxedBudgetApplied,
                                     boolean relaxedRadiusApplied,
                                     int exactMatchCount,
                                     int rankedCount,
                                     boolean matchedExpectation) {
        List<String> tips = new ArrayList<>(2);
        if (!esAvailable) {
            tips.add(TIP_ES_DEGRADED);
        }

        if (relaxedBudgetApplied || relaxedRadiusApplied) {
            if (exactMatchCount == 0 && rankedCount > 0) {
                tips.add(TIP_RELAXED_FROM_EMPTY);
            } else if (rankedCount > exactMatchCount) {
                tips.add(TIP_RELAXED_FROM_FEW);
            } else {
                tips.add(TIP_RELAXED_NO_EXTRA);
            }
        } else if (!matchedExpectation && rankedCount > 0) {
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

    private SmartGuideItemVO buildItem(HouseRankedItem rankedItem,
                                       SmartGuideQueryContext queryContext,
                                       HouseRecallEvidence evidence) {
        House house = rankedItem.house();
        SmartGuideItemVO item = new SmartGuideItemVO();
        item.setHouseId(house.getId());
        item.setPublisherUserId(house.getPublisherUserId());
        item.setTitle(house.getTitle());
        item.setStatus(house.getStatus());
        item.setPrice(convertCentToYuan(house.getPrice()));
        item.setDepositAmount(convertCentToYuan(house.getDepositAmount()));
        item.setTotalCost(convertCentToYuan(resolveComparableCostCent(house, true)));
        item.setDistanceToMetroKm(convertDistanceMetersToKm(evidence == null ? null : evidence.locationDistanceMeters()));
        item.setEstimatedCommuteMinutes(estimateCommuteMinutes(evidence == null ? null : evidence.locationDistanceMeters()));
        item.setReasons(mapReasons(rankedItem.reasonCodes(), queryContext, evidence));
        item.setScore(BigDecimal.valueOf(rankedItem.score()).setScale(3, RoundingMode.HALF_UP));
        return item;
    }

    private List<String> mapReasons(List<HouseReasonCode> reasonCodes,
                                    SmartGuideQueryContext queryContext,
                                    HouseRecallEvidence evidence) {
        List<String> reasons = new ArrayList<>(3);
        if (reasonCodes != null) {
            for (HouseReasonCode reasonCode : reasonCodes) {
                String mapped = mapReason(reasonCode, queryContext, evidence);
                if (mapped != null && !reasons.contains(mapped)) {
                    reasons.add(mapped);
                }
                if (reasons.size() >= 3) {
                    break;
                }
            }
        }
        if (reasons.isEmpty() && evidence != null && evidence.locationDistanceMeters() != null) {
            reasons.add("\u8ddd\u76ee\u6807\u5730\u70b9\u7ea6 "
                    + convertDistanceMetersToKm(evidence.locationDistanceMeters()).stripTrailingZeros().toPlainString()
                    + "km");
        }
        return reasons;
    }

    private String mapReason(HouseReasonCode reasonCode,
                             SmartGuideQueryContext queryContext,
                             HouseRecallEvidence evidence) {
        if (reasonCode == null) {
            return null;
        }
        if (reasonCode == HouseReasonCode.LOCATION_DISTANCE_ADVANTAGE && evidence != null && evidence.locationDistanceMeters() != null) {
            BigDecimal distanceKm = convertDistanceMetersToKm(evidence.locationDistanceMeters());
            return "\u8ddd\u76ee\u6807\u5730\u70b9\u7ea6 " + distanceKm.stripTrailingZeros().toPlainString() + "km";
        }
        if (reasonCode == HouseReasonCode.BUDGET_CLOSE_MATCH) {
            return queryContext.totalCostScope()
                    ? "\u9996\u6708\u603b\u6210\u672c\u8d34\u8fd1\u9884\u7b97"
                    : "\u6708\u79df\u8d34\u8fd1\u9884\u7b97";
        }
        return REASON_TEXT_MAP.get(reasonCode);
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

    private BigDecimal convertDistanceMetersToKm(Double distanceMeters) {
        if (distanceMeters == null) {
            return null;
        }
        return BigDecimal.valueOf(distanceMeters)
                .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    }

    private Integer estimateCommuteMinutes(Double distanceMeters) {
        if (distanceMeters == null) {
            return null;
        }
        double distanceKm = distanceMeters / 1000.0d;
        return Math.max(8, (int) Math.round(distanceKm * 4.5d + 6d));
    }

    private boolean hasRelaxedBudgetApplied(List<HouseRecallCandidate> candidates) {
        return candidates.stream()
                .map(HouseRecallCandidate::recallEvidence)
                .anyMatch(evidence -> evidence != null && evidence.relaxedBudgetApplied());
    }

    private boolean hasRelaxedRadiusApplied(List<HouseRecallCandidate> candidates) {
        return candidates.stream()
                .map(HouseRecallCandidate::recallEvidence)
                .anyMatch(evidence -> evidence != null && evidence.relaxedRadiusApplied());
    }

    private int countExactMatches(List<HouseRecallCandidate> candidates) {
        int exactCount = 0;
        for (HouseRecallCandidate candidate : candidates) {
            HouseRecallEvidence evidence = candidate == null ? null : candidate.recallEvidence();
            if (evidence != null && evidence.exactConstraintMatched()) {
                exactCount += 1;
            }
        }
        return exactCount;
    }

    private List<House> collectExactMatchHouses(List<HouseRecallCandidate> candidates) {
        List<House> exactHouses = new ArrayList<>();
        for (HouseRecallCandidate candidate : candidates) {
            HouseRecallEvidence evidence = candidate == null ? null : candidate.recallEvidence();
            if (candidate != null && candidate.house() != null && evidence != null && evidence.exactConstraintMatched()) {
                exactHouses.add(candidate.house());
            }
        }
        return exactHouses;
    }

    private int resolveRelaxedBudgetYuan(SmartGuideQueryContext queryContext,
                                         List<HouseRecallCandidate> recallCandidates) {
        int maxComparableCent = queryContext.budgetCent();
        for (HouseRecallCandidate candidate : recallCandidates) {
            if (candidate == null || candidate.house() == null) {
                continue;
            }
            HouseRecallEvidence evidence = candidate.recallEvidence();
            if (evidence == null || !evidence.relaxedBudgetApplied()) {
                continue;
            }
            int comparableCent = resolveComparableCostCent(candidate.house(), queryContext.totalCostScope());
            maxComparableCent = Math.max(maxComparableCent, comparableCent);
        }
        return (int) Math.ceil(maxComparableCent / 100.0d);
    }

    private String resolveRankingRentMode(String rentMode) {
        if (RENT_MODE_WHOLE.equals(rentMode)) {
            return "1";
        }
        if (RENT_MODE_SHARED.equals(rentMode)) {
            return "2";
        }
        return null;
    }

    private static Map<HouseReasonCode, String> buildReasonTextMap() {
        Map<HouseReasonCode, String> map = new EnumMap<>(HouseReasonCode.class);
        map.put(HouseReasonCode.RECALL_LOCATION_MATCH, "\u533a\u57df\u6216\u901a\u52e4\u5730\u70b9\u5339\u914d");
        map.put(HouseReasonCode.RECALL_TEXT_MATCH, "\u6807\u9898\u5173\u952e\u8bcd\u5339\u914d");
        map.put(HouseReasonCode.TEXT_RELEVANCE_ADVANTAGE, "\u6587\u672c\u76f8\u5173\u5ea6\u8f83\u9ad8");
        map.put(HouseReasonCode.RENT_MODE_MATCH, "\u79df\u4f4f\u65b9\u5f0f\u5339\u914d");
        map.put(HouseReasonCode.NEAR_SUBWAY_MATCH, "\u8fd1\u5730\u94c1\u51fa\u884c\u66f4\u65b9\u4fbf");
        map.put(HouseReasonCode.PRIVATE_BATHROOM_MATCH, "\u72ec\u7acb\u536b\u6d74\u4f7f\u7528\u66f4\u65b9\u4fbf");
        map.put(HouseReasonCode.HAS_BALCONY_MATCH, "\u5e26\u9633\u53f0\uff0c\u5c45\u4f4f\u4f53\u9a8c\u66f4\u597d");
        map.put(HouseReasonCode.CIVIL_WATER_ELECTRIC_MATCH, "\u6c11\u6c34\u6c11\u7535\uff0c\u751f\u6d3b\u6210\u672c\u66f4\u7a33\u5b9a");
        map.put(HouseReasonCode.SUPPORT_STUDENT_DEPOSIT_FREE_MATCH, "\u652f\u6301\u5b66\u751f\u514d\u62bc");
        map.put(HouseReasonCode.RELAXED_BUDGET_APPLIED, "\u5df2\u6309\u653e\u5bbd\u9884\u7b97\u8865\u5145\u623f\u6e90");
        map.put(HouseReasonCode.RELAXED_RADIUS_APPLIED, "\u5df2\u6269\u5927\u641c\u7d22\u8303\u56f4\u8865\u5145\u623f\u6e90");
        map.put(HouseReasonCode.FRESH_LISTING, "\u8fd1\u671f\u4e0a\u67b6\u623f\u6e90");
        return map;
    }
}

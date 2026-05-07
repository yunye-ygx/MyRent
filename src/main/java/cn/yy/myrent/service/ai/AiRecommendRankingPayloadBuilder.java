package cn.yy.myrent.service.ai;

import cn.yy.myrent.service.discovery.HouseRankResult;
import cn.yy.myrent.service.discovery.HouseRankedItem;
import cn.yy.myrent.service.discovery.HouseReasonCode;
import cn.yy.myrent.service.discovery.HouseScoreBreakdown;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiRecommendRankingPayloadBuilder {

    public AiRecommendRankingPayload build(AiRecommendSlots slots, HouseRankResult rankResult) {
        AiRecommendSlots safeSlots = slots == null ? new AiRecommendSlots() : slots;
        HouseRankResult safeRankResult = rankResult == null
                ? new HouseRankResult(List.of(), List.of(), 0)
                : rankResult;

        List<HouseRankedItem> pageItems = safeRankResult.currentPageItems();
        Map<String, Integer> sharedReasonCounts = new LinkedHashMap<>();
        List<AiRecommendRankingPayload.ListingPayload> listings = new ArrayList<>();

        for (HouseRankedItem item : pageItems) {
            if (item == null || item.house() == null) {
                continue;
            }
            List<String> reasonCodes = item.reasonCodes() == null
                    ? List.of()
                    : item.reasonCodes().stream().map(Enum::name).toList();
            for (String code : reasonCodes) {
                sharedReasonCounts.merge(code, 1, Integer::sum);
            }
            listings.add(AiRecommendRankingPayload.ListingPayload.builder()
                    .houseId(item.house().getId())
                    .title(item.house().getTitle())
                    .score(item.score())
                    .reasonCodes(new ArrayList<>(reasonCodes))
                    .reasonHighlights(reasonHighlights(prioritizeReasonCodes(item.reasonCodes())))
                    .scoreBreakdown(toScoreBreakdownPayload(item.scoreBreakdown()))
                    .build());
        }

        List<String> sharedReasonCodes = sharedReasonCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1 || listings.size() <= 1)
                .map(Map.Entry::getKey)
                .toList();

        List<String> sharedReasonHighlights = reasonHighlights(sharedReasonCodes.stream()
                .map(this::parseReasonCode)
                .filter(code -> code != null)
                .toList());

        return AiRecommendRankingPayload.builder()
                .slots(snapshot(safeSlots))
                .topListings(listings)
                .sharedReasonCodes(new ArrayList<>(sharedReasonCodes))
                .sharedReasonHighlights(new ArrayList<>(sharedReasonHighlights))
                .primaryReasonHighlights(reasonHighlights(List.of(HouseReasonCode.PRIMARY_PREFERENCE_MATCH)))
                .secondaryReasonHighlights(reasonHighlights(List.of(HouseReasonCode.SECONDARY_PREFERENCE_MATCH)))
                .relaxationHighlights(reasonHighlights(List.of(HouseReasonCode.BUDGET_RELAXED_ACCEPTED)))
                .summary(buildSummary(safeSlots, sharedReasonHighlights, listings, safeRankResult.total()))
                .build();
    }

    private AiRecommendRankingPayload.SlotSnapshot snapshot(AiRecommendSlots slots) {
        return AiRecommendRankingPayload.SlotSnapshot.builder()
                .city(slots.getCity())
                .locationName(slots.getLocationName())
                .budgetYuan(slots.getBudgetYuan())
                .budgetScope(slots.getBudgetScope())
                .rentMode(slots.getRentMode())
                .priority(slots.getPriority())
                .preferences(slots.getPreferences() == null ? new ArrayList<>() : new ArrayList<>(slots.getPreferences()))
                .build();
    }

    private String buildSummary(AiRecommendSlots slots,
                                List<String> sharedReasonHighlights,
                                List<AiRecommendRankingPayload.ListingPayload> listings,
                                long total) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(slots.getLocationName())) {
            parts.add("目标区域是" + slots.getLocationName());
        }
        if (slots.getBudgetYuan() != null) {
            parts.add("预算约" + slots.getBudgetYuan() + "元");
        }
        if (StringUtils.hasText(slots.getRentMode())) {
            parts.add("租住方式偏向" + toRentModeText(slots.getRentMode()));
        }
        if (slots.getPreferences() != null && !slots.getPreferences().isEmpty()) {
            parts.add("偏好包含" + String.join("、", slots.getPreferences()));
        }
        if (!sharedReasonHighlights.isEmpty()) {
            parts.add("靠前房源的共同优势是" + String.join("、", sharedReasonHighlights));
        } else if (!listings.isEmpty() && listings.get(0).getReasonHighlights() != null
                && !listings.get(0).getReasonHighlights().isEmpty()) {
            parts.add("排在最前的原因是" + String.join("、", listings.get(0).getReasonHighlights()));
        }
        if (listings.isEmpty()) {
            parts.add("当前候选房源较少");
        } else {
            parts.add("当前共筛出" + total + "套候选，已按综合得分排序");
        }
        return String.join("，", parts);
    }

    private List<String> reasonHighlights(List<HouseReasonCode> reasonCodes) {
        if (reasonCodes == null) {
            return List.of();
        }
        return reasonCodes.stream()
                .map(this::toReasonText)
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .limit(3)
                .toList();
    }

    private List<HouseReasonCode> prioritizeReasonCodes(List<HouseReasonCode> reasonCodes) {
        if (reasonCodes == null) {
            return List.of();
        }
        return reasonCodes.stream()
                .sorted((left, right) -> Integer.compare(reasonPriority(left), reasonPriority(right)))
                .toList();
    }

    private int reasonPriority(HouseReasonCode code) {
        if (code == null) {
            return Integer.MAX_VALUE;
        }
        return switch (code) {
            case PRIMARY_PREFERENCE_MATCH -> 0;
            case SECONDARY_PREFERENCE_MATCH -> 1;
            case BUDGET_RELAXED_ACCEPTED -> 2;
            case NEAR_SUBWAY_MATCH, PRIVATE_BATHROOM_MATCH, HAS_BALCONY_MATCH,
                    CIVIL_WATER_ELECTRIC_MATCH, SUPPORT_STUDENT_DEPOSIT_FREE_MATCH -> 3;
            case BUDGET_CLOSE_MATCH, RENT_MODE_MATCH -> 4;
            case LOCATION_DISTANCE_ADVANTAGE, RECALL_LOCATION_MATCH -> 5;
            case TEXT_RELEVANCE_ADVANTAGE, RECALL_TEXT_MATCH -> 6;
            case RELAXED_BUDGET_APPLIED, RELAXED_RADIUS_APPLIED -> 7;
            case FRESH_LISTING -> 8;
        };
    }

    private AiRecommendRankingPayload.ScoreBreakdownPayload toScoreBreakdownPayload(HouseScoreBreakdown scoreBreakdown) {
        if (scoreBreakdown == null) {
            return null;
        }
        return AiRecommendRankingPayload.ScoreBreakdownPayload.builder()
                .recallScore(scoreBreakdown.recallScore())
                .textRelevanceScore(scoreBreakdown.textRelevanceScore())
                .locationDistanceScore(scoreBreakdown.locationDistanceScore())
                .budgetCloseScore(scoreBreakdown.budgetCloseScore())
                .rentModeMatchScore(scoreBreakdown.rentModeMatchScore())
                .nearSubwayScore(scoreBreakdown.nearSubwayScore())
                .privateBathroomScore(scoreBreakdown.privateBathroomScore())
                .hasBalconyScore(scoreBreakdown.hasBalconyScore())
                .civilWaterElectricScore(scoreBreakdown.civilWaterElectricScore())
                .supportStudentDepositFreeScore(scoreBreakdown.supportStudentDepositFreeScore())
                .primaryPreferenceScore(scoreBreakdown.primaryPreferenceScore())
                .secondaryPreferenceScore(scoreBreakdown.secondaryPreferenceScore())
                .relaxationAcceptanceScore(scoreBreakdown.relaxationAcceptanceScore())
                .relaxationPenaltyOrAdjustment(scoreBreakdown.relaxationPenaltyOrAdjustment())
                .freshnessScore(scoreBreakdown.freshnessScore())
                .build();
    }

    private String toReasonText(HouseReasonCode code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case PRIMARY_PREFERENCE_MATCH -> "核心偏好优先满足";
            case SECONDARY_PREFERENCE_MATCH -> "次要偏好也有照顾";
            case BUDGET_RELAXED_ACCEPTED -> "预算小幅放宽后仍可接受";
            case BUDGET_CLOSE_MATCH -> "预算贴近";
            case RENT_MODE_MATCH -> "租住方式匹配";
            case NEAR_SUBWAY_MATCH -> "近地铁";
            case PRIVATE_BATHROOM_MATCH -> "独立卫浴";
            case HAS_BALCONY_MATCH -> "带阳台";
            case CIVIL_WATER_ELECTRIC_MATCH -> "民水民电";
            case SUPPORT_STUDENT_DEPOSIT_FREE_MATCH -> "支持学生免押";
            case LOCATION_DISTANCE_ADVANTAGE, RECALL_LOCATION_MATCH -> "位置匹配";
            case RELAXED_BUDGET_APPLIED -> "已放宽预算补充";
            case RELAXED_RADIUS_APPLIED -> "已扩大范围补充";
            case FRESH_LISTING -> "近期上架";
            case RECALL_TEXT_MATCH, TEXT_RELEVANCE_ADVANTAGE -> "文本相关";
        };
    }

    private HouseReasonCode parseReasonCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        try {
            return HouseReasonCode.valueOf(code);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String toRentModeText(String rentMode) {
        if (!StringUtils.hasText(rentMode)) {
            return "未指定";
        }
        return switch (rentMode.trim().toUpperCase()) {
            case "WHOLE" -> "整租";
            case "SHARED" -> "合租";
            default -> rentMode;
        };
    }
}

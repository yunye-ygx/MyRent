package cn.yy.myrent.service.ai.chat.tools;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchHousesTool {

    private static final String RENT_MODE_WHOLE = "WHOLE";
    private static final String RENT_MODE_SHARED = "SHARED";
    private static final Map<HouseReasonCode, String> REASON_TEXT_MAP = buildReasonTextMap();

    private final HouseRecallService houseRecallService;
    private final HouseRankingService houseRankingService;
    private final LocationResolveService locationResolveService;
    private final ObjectMapper objectMapper;

    @Tool(description = """
            根据用户需求搜索真实在租房源并返回排序后的推荐结果。
            仅当用户明确表达了找房、看房、推荐房源的意图时调用。
            locationName 必填；如果用户没有明确说预算或配套条件，不要猜测。
            返回结果里会包含推荐理由，便于后续用自然语言解释。
            """)
    public String searchHouses(
            @ToolParam(description = "区域名称，如'陆家嘴'、'三林'、'世纪公园'。必填。") String locationName,
            @ToolParam(description = "月租预算上限（元），如 3500。用户没说就不填。") Integer budgetYuan,
            @ToolParam(description = "WHOLE=整租，SHARED=合租。用户没说就不填。") String rentMode,
            @ToolParam(description = "是否要求靠近地铁站。用户没说就不填。") Boolean nearSubway,
            @ToolParam(description = "是否要求独立卫浴。用户没说就不填。") Boolean privateBathroom,
            @ToolParam(description = "是否要求有阳台。用户没说就不填。") Boolean hasBalcony,
            @ToolParam(description = "是否要求民水民电。用户没说就不填。") Boolean civilWaterElectric,
            @ToolParam(description = "返回房源数量，默认 5，最大 10。") Integer limit
    ) {
        ToolError validationError = validate(locationName, budgetYuan, rentMode);
        if (validationError != null) {
            return writeJson(validationError.toMap());
        }

        int pageSize = (limit != null && limit > 0 && limit <= 10) ? limit : 5;
        String normalizedRentMode = normalizeRentMode(rentMode);
        LocationResolveService.ResolvedLocation resolvedLocation;
        try {
            resolvedLocation = locationResolveService.resolveRequired(locationName);
        } catch (IllegalArgumentException ex) {
            return writeJson(error("INVALID_LOCATION",
                    "无法识别区域“" + locationName + "”，请提供具体地点，比如浦东、陆家嘴、三林"));
        }

        HouseRecallQuery recallQuery = HouseRecallQuery.builder()
                .locationName(resolvedLocation.name())
                .budgetYuan(budgetYuan)
                .rentMode(normalizedRentMode)
                .nearSubway(nearSubway)
                .privateBathroom(privateBathroom)
                .hasBalcony(hasBalcony)
                .civilWaterElectric(civilWaterElectric)
                .page(1)
                .size(pageSize)
                .recallProfile(HouseRecallProfile.AI_RECOMMEND)
                .build();

        HouseRankQuery rankQuery = HouseRankQuery.builder()
                .budgetYuan(budgetYuan)
                .budgetScope("RENT_ONLY")
                .rentMode(resolveRankingRentMode(normalizedRentMode))
                .nearSubway(nearSubway)
                .privateBathroom(privateBathroom)
                .hasBalcony(hasBalcony)
                .civilWaterElectric(civilWaterElectric)
                .page(1)
                .size(pageSize)
                .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                .build();

        try {
            HouseRecallResult recallResult = houseRecallService.recall(recallQuery);
            if (recallResult.candidates().isEmpty()) {
                Map<String, Object> result = successBase();
                result.put("count", 0);
                result.put("location", resolvedLocation.name());
                result.put("houses", List.of());
                result.put("message", "当前条件下暂无匹配房源，可以调整预算、区域或配套条件后再试");
                return writeJson(result);
            }

            HouseRankResult rankResult = houseRankingService.rank(recallResult.candidates(), rankQuery);
            Map<Long, HouseRecallEvidence> evidenceByHouseId = buildEvidenceByHouseId(recallResult.candidates());
            List<Map<String, Object>> houses = new ArrayList<>();

            for (HouseRankedItem item : rankResult.currentPageItems()) {
                House house = item.house();
                if (house == null) {
                    continue;
                }

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("houseId", house.getId());
                payload.put("title", house.getTitle());
                payload.put("priceYuan", toYuan(house.getPrice()));
                payload.put("rentMode", Integer.valueOf(1).equals(house.getRentType()) ? "整租" : "合租");
                payload.put("score", BigDecimal.valueOf(item.score()).setScale(3, RoundingMode.HALF_UP));
                payload.put("highlights", buildHighlights(house));
                payload.put("reasonCodes", item.reasonCodes().stream().map(Enum::name).toList());
                payload.put("reasons", mapReasons(item.reasonCodes(), evidenceByHouseId.get(house.getId())));
                houses.add(payload);
            }

            Map<String, Object> result = successBase();
            result.put("count", houses.size());
            result.put("location", resolvedLocation.name());
            result.put("houses", houses);
            return writeJson(result);
        } catch (Exception e) {
            log.error("searchHouses failed for location={}", resolvedLocation.name(), e);
            return writeJson(error("SYSTEM_ERROR", "搜索服务暂时不可用，请稍后再试"));
        }
    }

    private ToolError validate(String locationName, Integer budgetYuan, String rentMode) {
        if (!StringUtils.hasText(locationName)) {
            return new ToolError("MISSING_LOCATION", "请先告诉我你想在哪个区域租房");
        }
        if (budgetYuan != null && budgetYuan < 0) {
            return new ToolError("INVALID_BUDGET", "预算金额不能为负数");
        }
        String normalizedRentMode = normalizeRentMode(rentMode);
        if (StringUtils.hasText(rentMode) && normalizedRentMode == null) {
            return new ToolError("INVALID_RENT_MODE", "rentMode 只能是 WHOLE 或 SHARED");
        }
        return null;
    }

    private String normalizeRentMode(String rentMode) {
        if (!StringUtils.hasText(rentMode)) {
            return null;
        }
        String normalized = rentMode.trim().toUpperCase(Locale.ROOT);
        if (RENT_MODE_WHOLE.equals(normalized) || RENT_MODE_SHARED.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String resolveRankingRentMode(String rentMode) {
        if (RENT_MODE_WHOLE.equals(rentMode)) {
            return "1";
        }
        if (RENT_MODE_SHARED.equals(rentMode)) {
            return "0";
        }
        return null;
    }

    private Map<Long, HouseRecallEvidence> buildEvidenceByHouseId(List<HouseRecallCandidate> candidates) {
        Map<Long, HouseRecallEvidence> evidenceByHouseId = new HashMap<>();
        if (candidates == null) {
            return evidenceByHouseId;
        }
        for (HouseRecallCandidate candidate : candidates) {
            if (candidate == null || candidate.house() == null || candidate.house().getId() == null) {
                continue;
            }
            evidenceByHouseId.put(candidate.house().getId(), candidate.recallEvidence());
        }
        return evidenceByHouseId;
    }

    private List<String> buildHighlights(House house) {
        List<String> highlights = new ArrayList<>();
        if (Integer.valueOf(1).equals(house.getNearSubway())) {
            highlights.add("近地铁");
        }
        if (Integer.valueOf(1).equals(house.getPrivateBathroom())) {
            highlights.add("独立卫浴");
        }
        if (Integer.valueOf(1).equals(house.getHasBalcony())) {
            highlights.add("带阳台");
        }
        if (Integer.valueOf(1).equals(house.getCivilWaterElectric())) {
            highlights.add("民水民电");
        }
        return highlights;
    }

    private List<String> mapReasons(List<HouseReasonCode> reasonCodes, HouseRecallEvidence evidence) {
        List<String> reasons = new ArrayList<>(3);
        if (reasonCodes != null) {
            for (HouseReasonCode reasonCode : reasonCodes) {
                String mapped = mapReason(reasonCode, evidence);
                if (mapped != null && !reasons.contains(mapped)) {
                    reasons.add(mapped);
                }
                if (reasons.size() >= 3) {
                    break;
                }
            }
        }
        if (reasons.isEmpty() && evidence != null && evidence.locationDistanceMeters() != null) {
            reasons.add("距离目标地点约 " + toKm(evidence.locationDistanceMeters()).stripTrailingZeros().toPlainString() + "km");
        }
        return reasons;
    }

    private String mapReason(HouseReasonCode reasonCode, HouseRecallEvidence evidence) {
        if (reasonCode == null) {
            return null;
        }
        if (reasonCode == HouseReasonCode.LOCATION_DISTANCE_ADVANTAGE
                && evidence != null
                && evidence.locationDistanceMeters() != null) {
            return "距离目标地点约 " + toKm(evidence.locationDistanceMeters()).stripTrailingZeros().toPlainString() + "km";
        }
        return REASON_TEXT_MAP.get(reasonCode);
    }

    private Map<String, Object> successBase() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        return result;
    }

    private Map<String, Object> error(String errorCode, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("errorCode", errorCode);
        result.put("message", message);
        return result;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize tool response", e);
        }
    }

    private BigDecimal toYuan(Integer cent) {
        if (cent == null) {
            return null;
        }
        return BigDecimal.valueOf(cent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    private BigDecimal toKm(Double meters) {
        if (meters == null) {
            return null;
        }
        return BigDecimal.valueOf(meters).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
    }

    private static Map<HouseReasonCode, String> buildReasonTextMap() {
        Map<HouseReasonCode, String> map = new EnumMap<>(HouseReasonCode.class);
        map.put(HouseReasonCode.RECALL_LOCATION_MATCH, "区域或通勤地点匹配");
        map.put(HouseReasonCode.RECALL_TEXT_MATCH, "标题关键词匹配");
        map.put(HouseReasonCode.TEXT_RELEVANCE_ADVANTAGE, "文本相关度较高");
        map.put(HouseReasonCode.BUDGET_CLOSE_MATCH, "月租贴近预算");
        map.put(HouseReasonCode.RENT_MODE_MATCH, "租住方式匹配");
        map.put(HouseReasonCode.NEAR_SUBWAY_MATCH, "近地铁，通勤更方便");
        map.put(HouseReasonCode.PRIVATE_BATHROOM_MATCH, "独立卫浴更方便");
        map.put(HouseReasonCode.HAS_BALCONY_MATCH, "带阳台，居住体验更好");
        map.put(HouseReasonCode.CIVIL_WATER_ELECTRIC_MATCH, "民水民电，生活成本更稳定");
        map.put(HouseReasonCode.SUPPORT_STUDENT_DEPOSIT_FREE_MATCH, "支持学生免押");
        map.put(HouseReasonCode.PRIMARY_PREFERENCE_MATCH, "命中了你的核心偏好");
        map.put(HouseReasonCode.SECONDARY_PREFERENCE_MATCH, "命中了你的次级偏好");
        map.put(HouseReasonCode.BUDGET_RELAXED_ACCEPTED, "符合你可接受的预算放宽范围");
        map.put(HouseReasonCode.RELAXED_BUDGET_APPLIED, "这是放宽预算后的补充结果");
        map.put(HouseReasonCode.RELAXED_RADIUS_APPLIED, "这是扩大搜索范围后的补充结果");
        map.put(HouseReasonCode.FRESH_LISTING, "近期新上架房源");
        return map;
    }

    private record ToolError(String errorCode, String message) {
        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("errorCode", errorCode);
            result.put("message", message);
            return result;
        }
    }
}

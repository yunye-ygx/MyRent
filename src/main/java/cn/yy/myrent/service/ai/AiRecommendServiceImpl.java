package cn.yy.myrent.service.ai;

import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.dto.AiRecommendInteractionDTO;
import cn.yy.myrent.dto.AiRecommendInteractionSlotPatchDTO;
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
import cn.yy.myrent.vo.AiPreviewVO;
import cn.yy.myrent.vo.AiRecommendChatVO;
import cn.yy.myrent.vo.AiRecommendSlotsVO;
import cn.yy.myrent.vo.SmartGuideItemVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@ConditionalOnBean(AiRecommendDecisionClient.class)
@ConditionalOnProperty(value = "myrent.ai.recommend.enabled", havingValue = "true")
public class AiRecommendServiceImpl implements AiRecommendService {

    private static final int MIN_BUDGET_YUAN = 300;
    private static final int MAX_BUDGET_YUAN = 50000;
    private static final int SEARCH_PAGE_SIZE = 10;
    private static final String PREVIEW_SELECTION_TYPE = "PREVIEW_SELECTION";
    private static final String DEFAULT_BUDGET_SCOPE = "RENT_ONLY";

    private final AiRecommendDecisionClient decisionClient;
    private final AiRecommendStateStore stateStore;
    private final AiRecommendSummaryBuilder summaryBuilder;
    private final AiPreviewService previewService;
    private final HouseRecallService houseRecallService;
    private final HouseRankingService houseRankingService;
    private final AiRecommendRankingPayloadBuilder rankingPayloadBuilder;
    private final int historyLimit;
    private final int promptHistoryLimit;
    private final String defaultBudgetScope;

    public AiRecommendServiceImpl(AiRecommendDecisionClient decisionClient,
                                  AiRecommendStateStore stateStore,
                                  AiRecommendSummaryBuilder summaryBuilder,
                                  AiPreviewService previewService,
                                  HouseRecallService houseRecallService,
                                  HouseRankingService houseRankingService,
                                  AiRecommendRankingPayloadBuilder rankingPayloadBuilder,
                                  @Value("${myrent.ai.recommend.history-limit:10}") int historyLimit,
                                  @Value("${myrent.ai.recommend.prompt-history-limit:6}") int promptHistoryLimit,
                                  @Value("${myrent.ai.recommend.default-budget-scope:RENT_ONLY}") String defaultBudgetScope) {
        this.decisionClient = decisionClient;
        this.stateStore = stateStore;
        this.summaryBuilder = summaryBuilder;
        this.previewService = previewService;
        this.houseRecallService = houseRecallService;
        this.houseRankingService = houseRankingService;
        this.rankingPayloadBuilder = rankingPayloadBuilder;
        this.historyLimit = historyLimit;
        this.promptHistoryLimit = promptHistoryLimit;
        this.defaultBudgetScope = normalizeDefaultBudgetScope(defaultBudgetScope);
    }

    @Override
    public AiRecommendChatVO getOrCreateSession(Long userId) {
        AiRecommendSessionState state = normalizeState(stateStore.loadOrCreate(userId), userId);
        if (state.getHistory().isEmpty()) {
            String opening = "你好，可以先告诉我想看的区域，或者直接说你的预算和整租/合租偏好。";
            appendAssistant(state, opening);
            state.setStage(AiRecommendStage.ASK.name());
            state.setSummary(summaryBuilder.build(state.getSlots(), buildMissingSlots(state.getSlots())));
            stateStore.save(state);
            return toChatVO(state, AiRecommendStage.ASK, opening, buildMissingSlots(state.getSlots()), null, null);
        }
        return toChatVO(state, parseStage(state.getStage()), latestAssistantReply(state), buildMissingSlots(state.getSlots()), null, null);
    }

    @Override
    public AiRecommendChatVO chat(Long userId, AiRecommendChatReqDTO reqDTO) {
        AiRecommendSessionState state = normalizeState(stateStore.loadOrCreate(userId), userId);
        TurnInput input = normalizeInput(reqDTO);
        appendUser(state, input.transcriptMessage());

        AiRecommendSlots baseSlots = mergeSlots(state.getSlots(), input.slotPatch());
        state.setSlots(baseSlots);
        if (input.isPreviewSelection()) {
            state.setSelectedPreviewGroupKey(input.previewGroupKey());
        }

        AiRecommendDecision decision;
        try {
            decision = decisionClient.decide(buildPromptState(state), input.promptMessage());
        } catch (Exception ex) {
            decision = fallbackDecision(baseSlots);
        }

        AiRecommendSlots mergedSlots = mergeSlots(baseSlots, decision.getSlots());
        String inferredRentMode = normalizeRentMode(input.promptMessage());
        if (StringUtils.hasText(inferredRentMode)) {
            mergedSlots.setRentMode(inferredRentMode);
        }
        if (mergedSlots.getBudgetRelaxable() == null) {
            mergedSlots.setBudgetRelaxable(inferBudgetRelaxable(input.promptMessage()));
        }
        mergedSlots = normalizeSlots(mergedSlots);
        state.setSlots(mergedSlots);

        List<String> missingSlots = buildMissingSlots(mergedSlots);
        AiRecommendStage stage = deriveStage(state.getStage(), input, mergedSlots, missingSlots);
        AiPreviewVO preview = null;
        SmartGuideResultVO recommendation = null;
        String assistantReply = decision.getReply();

        if (stage == AiRecommendStage.PREVIEW) {
            preview = buildPreviewSafely(mergedSlots);
            if (preview == null || preview.getGroups() == null || preview.getGroups().isEmpty()) {
                stage = AiRecommendStage.ASK;
                assistantReply = buildPreviewUnavailableReply(missingSlots);
            }
        }

        if (stage == AiRecommendStage.SEARCH) {
            try {
                HouseRecallResult recallResult = houseRecallService.recall(buildRecallQuery(mergedSlots));
                HouseRankResult rankResult = houseRankingService.rank(recallResult.candidates(), buildRankQuery(mergedSlots));
                recommendation = buildRecommendation(mergedSlots, rankResult, buildEvidenceByHouseId(recallResult));
                try {
                    AiRecommendRankingPayload payload = rankingPayloadBuilder.build(mergedSlots, rankResult);
                    assistantReply = buildGroundedSearchReply(payload, recommendation, assistantReply);
                } catch (Exception payloadEx) {
                    assistantReply = buildRecommendationReply(recommendation, assistantReply);
                }
            } catch (Exception ex) {
                preview = buildPreviewSafely(mergedSlots);
                stage = preview != null && preview.getGroups() != null && !preview.getGroups().isEmpty()
                        ? AiRecommendStage.REFINE
                        : AiRecommendStage.ASK;
                recommendation = null;
                assistantReply = "我先记下你的条件了，不过刚才正式查房时出了点问题。你可以继续收窄方向，我再帮你试一次。";
            }
        }

        assistantReply = finalizeReply(stage, assistantReply, preview, recommendation, missingSlots, mergedSlots);
        state.setStage(stage.name());
        state.setSummary(summaryBuilder.build(mergedSlots, buildMissingSlots(mergedSlots)));
        appendAssistant(state, assistantReply);
        stateStore.save(state);
        return toChatVO(state, stage, assistantReply, buildMissingSlots(mergedSlots), preview, recommendation);
    }

    @Override
    public AiRecommendChatVO reset(Long userId) {
        stateStore.reset(userId);
        AiRecommendSessionState state = AiRecommendSessionState.empty(userId);
        state.setSlots(normalizeSlots(state.getSlots()));
        state.setStage(AiRecommendStage.ASK.name());
        state.setSelectedPreviewGroupKey(null);
        String opening = "已经帮你重新开始了。你可以直接告诉我想看的区域、预算，或者整租/合租偏好。";
        appendAssistant(state, opening);
        state.setSummary(summaryBuilder.build(state.getSlots(), buildMissingSlots(state.getSlots())));
        stateStore.save(state);
        return toChatVO(state, AiRecommendStage.ASK, opening, buildMissingSlots(state.getSlots()), null, null);
    }

    private HouseRecallQuery buildRecallQuery(AiRecommendSlots slots) {
        return HouseRecallQuery.builder()
                .locationName(slots.getLocationName())
                .city(slots.getCity())
                .budgetYuan(slots.getBudgetYuan())
                .budgetScope(slots.getBudgetScope())
                .rentMode(slots.getRentMode())
                .nearSubway(hasRecallPreference(slots, "nearSubway"))
                .privateBathroom(hasRecallPreference(slots, "privateBathroom"))
                .hasBalcony(hasRecallPreference(slots, "hasBalcony"))
                .civilWaterElectric(hasRecallPreference(slots, "civilWaterElectric"))
                .supportStudentDepositFree(hasRecallPreference(slots, "supportStudentDepositFree"))
                .page(1)
                .size(SEARCH_PAGE_SIZE)
                .recallProfile(HouseRecallProfile.AI_RECOMMEND)
                .build();
    }

    private HouseRankQuery buildRankQuery(AiRecommendSlots slots) {
        return HouseRankQuery.builder()
                .budgetYuan(slots.getBudgetYuan())
                .budgetScope(slots.getBudgetScope())
                .rentMode(resolveRankingRentMode(slots.getRentMode()))
                .page(1)
                .size(SEARCH_PAGE_SIZE)
                .nearSubway(hasRankPreference(slots, "nearSubway"))
                .privateBathroom(hasRankPreference(slots, "privateBathroom"))
                .hasBalcony(hasRankPreference(slots, "hasBalcony"))
                .civilWaterElectric(hasRankPreference(slots, "civilWaterElectric"))
                .supportStudentDepositFree(hasRankPreference(slots, "supportStudentDepositFree"))
                .preferenceWeightMap(buildPreferenceWeightMap(slots))
                .budgetRelaxable(slots.getBudgetRelaxable())
                .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                .build();
    }

    private SmartGuideResultVO buildRecommendation(AiRecommendSlots slots,
                                                   HouseRankResult rankResult,
                                                   Map<Long, HouseRecallEvidence> evidenceByHouseId) {
        SmartGuideResultVO result = new SmartGuideResultVO();
        result.setOriginalBudgetYuan(slots.getBudgetYuan());
        result.setRelaxedBudget(hasRelaxedBudgetMatch(rankResult, evidenceByHouseId));
        result.setMatchedExpectation(rankResult != null && !rankResult.currentPageItems().isEmpty());
        result.setTipMessage(rankResult == null || rankResult.currentPageItems().isEmpty()
                ? "当前符合条件的房源较少。"
                : "已根据你的条件完成排序，下面是优先推荐结果。");
        result.setRecommendations(mapRecommendationItems(
                rankResult == null ? List.of() : rankResult.currentPageItems(),
                slots,
                evidenceByHouseId
        ));
        return result;
    }

    private List<SmartGuideItemVO> mapRecommendationItems(List<HouseRankedItem> rankedItems,
                                                          AiRecommendSlots slots,
                                                          Map<Long, HouseRecallEvidence> evidenceByHouseId) {
        List<SmartGuideItemVO> items = new ArrayList<>();
        for (HouseRankedItem rankedItem : rankedItems) {
            if (rankedItem == null || rankedItem.house() == null) {
                continue;
            }
            HouseRecallEvidence evidence = evidenceByHouseId == null ? null : evidenceByHouseId.get(rankedItem.house().getId());
            SmartGuideItemVO item = new SmartGuideItemVO();
            item.setHouseId(rankedItem.house().getId());
            item.setPublisherUserId(rankedItem.house().getPublisherUserId());
            item.setTitle(rankedItem.house().getTitle());
            item.setStatus(rankedItem.house().getStatus());
            item.setPrice(toYuan(rankedItem.house().getPrice()));
            item.setDepositAmount(toYuan(rankedItem.house().getDepositAmount()));
            item.setTotalCost(toYuan(rankedItem.house().getTotalCost() != null
                    ? rankedItem.house().getTotalCost()
                    : rankedItem.house().getPrice()));
            item.setDistanceToMetroKm(convertDistanceMetersToKm(evidence == null ? null : evidence.locationDistanceMeters()));
            item.setEstimatedCommuteMinutes(estimateCommuteMinutes(evidence == null ? null : evidence.locationDistanceMeters()));
            item.setScore(BigDecimal.valueOf(rankedItem.score()).setScale(3, RoundingMode.HALF_UP));
            RecommendationReasonBundle reasonBundle = buildRecommendationReasonBundle(rankedItem, slots);
            item.setRecommendationSummary(reasonBundle.summary());
            item.setPrimaryReasons(reasonBundle.primaryReasons());
            item.setSecondaryReasons(reasonBundle.secondaryReasons());
            item.setRelaxationNotes(reasonBundle.relaxationNotes());
            item.setReasons(reasonBundle.reasons());
            items.add(item);
        }
        return items;
    }

    private List<String> mapReasons(List<HouseReasonCode> reasonCodes, AiRecommendSlots slots) {
        List<String> reasons = new ArrayList<>();
        if (reasonCodes == null) {
            return reasons;
        }
        for (HouseReasonCode code : reasonCodes) {
            String text = switch (code) {
                case PRIMARY_PREFERENCE_MATCH -> "优先满足核心偏好";
                case SECONDARY_PREFERENCE_MATCH -> "兼顾次要偏好";
                case BUDGET_RELAXED_ACCEPTED -> "预算已小幅放宽仍可接受";
                case BUDGET_CLOSE_MATCH -> "预算贴近";
                case RENT_MODE_MATCH -> "租住方式匹配";
                case NEAR_SUBWAY_MATCH -> "近地铁更方便";
                case PRIVATE_BATHROOM_MATCH -> "独立卫浴更方便";
                case HAS_BALCONY_MATCH -> "带阳台";
                case CIVIL_WATER_ELECTRIC_MATCH -> "民水民电";
                case SUPPORT_STUDENT_DEPOSIT_FREE_MATCH -> "支持学生免押";
                case LOCATION_DISTANCE_ADVANTAGE, RECALL_LOCATION_MATCH -> "位置匹配 " + safeValue(slots.getLocationName());
                case RELAXED_BUDGET_APPLIED -> "已按放宽预算补充";
                case RELAXED_RADIUS_APPLIED -> "已按扩大范围补充";
                case RECALL_TEXT_MATCH, TEXT_RELEVANCE_ADVANTAGE -> "文本相关";
                case FRESH_LISTING -> "近期上架";
            };
            if (!reasons.contains(text)) {
                reasons.add(text);
            }
            if (reasons.size() >= 3) {
                break;
            }
        }
        return reasons;
    }

    private RecommendationReasonBundle buildRecommendationReasonBundle(HouseRankedItem rankedItem, AiRecommendSlots slots) {
        List<HouseReasonCode> reasonCodes = rankedItem == null || rankedItem.reasonCodes() == null
                ? List.of()
                : rankedItem.reasonCodes();
        House house = rankedItem == null ? null : rankedItem.house();

        LinkedHashSet<String> primaryReasons = new LinkedHashSet<>();
        LinkedHashSet<String> secondaryReasons = new LinkedHashSet<>();
        LinkedHashSet<String> relaxationNotes = new LinkedHashSet<>();

        if (slots != null && slots.getWeightedPreferences() != null) {
            for (AiWeightedPreference weightedPreference : slots.getWeightedPreferences()) {
                if (weightedPreference == null
                        || !StringUtils.hasText(weightedPreference.getPreferenceKey())
                        || !matchesPreference(house, weightedPreference.getPreferenceKey())) {
                    continue;
                }
                switch (weightedPreference.getWeightLevel()) {
                    case HIGH -> primaryReasons.add(buildPrimaryPreferenceReason(weightedPreference, slots));
                    case MEDIUM -> secondaryReasons.add("也兼顾了" + preferenceLabel(weightedPreference.getPreferenceKey()) + "偏好");
                    case LOW -> secondaryReasons.add(preferenceLabel(weightedPreference.getPreferenceKey()) + "也作为加分项被照顾到了");
                }
            }
        }

        for (HouseReasonCode code : reasonCodes) {
            switch (code) {
                case BUDGET_CLOSE_MATCH -> secondaryReasons.add("租金和你的预算更接近");
                case RENT_MODE_MATCH -> secondaryReasons.add("整租/合租方式匹配");
                case LOCATION_DISTANCE_ADVANTAGE, RECALL_LOCATION_MATCH -> secondaryReasons.add(locationReason(slots));
                case NEAR_SUBWAY_MATCH -> addFallbackPreferenceReason(secondaryReasons, slots, "nearSubway");
                case PRIVATE_BATHROOM_MATCH -> addFallbackPreferenceReason(secondaryReasons, slots, "privateBathroom");
                case HAS_BALCONY_MATCH -> addFallbackPreferenceReason(secondaryReasons, slots, "hasBalcony");
                case CIVIL_WATER_ELECTRIC_MATCH -> addFallbackPreferenceReason(secondaryReasons, slots, "civilWaterElectric");
                case SUPPORT_STUDENT_DEPOSIT_FREE_MATCH -> addFallbackPreferenceReason(secondaryReasons, slots, "supportStudentDepositFree");
                case RELAXED_BUDGET_APPLIED, BUDGET_RELAXED_ACCEPTED -> relaxationNotes.add("为了保留更强匹配，这套房接受了轻度预算放宽");
                case RELAXED_RADIUS_APPLIED -> relaxationNotes.add("为了保留更强匹配，搜索范围做了适度放宽");
                case TEXT_RELEVANCE_ADVANTAGE, RECALL_TEXT_MATCH -> secondaryReasons.add("文本条件匹配度更高");
                case FRESH_LISTING -> secondaryReasons.add("房源上架时间较近");
                case PRIMARY_PREFERENCE_MATCH, SECONDARY_PREFERENCE_MATCH -> {
                }
            }
        }

        List<String> primary = limitReasons(primaryReasons, 2);
        List<String> secondary = limitReasons(secondaryReasons, 3);
        List<String> relaxation = limitReasons(relaxationNotes, 2);
        List<String> outwardReasons = new ArrayList<>();
        outwardReasons.addAll(primary);
        outwardReasons.addAll(secondary);
        outwardReasons.addAll(relaxation);
        String summary = buildRecommendationSummary(primary, secondary, relaxation);
        return new RecommendationReasonBundle(
                summary,
                primary,
                secondary,
                relaxation,
                limitReasons(outwardReasons, 4)
        );
    }

    private String buildRecommendationSummary(List<String> primaryReasons,
                                              List<String> secondaryReasons,
                                              List<String> relaxationNotes) {
        if (primaryReasons != null && !primaryReasons.isEmpty()) {
            return primaryReasons.get(0);
        }
        if (secondaryReasons != null && !secondaryReasons.isEmpty()) {
            return secondaryReasons.get(0);
        }
        if (relaxationNotes != null && !relaxationNotes.isEmpty()) {
            return relaxationNotes.get(0);
        }
        return null;
    }

    private List<String> limitReasons(LinkedHashSet<String> reasons, int maxCount) {
        return limitReasons(new ArrayList<>(reasons), maxCount);
    }

    private List<String> limitReasons(List<String> reasons, int maxCount) {
        if (reasons == null || reasons.isEmpty()) {
            return List.of();
        }
        return reasons.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(maxCount)
                .toList();
    }

    private void addFallbackPreferenceReason(LinkedHashSet<String> secondaryReasons,
                                             AiRecommendSlots slots,
                                             String preferenceKey) {
        if (secondaryReasons == null || isWeightedPreferenceAlreadyExplained(slots, preferenceKey)) {
            return;
        }
        secondaryReasons.add(preferenceLabel(preferenceKey) + "条件也匹配");
    }

    private boolean isWeightedPreferenceAlreadyExplained(AiRecommendSlots slots, String preferenceKey) {
        if (slots == null || slots.getWeightedPreferences() == null || !StringUtils.hasText(preferenceKey)) {
            return false;
        }
        return slots.getWeightedPreferences().stream()
                .filter(item -> item != null && StringUtils.hasText(item.getPreferenceKey()))
                .anyMatch(item -> preferenceKey.equalsIgnoreCase(item.getPreferenceKey().trim()));
    }

    private String buildPrimaryPreferenceReason(AiWeightedPreference weightedPreference, AiRecommendSlots slots) {
        String label = preferenceLabel(weightedPreference.getPreferenceKey());
        if ("COMMUTE".equals(normalizeToken(slots == null ? null : slots.getPriority()))
                && "nearSubway".equalsIgnoreCase(weightedPreference.getPreferenceKey())) {
            return "优先满足通勤诉求，近地铁匹配更强";
        }
        return "优先满足" + label + "这个核心条件";
    }

    private String locationReason(AiRecommendSlots slots) {
        if (slots != null && StringUtils.hasText(slots.getLocationName())) {
            return "位置更贴近" + slots.getLocationName();
        }
        return "位置匹配度更高";
    }

    private String preferenceLabel(String preferenceKey) {
        if (!StringUtils.hasText(preferenceKey)) {
            return "偏好";
        }
        return switch (preferenceKey.trim()) {
            case "nearSubway" -> "近地铁";
            case "privateBathroom" -> "独立卫浴";
            case "hasBalcony" -> "阳台";
            case "civilWaterElectric" -> "民水民电";
            case "supportStudentDepositFree" -> "学生免押";
            default -> preferenceKey;
        };
    }

    private boolean matchesPreference(House house, String preferenceKey) {
        if (house == null || !StringUtils.hasText(preferenceKey)) {
            return false;
        }
        return switch (preferenceKey.trim()) {
            case "nearSubway" -> isEnabled(house.getNearSubway());
            case "privateBathroom" -> isEnabled(house.getPrivateBathroom());
            case "hasBalcony" -> isEnabled(house.getHasBalcony());
            case "civilWaterElectric" -> isEnabled(house.getCivilWaterElectric());
            case "supportStudentDepositFree" -> isEnabled(house.getSupportStudentDepositFree());
            default -> false;
        };
    }

    private String buildGroundedSearchReply(AiRecommendRankingPayload payload,
                                            SmartGuideResultVO recommendation,
                                            String fallbackReply) {
        if (payload == null) {
            return buildRecommendationReply(recommendation, fallbackReply);
        }
        List<String> titles = payload.getTopListings() == null
                ? List.of()
                : payload.getTopListings().stream()
                .map(AiRecommendRankingPayload.ListingPayload::getTitle)
                .filter(StringUtils::hasText)
                .limit(3)
                .toList();
        List<String> sharedHighlights = payload.getSharedReasonHighlights() == null
                ? List.of()
                : payload.getSharedReasonHighlights().stream()
                .filter(StringUtils::hasText)
                .limit(3)
                .toList();
        List<String> topListingHighlights = payload.getTopListings() == null || payload.getTopListings().isEmpty()
                ? List.of()
                : payload.getTopListings().get(0).getReasonHighlights() == null
                ? List.of()
                : payload.getTopListings().get(0).getReasonHighlights().stream()
                .filter(StringUtils::hasText)
                .limit(3)
                .toList();
        String prefix = StringUtils.hasText(payload.getSummary()) ? payload.getSummary() : "已完成排序。";
        if (!titles.isEmpty() && !sharedHighlights.isEmpty()) {
            return prefix + "，优先可以先看：" + String.join("、", titles) + "。这些房源主要因为" + String.join("、", sharedHighlights) + "排在前面。";
        }
        if (!titles.isEmpty() && !topListingHighlights.isEmpty()) {
            return prefix + "，优先可以先看：" + String.join("、", titles) + "。排在最前的原因是" + String.join("、", topListingHighlights) + "。";
        }
        if (!titles.isEmpty()) {
            return prefix + "，优先可以先看：" + String.join("、", titles) + "。";
        }
        return buildRecommendationReply(recommendation, fallbackReply);
    }

    private String buildRecommendationReply(SmartGuideResultVO recommendation, String fallbackReply) {
        if (recommendation != null
                && recommendation.getRecommendations() != null
                && !recommendation.getRecommendations().isEmpty()
                && StringUtils.hasText(recommendation.getRecommendations().get(0).getTitle())) {
            return "我已经按你的条件完成排序，优先推荐 " + recommendation.getRecommendations().get(0).getTitle() + "。";
        }
        if (StringUtils.hasText(fallbackReply)) {
            return fallbackReply.trim();
        }
        return "我已经按你的条件完成搜索，并整理好了推荐结果。";
    }

    private AiRecommendDecision fallbackDecision(AiRecommendSlots slots) {
        return AiRecommendDecision.builder()
                .reply("我先继续帮你整理条件。你可以补充区域、预算，或者告诉我是整租还是合租。")
                .slots(slots)
                .build();
    }

    private AiRecommendSessionState normalizeState(AiRecommendSessionState state, Long userId) {
        AiRecommendSessionState resolved = state == null ? AiRecommendSessionState.empty(userId) : state;
        if (resolved.getUserId() == null) {
            resolved.setUserId(userId);
        }
        if (!StringUtils.hasText(resolved.getSessionId())) {
            resolved.setSessionId(AiRecommendSessionState.buildSessionId(userId));
        }
        if (!StringUtils.hasText(resolved.getStage())) {
            resolved.setStage(AiRecommendStage.ASK.name());
        }
        if (!StringUtils.hasText(resolved.getSelectedPreviewGroupKey())) {
            resolved.setSelectedPreviewGroupKey(null);
        }
        resolved.setSlots(normalizeSlots(resolved.getSlots()));
        if (resolved.getHistory() == null) {
            resolved.setHistory(new ArrayList<>());
        }
        if (resolved.getSummary() == null) {
            resolved.setSummary("");
        }
        resolved.setHistory(trimHistory(resolved.getHistory()));
        return resolved;
    }

    private TurnInput normalizeInput(AiRecommendChatReqDTO reqDTO) {
        String promptMessage = resolvePromptMessage(reqDTO);
        AiRecommendInteractionDTO interaction = reqDTO == null ? null : reqDTO.getInteraction();
        boolean previewSelection = interaction != null
                && PREVIEW_SELECTION_TYPE.equals(normalizeToken(interaction.getType()));
        return new TurnInput(
                promptMessage,
                promptMessage,
                toSlotsPatch(interaction),
                previewSelection,
                previewSelection ? normalizeText(interaction.getGroupKey()) : null
        );
    }

    private AiRecommendSlots normalizeSlots(AiRecommendSlots slots) {
        AiRecommendSlots source = slots == null ? new AiRecommendSlots() : slots;
        List<AiWeightedPreference> weightedPreferences = normalizeWeightedPreferences(source);
        List<String> preferences = normalizePreferenceKeys(source.getPreferences(), weightedPreferences);
        return AiRecommendSlots.builder()
                .city(normalizeText(source.getCity()))
                .locationName(normalizeText(source.getLocationName()))
                .budgetYuan(source.getBudgetYuan())
                .budgetScope(normalizeBudgetScope(source.getBudgetScope()))
                .rentMode(normalizeRentMode(source.getRentMode()))
                .priority(normalizeToken(source.getPriority()))
                .preferences(new ArrayList<>(preferences))
                .weightedPreferences(new ArrayList<>(weightedPreferences))
                .budgetRelaxable(source.getBudgetRelaxable())
                .build();
    }

    private AiRecommendSlots mergeSlots(AiRecommendSlots current, AiRecommendSlots incoming) {
        AiRecommendSlots base = normalizeSlots(current);
        AiRecommendSlots update = normalizeSlots(incoming);
        return AiRecommendSlots.builder()
                .city(firstNonBlank(update.getCity(), base.getCity()))
                .locationName(firstNonBlank(update.getLocationName(), base.getLocationName()))
                .budgetYuan(update.getBudgetYuan() != null ? update.getBudgetYuan() : base.getBudgetYuan())
                .budgetScope(firstNonBlank(update.getBudgetScope(), base.getBudgetScope()))
                .rentMode(firstNonBlank(update.getRentMode(), base.getRentMode()))
                .priority(firstNonBlank(update.getPriority(), base.getPriority()))
                .preferences(!update.getPreferences().isEmpty() ? update.getPreferences() : base.getPreferences())
                .weightedPreferences(!update.getWeightedPreferences().isEmpty()
                        ? update.getWeightedPreferences()
                        : base.getWeightedPreferences())
                .budgetRelaxable(update.getBudgetRelaxable() != null ? update.getBudgetRelaxable() : base.getBudgetRelaxable())
                .build();
    }

    private List<String> buildMissingSlots(AiRecommendSlots slots) {
        List<String> missing = new ArrayList<>();
        if (!isBudgetUsable(slots.getBudgetYuan())) {
            missing.add("budgetYuan");
        }
        if (!StringUtils.hasText(slots.getRentMode())) {
            missing.add("rentMode");
        }
        if (!hasResolvableLocation(slots.getLocationName())) {
            missing.add("locationName");
        }
        return missing;
    }

    private AiRecommendStage deriveStage(String lastStage,
                                         TurnInput input,
                                         AiRecommendSlots slots,
                                         List<String> missingSlots) {
        if (!hasResolvableLocation(slots.getLocationName())) {
            return AiRecommendStage.ASK;
        }
        if (missingSlots.isEmpty()) {
            return AiRecommendStage.SEARCH;
        }
        if (input.isPreviewSelection()) {
            return AiRecommendStage.REFINE;
        }
        if (AiRecommendStage.PREVIEW.name().equals(lastStage) || AiRecommendStage.REFINE.name().equals(lastStage)) {
            return AiRecommendStage.REFINE;
        }
        return AiRecommendStage.PREVIEW;
    }

    private AiRecommendChatVO toChatVO(AiRecommendSessionState state,
                                       AiRecommendStage stage,
                                       String assistantReply,
                                       List<String> missingSlots,
                                       AiPreviewVO preview,
                                       SmartGuideResultVO recommendation) {
        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId(state.getSessionId());
        vo.setStage(stage.name());
        vo.setAssistantReply(assistantReply);
        vo.setSlots(toSlotsVO(state.getSlots()));
        vo.setMissingSlots(new ArrayList<>(missingSlots));
        vo.setPreview(preview);
        vo.setRecommendation(recommendation);
        return vo;
    }

    private AiRecommendSlotsVO toSlotsVO(AiRecommendSlots slots) {
        AiRecommendSlotsVO vo = new AiRecommendSlotsVO();
        vo.setCity(slots.getCity());
        vo.setLocationName(slots.getLocationName());
        vo.setBudgetYuan(slots.getBudgetYuan());
        vo.setBudgetScope(slots.getBudgetScope());
        vo.setRentMode(slots.getRentMode());
        vo.setPriority(slots.getPriority());
        vo.setPreferences(new ArrayList<>(slots.getPreferences()));
        return vo;
    }

    private void appendUser(AiRecommendSessionState state, String message) {
        List<AiRecommendTurn> history = new ArrayList<>(state.getHistory());
        history.add(AiRecommendTurn.user(message));
        state.setHistory(trimHistory(history));
    }

    private void appendAssistant(AiRecommendSessionState state, String message) {
        List<AiRecommendTurn> history = new ArrayList<>(state.getHistory());
        history.add(AiRecommendTurn.assistant(message));
        state.setHistory(trimHistory(history));
    }

    private AiRecommendSessionState buildPromptState(AiRecommendSessionState state) {
        return AiRecommendSessionState.builder()
                .userId(state.getUserId())
                .sessionId(state.getSessionId())
                .summary(state.getSummary())
                .stage(state.getStage())
                .slots(state.getSlots())
                .history(trimPromptHistory(state.getHistory()))
                .build();
    }

    private List<AiRecommendTurn> trimHistory(List<AiRecommendTurn> history) {
        int limit = Math.max(historyLimit, 1);
        if (history.size() <= limit) {
            return new ArrayList<>(history);
        }
        return new ArrayList<>(history.subList(history.size() - limit, history.size()));
    }

    private List<AiRecommendTurn> trimPromptHistory(List<AiRecommendTurn> history) {
        List<AiRecommendTurn> safeHistory = history == null ? new ArrayList<>() : history;
        int limit = Math.max(promptHistoryLimit, 1);
        if (safeHistory.size() <= limit) {
            return new ArrayList<>(safeHistory);
        }
        return new ArrayList<>(safeHistory.subList(safeHistory.size() - limit, safeHistory.size()));
    }

    private String finalizeReply(AiRecommendStage stage,
                                 String reply,
                                 AiPreviewVO preview,
                                 SmartGuideResultVO recommendation,
                                 List<String> missingSlots,
                                 AiRecommendSlots slots) {
        return switch (stage) {
            case ASK -> ensureAskReply(reply, missingSlots, slots);
            case PREVIEW -> ensurePreviewReply(reply, preview);
            case REFINE -> ensureRefineReply(reply, preview, missingSlots);
            case SEARCH -> ensureSearchReply(reply, recommendation);
        };
    }

    private String normalizeReply(String reply, boolean searchReady) {
        if (StringUtils.hasText(reply)) {
            return reply.trim();
        }
        return searchReady
                ? "条件已经齐了，我现在开始按这些条件推荐房源。"
                : "我先帮你整理需求。你可以继续补充区域、预算，或者整租/合租偏好。";
    }

    private String ensureAskReply(String reply, List<String> missingSlots, AiRecommendSlots slots) {
        if (!StringUtils.hasText(reply) || looksLikeSearchCommitment(reply)) {
            return buildSearchBlockedReply(missingSlots, slots);
        }
        return reply.trim();
    }

    private String ensureSearchReply(String reply, SmartGuideResultVO recommendation) {
        if (!hasRecommendationItems(recommendation)) {
            return "当前没有符合条件的真实房源，你可以放宽预算、扩大区域，或者调整整租/合租偏好，我再继续帮你找。";
        }
        return normalizeReply(reply, true);
    }

    private String ensurePreviewReply(String reply, AiPreviewVO preview) {
        if (!StringUtils.hasText(reply) || looksLikeSearchCommitment(reply)) {
            return buildPreviewReply(preview);
        }
        return reply.trim();
    }

    private String ensureRefineReply(String reply, AiPreviewVO preview, List<String> missingSlots) {
        if (!StringUtils.hasText(reply) || looksLikeSearchCommitment(reply)) {
            return buildRefineReply(preview, missingSlots);
        }
        return reply.trim();
    }

    private boolean looksLikeSearchCommitment(String reply) {
        if (!StringUtils.hasText(reply)) {
            return false;
        }
        String normalized = reply.replace(" ", "").toLowerCase(Locale.ROOT);
        return normalized.contains("search")
                || normalized.contains("listing")
                || normalized.contains("find")
                || normalized.contains("查房")
                || normalized.contains("搜索")
                || normalized.contains("房源")
                || normalized.contains("匹配");
    }

    private String buildSearchBlockedReply(List<String> missingSlots, AiRecommendSlots slots) {
        if (missingSlots.contains("budgetYuan") && slots.getBudgetYuan() != null && !isBudgetUsable(slots.getBudgetYuan())) {
            return "你给的预算目前不在可用范围内，请给我一个 300 到 50000 之间的月租预算。";
        }
        if (missingSlots.contains("locationName")) {
            return "我还不能开始查房，先告诉我你想看的区域。";
        }
        return "我先把方向理一理，还差" + String.join("、", missingSlots.stream().map(this::toSlotLabel).toList()) + "。";
    }

    private String buildPreviewReply(AiPreviewVO preview) {
        if (preview == null || !StringUtils.hasText(preview.getLocationName())) {
            return "我先看了一下附近的真实房源，可以先选一个方向继续收窄。";
        }
        return "我先看了" + preview.getLocationName() + "附近的真实房源，我们可以先挑一个方向继续收窄。";
    }

    private String buildPreviewUnavailableReply(List<String> missingSlots) {
        if (missingSlots.contains("locationName")) {
            return "我还没法形成可靠的预览，先告诉我更具体的区域。";
        }
        return "我还没法形成可靠的预览，你可以换一个区域，或者继续补充偏好。";
    }

    private String buildRefineReply(AiPreviewVO preview, List<String> missingSlots) {
        if (missingSlots.isEmpty()) {
            return "方向已经收窄好了，我现在开始正式找房。";
        }
        return "这个方向我记下来了，接下来还差" + String.join("、", missingSlots.stream().map(this::toSlotLabel).toList()) + "。";
    }

    private String latestAssistantReply(AiRecommendSessionState state) {
        List<AiRecommendTurn> history = state.getHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            AiRecommendTurn turn = history.get(i);
            if ("assistant".equals(turn.getRole()) && StringUtils.hasText(turn.getContent())) {
                return turn.getContent();
            }
        }
        return "你好，可以先告诉我想看的区域，或者直接说你的预算和整租/合租偏好。";
    }

    private String resolvePromptMessage(AiRecommendChatReqDTO reqDTO) {
        if (reqDTO != null && StringUtils.hasText(reqDTO.getMessage())) {
            return reqDTO.getMessage().trim();
        }
        AiRecommendInteractionDTO interaction = reqDTO == null ? null : reqDTO.getInteraction();
        if (interaction != null && StringUtils.hasText(interaction.getLabel())) {
            return interaction.getLabel().trim();
        }
        return "interaction";
    }

    private AiRecommendSlots toSlotsPatch(AiRecommendInteractionDTO interaction) {
        if (interaction == null || interaction.getSlotPatch() == null) {
            return new AiRecommendSlots();
        }
        AiRecommendInteractionSlotPatchDTO slotPatch = interaction.getSlotPatch();
        List<String> preferences = slotPatch.getPreferences() == null ? List.of() : slotPatch.getPreferences();
        return AiRecommendSlots.builder()
                .locationName(slotPatch.getLocationName())
                .budgetYuan(slotPatch.getBudgetYuan())
                .budgetScope(slotPatch.getBudgetScope())
                .rentMode(slotPatch.getRentMode())
                .priority(slotPatch.getPriority())
                .preferences(preferences)
                .build();
    }

    private AiPreviewVO buildPreviewSafely(AiRecommendSlots slots) {
        if (!hasResolvableLocation(slots.getLocationName())) {
            return null;
        }
        try {
            return previewService.build(slots.getLocationName(), slots.getBudgetYuan(), slots.getBudgetScope(), slots.getRentMode());
        } catch (Exception ex) {
            return null;
        }
    }

    private AiRecommendStage parseStage(String value) {
        if (!StringUtils.hasText(value)) {
            return AiRecommendStage.ASK;
        }
        try {
            return AiRecommendStage.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AiRecommendStage.ASK;
        }
    }

    private String normalizeBudgetScope(String value) {
        String normalized = normalizeToken(value);
        if ("TOTAL".equals(normalized) || "RENT_ONLY".equals(normalized)) {
            return normalized;
        }
        return defaultBudgetScope;
    }

    private String normalizeDefaultBudgetScope(String value) {
        String normalized = normalizeToken(value);
        if ("TOTAL".equals(normalized) || "RENT_ONLY".equals(normalized)) {
            return normalized;
        }
        return DEFAULT_BUDGET_SCOPE;
    }

    private String normalizeRentMode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if ("WHOLE".equals(normalized)) {
            return "WHOLE";
        }
        if ("SHARED".equals(normalized)) {
            return "SHARED";
        }
        String compact = trimmed.replace(" ", "").toLowerCase(Locale.ROOT);
        if (compact.contains("整租") || compact.contains("whole") || compact.contains("entire")) {
            return "WHOLE";
        }
        if (compact.contains("合租") || compact.contains("shared") || compact.contains("roommate")) {
            return "SHARED";
        }
        return null;
    }

    private String normalizeToken(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private boolean isBudgetUsable(Integer budgetYuan) {
        return budgetYuan != null && budgetYuan >= MIN_BUDGET_YUAN && budgetYuan <= MAX_BUDGET_YUAN;
    }

    private boolean hasResolvableLocation(String locationName) {
        return StringUtils.hasText(locationName);
    }

    private String toSlotLabel(String slot) {
        return switch (slot) {
            case "budgetYuan" -> "预算";
            case "rentMode" -> "整租/合租";
            case "locationName" -> "区域";
            default -> slot;
        };
    }

    private boolean hasPreference(AiRecommendSlots slots, String preference) {
        return slots != null
                && ((slots.getPreferences() != null
                && slots.getPreferences().stream().anyMatch(item -> preference.equalsIgnoreCase(item)))
                || (slots.getWeightedPreferences() != null
                && slots.getWeightedPreferences().stream()
                .map(AiWeightedPreference::getPreferenceKey)
                .anyMatch(item -> preference.equalsIgnoreCase(item))));
    }

    private boolean hasRankPreference(AiRecommendSlots slots, String preference) {
        return hasRecallPreference(slots, preference);
    }

    private boolean hasRecallPreference(AiRecommendSlots slots, String preference) {
        if (slots == null || !StringUtils.hasText(preference)) {
            return false;
        }
        if (slots.getWeightedPreferences() != null && !slots.getWeightedPreferences().isEmpty()) {
            return slots.getWeightedPreferences().stream()
                    .filter(item -> item != null && StringUtils.hasText(item.getPreferenceKey()))
                    .anyMatch(item -> preference.equalsIgnoreCase(item.getPreferenceKey())
                            && item.getWeightLevel() != AiPreferenceWeightLevel.LOW);
        }
        return slots.getPreferences() != null
                && slots.getPreferences().stream().anyMatch(item -> preference.equalsIgnoreCase(item));
    }

    List<AiWeightedPreference> normalizeWeightedPreferences(AiRecommendSlots slots) {
        AiRecommendSlots source = slots == null ? new AiRecommendSlots() : slots;
        List<AiWeightedPreference> explicit = source.getWeightedPreferences() == null
                ? List.of()
                : source.getWeightedPreferences();
        if (!explicit.isEmpty()) {
            List<AiWeightedPreference> normalized = new ArrayList<>();
            for (AiWeightedPreference weightedPreference : explicit) {
                if (weightedPreference == null || !StringUtils.hasText(weightedPreference.getPreferenceKey())) {
                    continue;
                }
                String preferenceKey = weightedPreference.getPreferenceKey().trim();
                AiPreferenceWeightLevel weightLevel = weightedPreference.getWeightLevel() != null
                        ? weightedPreference.getWeightLevel()
                        : resolveWeightLevel(source, preferenceKey);
                normalized.add(AiWeightedPreference.builder()
                        .preferenceKey(preferenceKey)
                        .weightLevel(weightLevel)
                        .relaxable(weightedPreference.isRelaxable() || weightLevel == AiPreferenceWeightLevel.LOW)
                        .build());
            }
            return deduplicateWeightedPreferences(normalized);
        }

        List<AiWeightedPreference> normalized = new ArrayList<>();
        for (String preferenceKey : source.getPreferences() == null ? List.<String>of() : source.getPreferences()) {
            if (!StringUtils.hasText(preferenceKey)) {
                continue;
            }
            String normalizedKey = preferenceKey.trim();
            AiPreferenceWeightLevel weightLevel = resolveWeightLevel(source, normalizedKey);
            normalized.add(AiWeightedPreference.builder()
                    .preferenceKey(normalizedKey)
                    .weightLevel(weightLevel)
                    .relaxable(weightLevel == AiPreferenceWeightLevel.LOW)
                    .build());
        }
        return deduplicateWeightedPreferences(normalized);
    }

    private AiPreferenceWeightLevel resolveWeightLevel(AiRecommendSlots slots, String preferenceKey) {
        String normalizedPriority = normalizeToken(slots == null ? null : slots.getPriority());
        if ("COMMUTE".equals(normalizedPriority) && "nearSubway".equalsIgnoreCase(preferenceKey)) {
            return AiPreferenceWeightLevel.HIGH;
        }
        if (hasSoftPreferenceSignal(slots, preferenceKey)) {
            return AiPreferenceWeightLevel.LOW;
        }
        return AiPreferenceWeightLevel.MEDIUM;
    }

    private boolean hasSoftPreferenceSignal(AiRecommendSlots slots, String preferenceKey) {
        if (slots == null || !StringUtils.hasText(preferenceKey) || slots.getWeightedPreferences() == null) {
            return false;
        }
        for (AiWeightedPreference weightedPreference : slots.getWeightedPreferences()) {
            if (weightedPreference == null || !StringUtils.hasText(weightedPreference.getPreferenceKey())) {
                continue;
            }
            if (!preferenceKey.equalsIgnoreCase(weightedPreference.getPreferenceKey().trim())) {
                continue;
            }
            return weightedPreference.getWeightLevel() == AiPreferenceWeightLevel.LOW
                    || weightedPreference.isRelaxable();
        }
        return false;
    }

    private List<AiWeightedPreference> deduplicateWeightedPreferences(List<AiWeightedPreference> preferences) {
        Map<String, AiWeightedPreference> deduplicated = new LinkedHashMap<>();
        for (AiWeightedPreference preference : preferences) {
            if (preference == null || !StringUtils.hasText(preference.getPreferenceKey())) {
                continue;
            }
            deduplicated.putIfAbsent(preference.getPreferenceKey(), preference);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<String> normalizePreferenceKeys(List<String> preferences, List<AiWeightedPreference> weightedPreferences) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (preferences != null) {
            for (String preference : preferences) {
                if (StringUtils.hasText(preference)) {
                    keys.add(preference.trim());
                }
            }
        }
        if (weightedPreferences != null) {
            for (AiWeightedPreference weightedPreference : weightedPreferences) {
                if (weightedPreference != null && StringUtils.hasText(weightedPreference.getPreferenceKey())) {
                    keys.add(weightedPreference.getPreferenceKey().trim());
                }
            }
        }
        return new ArrayList<>(keys);
    }

    private Map<String, Integer> buildPreferenceWeightMap(AiRecommendSlots slots) {
        Map<String, Integer> weightMap = new LinkedHashMap<>();
        if (slots == null || slots.getWeightedPreferences() == null) {
            return weightMap;
        }
        for (AiWeightedPreference weightedPreference : slots.getWeightedPreferences()) {
            if (weightedPreference == null || !StringUtils.hasText(weightedPreference.getPreferenceKey())) {
                continue;
            }
            weightMap.put(weightedPreference.getPreferenceKey(), toWeightValue(weightedPreference.getWeightLevel()));
        }
        return weightMap;
    }

    private int toWeightValue(AiPreferenceWeightLevel weightLevel) {
        if (weightLevel == null) {
            return 2;
        }
        return switch (weightLevel) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    private Boolean inferBudgetRelaxable(String promptMessage) {
        if (!StringUtils.hasText(promptMessage)) {
            return null;
        }
        String normalized = promptMessage.replace(" ", "").toLowerCase(Locale.ROOT);
        if (normalized.contains("稍微放")
                || normalized.contains("放宽预算")
                || normalized.contains("预算可以")
                || normalized.contains("超一点")
                || normalized.contains("加一点")) {
            return Boolean.TRUE;
        }
        return null;
    }

    private String resolveRankingRentMode(String rentMode) {
        if ("WHOLE".equals(rentMode)) {
            return "1";
        }
        if ("SHARED".equals(rentMode)) {
            return "2";
        }
        return null;
    }

    private BigDecimal toYuan(Integer cent) {
        if (cent == null) {
            return null;
        }
        return BigDecimal.valueOf(cent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
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

    private Map<Long, HouseRecallEvidence> buildEvidenceByHouseId(HouseRecallResult recallResult) {
        Map<Long, HouseRecallEvidence> evidenceByHouseId = new LinkedHashMap<>();
        if (recallResult == null || recallResult.candidates() == null) {
            return evidenceByHouseId;
        }
        for (HouseRecallCandidate candidate : recallResult.candidates()) {
            if (candidate == null || candidate.house() == null || candidate.house().getId() == null) {
                continue;
            }
            evidenceByHouseId.put(candidate.house().getId(), candidate.recallEvidence());
        }
        return evidenceByHouseId;
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private boolean isEnabled(Integer value) {
        return value != null && value == 1;
    }

    private boolean hasRecommendationItems(SmartGuideResultVO recommendation) {
        return recommendation != null
                && recommendation.getRecommendations() != null
                && !recommendation.getRecommendations().isEmpty();
    }

    private boolean hasRelaxedBudgetMatch(HouseRankResult rankResult, Map<Long, HouseRecallEvidence> evidenceByHouseId) {
        if (rankResult == null || rankResult.currentPageItems() == null || evidenceByHouseId == null) {
            return false;
        }
        for (HouseRankedItem rankedItem : rankResult.currentPageItems()) {
            if (rankedItem == null || rankedItem.house() == null || rankedItem.house().getId() == null) {
                continue;
            }
            HouseRecallEvidence evidence = evidenceByHouseId.get(rankedItem.house().getId());
            if (evidence != null && evidence.relaxedBudgetApplied()) {
                return true;
            }
        }
        return false;
    }

    private record TurnInput(
            String promptMessage,
            String transcriptMessage,
            AiRecommendSlots slotPatch,
            boolean isPreviewSelection,
            String previewGroupKey
    ) {
    }

    private record RecommendationReasonBundle(
            String summary,
            List<String> primaryReasons,
            List<String> secondaryReasons,
            List<String> relaxationNotes,
            List<String> reasons
    ) {
    }
}

package cn.yy.myrent.service.ai;

import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.dto.AiRecommendInteractionDTO;
import cn.yy.myrent.dto.AiRecommendInteractionSlotPatchDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.vo.AiPreviewVO;
import cn.yy.myrent.vo.AiRecommendChatVO;
import cn.yy.myrent.vo.AiRecommendSlotsVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@ConditionalOnBean(AiRecommendDecisionClient.class)
@ConditionalOnProperty(value = "myrent.ai.recommend.enabled", havingValue = "true")
public class AiRecommendServiceImpl implements AiRecommendService {

    private static final int MIN_BUDGET_YUAN = 300;
    private static final int MAX_BUDGET_YUAN = 50000;
    private static final String PREVIEW_SELECTION_TYPE = "PREVIEW_SELECTION";

    private final AiRecommendDecisionClient decisionClient;
    private final AiRecommendStateStore stateStore;
    private final IHouseService houseService;
    private final AiRecommendSummaryBuilder summaryBuilder;
    private final AiPreviewService previewService;
    private final int historyLimit;
    private final int promptHistoryLimit;
    private final String defaultBudgetScope;

    public AiRecommendServiceImpl(AiRecommendDecisionClient decisionClient,
                                  AiRecommendStateStore stateStore,
                                  IHouseService houseService,
                                  AiRecommendSummaryBuilder summaryBuilder,
                                  AiPreviewService previewService,
                                  @Value("${myrent.ai.recommend.history-limit:10}") int historyLimit,
                                  @Value("${myrent.ai.recommend.prompt-history-limit:6}") int promptHistoryLimit,
                                  @Value("${myrent.ai.recommend.default-budget-scope:RENT_ONLY}") String defaultBudgetScope) {
        this.decisionClient = decisionClient;
        this.stateStore = stateStore;
        this.houseService = houseService;
        this.summaryBuilder = summaryBuilder;
        this.previewService = previewService;
        this.historyLimit = historyLimit;
        this.promptHistoryLimit = promptHistoryLimit;
        this.defaultBudgetScope = normalizeBudgetScope(defaultBudgetScope);
    }

    @Override
    public AiRecommendChatVO getOrCreateSession(Long userId) {
        AiRecommendSessionState state = normalizeState(stateStore.loadOrCreate(userId), userId);
        if (state.getHistory().isEmpty()) {
            String opening = "你好，可以先告诉我想看的区域，或者直接说你想找什么样的房子。";
            appendAssistant(state, opening);
            state.setStage(AiRecommendStage.ASK.name());
            state.setSummary(summaryBuilder.build(state.getSlots(), buildMissingSlots(state.getSlots())));
            stateStore.save(state);
            return toChatVO(state, AiRecommendStage.ASK, opening, buildMissingSlots(state.getSlots()), null, null);
        }
        return toChatVO(
                state,
                parseStage(state.getStage()),
                latestAssistantReply(state),
                buildMissingSlots(state.getSlots()),
                null,
                null
        );
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
                preview = null;
                assistantReply = buildPreviewUnavailableReply(missingSlots, mergedSlots);
            }
        }

        if (stage == AiRecommendStage.SEARCH) {
            try {
                recommendation = houseService.smartGuide(buildSmartGuideReq(mergedSlots));
            } catch (Exception ex) {
                preview = buildPreviewSafely(mergedSlots);
                stage = preview != null && preview.getGroups() != null && !preview.getGroups().isEmpty()
                        ? AiRecommendStage.REFINE
                        : AiRecommendStage.ASK;
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
        List<String> preferences = source.getPreferences() == null ? new ArrayList<>() : source.getPreferences().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        return AiRecommendSlots.builder()
                .city(normalizeText(source.getCity()))
                .locationName(normalizeText(source.getLocationName()))
                .budgetYuan(source.getBudgetYuan())
                .budgetScope(normalizeBudgetScope(source.getBudgetScope()))
                .rentMode(normalizeRentMode(source.getRentMode()))
                .priority(normalizeToken(source.getPriority()))
                .preferences(new ArrayList<>(preferences))
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

    private SmartGuideReqDTO buildSmartGuideReq(AiRecommendSlots slots) {
        SmartGuideReqDTO reqDTO = new SmartGuideReqDTO();
        reqDTO.setBudgetYuan(slots.getBudgetYuan());
        reqDTO.setBudgetScope(slots.getBudgetScope());
        reqDTO.setRentMode(slots.getRentMode());
        reqDTO.setLocationName(slots.getLocationName());
        reqDTO.setPage(1);
        reqDTO.setSize(10);
        return reqDTO;
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
            case SEARCH -> normalizeReply(reply, recommendation != null);
        };
    }

    private String normalizeReply(String reply, boolean searchReady) {
        if (StringUtils.hasText(reply)) {
            return reply.trim();
        }
        if (searchReady) {
            return "条件已经齐了，我现在开始帮你查找房源。";
        }
        return "我先帮你整理需求。你可以继续补充区域、预算，或者告诉我是整租还是合租。";
    }

    private String ensureAskReply(String reply, List<String> missingSlots, AiRecommendSlots slots) {
        if (!StringUtils.hasText(reply) || looksLikeSearchCommitment(reply)) {
            return buildSearchBlockedReply(missingSlots, slots);
        }
        return reply.trim();
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
                || normalized.contains("搜")
                || normalized.contains("查")
                || normalized.contains("房源")
                || normalized.contains("匹配");
    }

    private String buildSearchBlockedReply(List<String> missingSlots, AiRecommendSlots slots) {
        if (missingSlots.contains("budgetYuan") && slots.getBudgetYuan() != null && !isBudgetUsable(slots.getBudgetYuan())) {
            return "你给的预算目前不在可用范围内，请给我一个 300 到 50000 之间的月租预算。";
        }
        if (missingSlots.contains("locationName")) {
            return "我还不能开始查房，先告诉我你想看的区域，我再基于真实房源给你几个方向。";
        }

        List<String> labels = missingSlots.stream()
                .map(this::toSlotLabel)
                .toList();
        return "我先把方向理一理，还差 " + String.join("、", labels) + "，补上后我就能继续往下收窄。";
    }

    private String buildPreviewReply(AiPreviewVO preview) {
        if (preview == null || !StringUtils.hasText(preview.getLocationName())) {
            return "我先看了下这个区域附近的真实房源，目前可以先挑一个方向继续收窄。";
        }
        int groupCount = preview.getGroups() == null ? 0 : preview.getGroups().size();
        if (groupCount > 0) {
            return "我先看了下" + preview.getLocationName() + "附近的真实房源，目前大致有" + groupCount + "种方向可以继续收窄。";
        }
        return "我先看了下" + preview.getLocationName() + "附近的真实房源，我们可以继续挑一个方向往下看。";
    }

    private String buildPreviewUnavailableReply(List<String> missingSlots, AiRecommendSlots slots) {
        if (missingSlots.contains("locationName")) {
            return "我还没法形成可靠的预览，先告诉我更具体的区域。";
        }
        if (missingSlots.isEmpty()) {
            return "我还没法形成可靠的预览，你可以换一个区域，或者继续补充偏好。";
        }
        List<String> labels = missingSlots.stream()
                .map(this::toSlotLabel)
                .toList();
        return "我还没法形成可靠的预览，先补充 " + String.join("、", labels) + "，我再继续帮你缩小范围。";
    }

    private String buildRefineReply(AiPreviewVO preview, List<String> missingSlots) {
        if (missingSlots.isEmpty()) {
            return "方向已经收窄好了，我现在开始按这个思路正式找房。";
        }
        List<String> labels = missingSlots.stream()
                .map(this::toSlotLabel)
                .toList();
        String prefix = preview != null && StringUtils.hasText(preview.getLocationName())
                ? "我先按这个方向继续收窄。"
                : "这个方向我记下来了。";
        return prefix + " 接下来还差 " + String.join("、", labels) + "，补上后我就能正式开始找房。";
    }

    private String latestAssistantReply(AiRecommendSessionState state) {
        List<AiRecommendTurn> history = state.getHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            AiRecommendTurn turn = history.get(i);
            if ("assistant".equals(turn.getRole()) && StringUtils.hasText(turn.getContent())) {
                return turn.getContent();
            }
        }
        return "你好，可以先告诉我想看的区域，或者直接说你想找什么样的房子。";
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
            return previewService.build(
                    slots.getLocationName(),
                    slots.getBudgetYuan(),
                    slots.getBudgetScope(),
                    slots.getRentMode()
            );
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
        return "TOTAL".equals(defaultBudgetScope) ? "TOTAL" : "RENT_ONLY";
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

    private record TurnInput(
            String promptMessage,
            String transcriptMessage,
            AiRecommendSlots slotPatch,
            boolean isPreviewSelection,
            String previewGroupKey
    ) {
    }
}

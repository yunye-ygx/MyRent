package cn.yy.myrent.service.ai;

import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.service.IHouseService;
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

    private final AiRecommendDecisionClient decisionClient;
    private final AiRecommendStateStore stateStore;
    private final IHouseService houseService;
    private final AiRecommendSummaryBuilder summaryBuilder;
    private final int historyLimit;
    private final int promptHistoryLimit;
    private final String defaultBudgetScope;

    public AiRecommendServiceImpl(AiRecommendDecisionClient decisionClient,
                                  AiRecommendStateStore stateStore,
                                  IHouseService houseService,
                                  AiRecommendSummaryBuilder summaryBuilder,
                                  @Value("${myrent.ai.recommend.history-limit:10}") int historyLimit,
                                  @Value("${myrent.ai.recommend.prompt-history-limit:6}") int promptHistoryLimit,
                                  @Value("${myrent.ai.recommend.default-budget-scope:RENT_ONLY}") String defaultBudgetScope) {
        this.decisionClient = decisionClient;
        this.stateStore = stateStore;
        this.houseService = houseService;
        this.summaryBuilder = summaryBuilder;
        this.historyLimit = historyLimit;
        this.promptHistoryLimit = promptHistoryLimit;
        this.defaultBudgetScope = normalizeBudgetScope(defaultBudgetScope);
    }

    @Override
    public AiRecommendChatVO getOrCreateSession(Long userId) {
        AiRecommendSessionState state = normalizeState(stateStore.loadOrCreate(userId), userId);
        if (state.getHistory().isEmpty()) {
            String opening = "你好，可以先告诉我预算、区域、整租/合租，或者直接说你想找什么样的房子。";
            appendAssistant(state, opening);
            state.setSummary(summaryBuilder.build(state.getSlots(), buildMissingSlots(state.getSlots())));
            stateStore.save(state);
            return toChatVO(state, "ASK", opening, buildMissingSlots(state.getSlots()), null);
        }
        return toChatVO(state, "ASK", latestAssistantReply(state), buildMissingSlots(state.getSlots()), null);
    }

    @Override
    public AiRecommendChatVO chat(Long userId, AiRecommendChatReqDTO reqDTO) {
        AiRecommendSessionState state = normalizeState(stateStore.loadOrCreate(userId), userId);
        String message = reqDTO.getMessage().trim();
        appendUser(state, message);

        AiRecommendDecision decision;
        try {
            decision = decisionClient.decide(buildPromptState(state), message);
        } catch (Exception ex) {
            decision = fallbackDecision(state.getSlots());
        }

        AiRecommendSlots mergedSlots = mergeSlots(state.getSlots(), decision.getSlots());
        String inferredRentMode = normalizeRentMode(message);
        if (StringUtils.hasText(inferredRentMode)) {
            mergedSlots.setRentMode(inferredRentMode);
        }
        state.setSlots(mergedSlots);

        List<String> missingSlots = buildMissingSlots(mergedSlots);
        state.setSummary(summaryBuilder.build(mergedSlots, missingSlots));
        SmartGuideResultVO recommendation = null;
        String action = deriveAction(mergedSlots, message, missingSlots);
        String assistantReply = normalizeReply(decision.getReply(), "SEARCH".equals(action));

        if ("SEARCH".equals(action)) {
            try {
                recommendation = houseService.smartGuide(buildSmartGuideReq(mergedSlots));
            } catch (Exception ex) {
                action = "ADVISE";
                assistantReply = "我先记下你的条件了，不过刚才查询房源时出了点问题。你可以稍后再试，或者继续补充偏好，我先帮你整理需求。";
            }
        } else if ("ASK".equals(action)) {
            assistantReply = ensureAskReply(assistantReply, missingSlots, mergedSlots);
        } else {
            assistantReply = ensureAdviseReply(assistantReply, mergedSlots);
        }

        appendAssistant(state, assistantReply);
        stateStore.save(state);
        return toChatVO(state, action, assistantReply, missingSlots, recommendation);
    }

    @Override
    public AiRecommendChatVO reset(Long userId) {
        stateStore.reset(userId);
        AiRecommendSessionState state = AiRecommendSessionState.empty(userId);
        state.setSlots(normalizeSlots(state.getSlots()));
        String opening = "已经帮你重新开始了。你可以直接告诉我预算、区域、整租/合租。";
        appendAssistant(state, opening);
        state.setSummary(summaryBuilder.build(state.getSlots(), buildMissingSlots(state.getSlots())));
        stateStore.save(state);
        return toChatVO(state, "ASK", opening, buildMissingSlots(state.getSlots()), null);
    }

    private AiRecommendDecision fallbackDecision(AiRecommendSlots slots) {
        return AiRecommendDecision.builder()
                .reply("我先继续帮你整理条件。你可以补充预算、区域，或者告诉我是整租还是合租。")
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
        if (!StringUtils.hasText(slots.getLocationName())) {
            missing.add("locationName");
        }
        return missing;
    }

    private String deriveAction(AiRecommendSlots slots, String userMessage, List<String> missingSlots) {
        if (missingSlots.isEmpty()) {
            return "SEARCH";
        }
        if (shouldAdviseInsteadOfAsk(userMessage)) {
            return "ADVISE";
        }
        return "ASK";
    }

    private boolean shouldAdviseInsteadOfAsk(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        String normalized = userMessage.replace(" ", "").toLowerCase(Locale.ROOT);
        return normalized.contains("建议")
                || normalized.contains("怎么选")
                || normalized.contains("没想好")
                || normalized.contains("先聊")
                || normalized.contains("advice")
                || normalized.contains("suggest");
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
                                       String action,
                                       String assistantReply,
                                       List<String> missingSlots,
                                       SmartGuideResultVO recommendation) {
        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId(state.getSessionId());
        vo.setAction(action);
        vo.setAssistantReply(assistantReply);
        vo.setSlots(toSlotsVO(state.getSlots()));
        vo.setMissingSlots(new ArrayList<>(missingSlots));
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

    private String normalizeReply(String reply, boolean searchReady) {
        if (StringUtils.hasText(reply)) {
            return reply.trim();
        }
        if (searchReady) {
            return "条件已经齐了，我现在开始帮你查找房源。";
        }
        return "我先帮你整理需求。你可以继续补充预算、区域，或者告诉我是整租还是合租。";
    }

    private String ensureAskReply(String reply, List<String> missingSlots, AiRecommendSlots slots) {
        if (!StringUtils.hasText(reply) || looksLikeSearchCommitment(reply)) {
            return buildSearchBlockedReply(missingSlots, slots);
        }
        return reply.trim();
    }

    private String ensureAdviseReply(String reply, AiRecommendSlots slots) {
        if (!StringUtils.hasText(reply) || looksLikeSearchCommitment(reply)) {
            return buildAdviseReply(slots);
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

    private String buildDowngradeReply(List<String> missingSlots, AiRecommendSlots slots) {
        if (missingSlots.contains("budgetYuan") && slots.getBudgetYuan() != null && !isBudgetUsable(slots.getBudgetYuan())) {
            return "你给的预算目前不在可用范围内，请给我一个 300 到 50000 之间的月租预算。";
        }

        List<String> labels = missingSlots.stream()
                .map(this::toSlotLabel)
                .toList();
        return "条件还不够完整，我先不查房。你还可以继续补充 " + String.join("、", labels) + "。";
    }

    private String buildSearchBlockedReply(List<String> missingSlots, AiRecommendSlots slots) {
        if (missingSlots.contains("budgetYuan") && slots.getBudgetYuan() != null && !isBudgetUsable(slots.getBudgetYuan())) {
            return "你给的预算目前不在可用范围内，请给我一个 300 到 50000 之间的月租预算。";
        }

        List<String> labels = missingSlots.stream()
                .map(this::toSlotLabel)
                .toList();
        return "我还不能开始查房，还差 " + String.join("、", labels) + "。";
    }

    private String buildAdviseReply(AiRecommendSlots slots) {
        String rentModeHint = slots != null && StringUtils.hasText(slots.getRentMode())
                ? slots.getRentMode()
                : "整租/合租";
        return "可以先不查房源，我先根据你的偏好给你一些方向建议。你更在意通勤、预算，还是 " + rentModeHint + " 呢？";
    }

    private String latestAssistantReply(AiRecommendSessionState state) {
        List<AiRecommendTurn> history = state.getHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            AiRecommendTurn turn = history.get(i);
            if ("assistant".equals(turn.getRole()) && StringUtils.hasText(turn.getContent())) {
                return turn.getContent();
            }
        }
        return "你好，可以先告诉我预算、区域、整租/合租，或者直接说你想找什么样的房子。";
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

    private String toSlotLabel(String slot) {
        return switch (slot) {
            case "budgetYuan" -> "预算";
            case "rentMode" -> "整租/合租";
            case "locationName" -> "区域";
            default -> slot;
        };
    }
}

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
    private final int historyLimit;
    private final String defaultBudgetScope;

    public AiRecommendServiceImpl(AiRecommendDecisionClient decisionClient,
                                  AiRecommendStateStore stateStore,
                                  IHouseService houseService,
                                  @Value("${myrent.ai.recommend.history-limit:10}") int historyLimit,
                                  @Value("${myrent.ai.recommend.default-budget-scope:RENT_ONLY}") String defaultBudgetScope) {
        this.decisionClient = decisionClient;
        this.stateStore = stateStore;
        this.houseService = houseService;
        this.historyLimit = historyLimit;
        this.defaultBudgetScope = normalizeBudgetScope(defaultBudgetScope);
    }

    @Override
    public AiRecommendChatVO getOrCreateSession(Long userId) {
        AiRecommendSessionState state = normalizeState(stateStore.loadOrCreate(userId), userId);
        if (state.getHistory().isEmpty()) {
            String opening = "先告诉我你的预算、想整租还是合租，或者你目前更在意哪个区域和通勤。";

            appendAssistant(state, opening); //初始化history
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
            decision = decisionClient.decide(state, message);
        } catch (Exception ex) {
            decision = fallbackDecision(state.getSlots());
        }

        AiRecommendSlots mergedSlots = mergeSlots(state.getSlots(), decision.getSlots());
        String inferredRentMode = normalizeRentMode(message);
        if (StringUtils.hasText(inferredRentMode)) {
            mergedSlots.setRentMode(inferredRentMode);
        }
        state.setSlots(mergedSlots);

        String action = normalizeAction(decision.getAction());
        List<String> missingSlots = buildMissingSlots(mergedSlots);
        SmartGuideResultVO recommendation = null;
        String assistantReply = normalizeReply(decision.getReply(), action);

        if ("SEARCH".equals(action)) {
            if (!missingSlots.isEmpty()) {
                action = "ASK";
                assistantReply = buildDowngradeReply(missingSlots, mergedSlots);
            } else {
                try {
                    recommendation = houseService.smartGuide(buildSmartGuideReq(mergedSlots));
                } catch (Exception ex) {
                    action = "ADVISE";
                    assistantReply = "我刚才查询真实房源时出了点问题。你可以稍后再试，或者先换一个区域、预算区间，我继续帮你缩小范围。";
                }
            }
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
        String opening = "我们重新开始。你先补充预算、区域和整租/合租偏好。";
        appendAssistant(state, opening);
        stateStore.save(state);
        return toChatVO(state, "ASK", opening, buildMissingSlots(state.getSlots()), null);
    }

    private AiRecommendDecision fallbackDecision(AiRecommendSlots slots) {
        return AiRecommendDecision.builder()
                .action("ASK")
                .reply("我这边暂时没法稳定判断，你先补充预算、区域和整租/合租信息，我继续帮你收敛条件。")
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

    private List<AiRecommendTurn> trimHistory(List<AiRecommendTurn> history) {
        int limit = Math.max(historyLimit, 1);
        if (history.size() <= limit) {
            return new ArrayList<>(history);
        }
        return new ArrayList<>(history.subList(history.size() - limit, history.size()));
    }

    private String normalizeAction(String action) {
        String normalized = normalizeToken(action);
        if (List.of("ASK", "ADVISE", "SEARCH").contains(normalized)) {
            return normalized;
        }
        return "ASK";
    }

    private String normalizeReply(String reply, String action) {
        if (StringUtils.hasText(reply)) {
            return reply.trim();
        }
        if ("ADVISE".equals(action)) {
            return "你可以先告诉我更在意预算、通勤还是居住质量，我再给你更具体的建议。";
        }
        return "先补充预算、区域和整租/合租信息，我再继续帮你判断。";
    }

    private String buildDowngradeReply(List<String> missingSlots, AiRecommendSlots slots) {
        if (missingSlots.contains("budgetYuan") && slots.getBudgetYuan() != null && !isBudgetUsable(slots.getBudgetYuan())) {
            return "为了继续筛真实房源，我还需要你确认一个合理的月预算，当前建议填写 300 到 50000 元之间。";
        }

        List<String> labels = missingSlots.stream()
                .map(this::toSlotLabel)
                .toList();
        return "要开始查真实房源，还差这些关键信息：" + String.join("、", labels) + "。";
    }

    private String latestAssistantReply(AiRecommendSessionState state) {
        List<AiRecommendTurn> history = state.getHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            AiRecommendTurn turn = history.get(i);
            if ("assistant".equals(turn.getRole()) && StringUtils.hasText(turn.getContent())) {
                return turn.getContent();
            }
        }
        return "先告诉我你的预算、想整租还是合租，或者你目前更在意哪个区域和通勤。";
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

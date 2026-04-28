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
            String opening = "鍏堝憡璇夋垜浣犵殑棰勭畻銆佹兂鏁寸杩樻槸鍚堢锛屾垨鑰呬綘鐩墠鏇村湪鎰忓摢涓尯鍩熷拰閫氬嫟銆?";

            appendAssistant(state, opening); //鍒濆鍖杊istory
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

        List<String> missingSlots = buildMissingSlots(mergedSlots);
        SmartGuideResultVO recommendation = null;
        String action = missingSlots.isEmpty() ? "SEARCH" : "ASK";
        String assistantReply = normalizeReply(decision.getReply(), "SEARCH".equals(action));

        if ("SEARCH".equals(action)) {
            try {
                recommendation = houseService.smartGuide(buildSmartGuideReq(mergedSlots));
            } catch (Exception ex) {
                action = "ADVISE";
                assistantReply = "鎴戝垰鎵嶆煡璇㈢湡瀹炴埧婧愭椂鍑轰簡鐐归棶棰樸€備綘鍙互绋嶅悗鍐嶈瘯锛屾垨鑰呭厛鎹竴涓尯鍩熴€侀绠楀尯闂达紝鎴戠户缁府浣犵缉灏忚寖鍥淬€?";
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
        String opening = "鎴戜滑閲嶆柊寮€濮嬨€備綘鍏堣ˉ鍏呴绠椼€佸尯鍩熷拰鏁寸/鍚堢鍋忓ソ銆?";
        appendAssistant(state, opening);
        stateStore.save(state);
        return toChatVO(state, "ASK", opening, buildMissingSlots(state.getSlots()), null);
    }

    private AiRecommendDecision fallbackDecision(AiRecommendSlots slots) {
        return AiRecommendDecision.builder()
                .reply("鎴戣繖杈规殏鏃舵病娉曠ǔ瀹氬垽鏂紝浣犲厛琛ュ厖棰勭畻銆佸尯鍩熷拰鏁寸/鍚堢淇℃伅锛屾垜缁х画甯綘鏀舵暃鏉′欢銆?")
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

    private String normalizeReply(String reply, boolean searchReady) {
        if (StringUtils.hasText(reply)) {
            return reply.trim();
        }
        if (searchReady) {
            return "浣犲彲浠ュ厛鍛婅瘔鎴戞洿鍦ㄦ剰棰勭畻銆侀€氬嫟杩樻槸灞呬綇璐ㄩ噺锛屾垜鍐嶇粰浣犳洿鍏蜂綋鐨勫缓璁€?";
        }
        return "鍏堣ˉ鍏呴绠椼€佸尯鍩熷拰鏁寸/鍚堢淇℃伅锛屾垜鍐嶇户缁府浣犲垽鏂€?";
    }

    private String buildDowngradeReply(List<String> missingSlots, AiRecommendSlots slots) {
        if (missingSlots.contains("budgetYuan") && slots.getBudgetYuan() != null && !isBudgetUsable(slots.getBudgetYuan())) {
            return "涓轰簡缁х画绛涚湡瀹炴埧婧愶紝鎴戣繕闇€瑕佷綘纭涓€涓悎鐞嗙殑鏈堥绠楋紝褰撳墠寤鸿濉啓 300 鍒?50000 鍏冧箣闂淬€?";
        }

        List<String> labels = missingSlots.stream()
                .map(this::toSlotLabel)
                .toList();
        return "瑕佸紑濮嬫煡鐪熷疄鎴挎簮锛岃繕宸繖浜涘叧閿俊鎭細" + String.join("銆?", labels) + "銆?";
    }

    private String latestAssistantReply(AiRecommendSessionState state) {
        List<AiRecommendTurn> history = state.getHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            AiRecommendTurn turn = history.get(i);
            if ("assistant".equals(turn.getRole()) && StringUtils.hasText(turn.getContent())) {
                return turn.getContent();
            }
        }
        return "鍏堝憡璇夋垜浣犵殑棰勭畻銆佹兂鏁寸杩樻槸鍚堢锛屾垨鑰呬綘鐩墠鏇村湪鎰忓摢涓尯鍩熷拰閫氬嫟銆?";
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
        if (compact.contains("鏁寸") || compact.contains("whole") || compact.contains("entire")) {
            return "WHOLE";
        }
        if (compact.contains("鍚堢") || compact.contains("shared") || compact.contains("roommate")) {
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
            case "budgetYuan" -> "棰勭畻";
            case "rentMode" -> "鏁寸/鍚堢";
            case "locationName" -> "鍖哄煙";
            default -> slot;
        };
    }
}

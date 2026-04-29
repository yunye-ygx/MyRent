package cn.yy.myrent.service.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

@Component
public class AiRecommendSummaryBuilder {

    public String build(AiRecommendSlots slots, List<String> missingSlots) {
        AiRecommendSlots safeSlots = slots == null ? new AiRecommendSlots() : slots;
        List<String> safePreferences = safeSlots.getPreferences() == null ? List.of() : safeSlots.getPreferences();
        List<String> safeMissingSlots = missingSlots == null ? List.of() : missingSlots;

        StringJoiner joiner = new StringJoiner("; ");
        joiner.add("city=" + safe(safeSlots.getCity()));
        joiner.add("location=" + safe(safeSlots.getLocationName()));
        joiner.add("budget=" + safe(safeSlots.getBudgetYuan()));
        joiner.add("budgetScope=" + safe(safeSlots.getBudgetScope()));
        joiner.add("rentMode=" + safe(safeSlots.getRentMode()));
        joiner.add("priority=" + safe(safeSlots.getPriority()));
        joiner.add("preferences=" + safePreferences);
        joiner.add("missing=" + safeMissingSlots);
        return joiner.toString();
    }

    private Object safe(Object value) {
        return value == null ? "null" : value;
    }
}

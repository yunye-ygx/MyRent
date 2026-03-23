package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.entity.House;

import java.util.List;

public record SmartGuideCandidateResult(
        List<House> candidates,
        boolean relaxedBudget,
        int relaxedBudgetYuan,
        int exactMatchCount
) {

    public SmartGuideCandidateResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /**
     * 构造“未触发放宽”的严格匹配结果。
     */
    public static SmartGuideCandidateResult exact(List<House> candidates, int budgetYuan) {
        int exactCount = candidates == null ? 0 : candidates.size();
        return new SmartGuideCandidateResult(candidates, false, budgetYuan, exactCount);
    }

    /**
     * 构造“触发放宽后”的结果。
     * exactMatchCount 用来记录放宽前严格命中的数量，便于前端提示和结果解释。
     */
    public static SmartGuideCandidateResult relaxed(List<House> candidates, int relaxedBudgetYuan, int exactMatchCount) {
        return new SmartGuideCandidateResult(candidates, true, relaxedBudgetYuan, exactMatchCount);
    }
}

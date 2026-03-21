package cn.yy.myrent.service.smartguide;

import java.util.List;

public record SmartGuidePrefilterResult(
        boolean esAvailable,
        boolean esQueryTimedOut,
        List<Long> candidateIds
) {

    public SmartGuidePrefilterResult {
        candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
    }

    public static SmartGuidePrefilterResult esAvailable(List<Long> candidateIds) {
        return new SmartGuidePrefilterResult(true, false, candidateIds);
    }

    public static SmartGuidePrefilterResult timedOut() {
        return new SmartGuidePrefilterResult(false, true, List.of());
    }

    public static SmartGuidePrefilterResult esUnavailable() {
        return new SmartGuidePrefilterResult(false, false, List.of());
    }
}

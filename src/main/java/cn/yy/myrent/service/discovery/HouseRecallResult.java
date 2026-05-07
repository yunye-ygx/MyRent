package cn.yy.myrent.service.discovery;

import java.util.List;

public record HouseRecallResult(
        List<HouseRecallCandidate> candidates,
        boolean esAvailable,
        boolean degraded
) {

    public HouseRecallResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}

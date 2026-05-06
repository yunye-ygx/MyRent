package cn.yy.myrent.service.smartguide;

import cn.yy.myrent.entity.House;

import java.util.List;

public record SmartGuideCandidateBundle(
        String locationName,
        double targetLatitude,
        double targetLongitude,
        boolean esAvailable,
        List<House> candidates
) {

    public SmartGuideCandidateBundle {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}

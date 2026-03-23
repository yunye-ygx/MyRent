package cn.yy.myrent.service.location;

import cn.yy.myrent.entity.LocationDict;
import cn.yy.myrent.mapper.LocationDictMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LocationResolveService {

    private final LocationDictMapper locationDictMapper;

    public ResolvedLocation resolveRequired(String requestedLocationName) {
        if (!StringUtils.hasText(requestedLocationName)) {
            throw new IllegalArgumentException("locationName cannot be blank");
        }

        List<LocationDict> locations = locationDictMapper.selectList(Wrappers.emptyWrapper());
        if (locations == null || locations.isEmpty()) {
            throw new IllegalArgumentException("location_dict has no test data");
        }

        String normalizedInput = normalizeText(requestedLocationName);
        return locations.stream()
                .map(location -> new CandidateLocation(
                        location.getName(),
                        location.getLatitude() == null ? null : location.getLatitude().doubleValue(),
                        location.getLongitude() == null ? null : location.getLongitude().doubleValue(),
                        calculateLocationMatchScore(normalizedInput, normalizeText(location.getName()))
                ))
                .filter(location -> location.latitude() != null
                        && location.longitude() != null
                        && location.matchScore() > 0)
                .max(Comparator.comparingInt(CandidateLocation::matchScore))
                .map(location -> new ResolvedLocation(location.name(), location.latitude(), location.longitude()))
                .orElseThrow(() -> new IllegalArgumentException("locationName is not found in location_dict"));
    }

    private int calculateLocationMatchScore(String input, String candidate) {
        if (!StringUtils.hasText(input) || !StringUtils.hasText(candidate)) {
            return 0;
        }
        if (candidate.equals(input)) {
            return 1000;
        }
        if (candidate.contains(input) || input.contains(candidate)) {
            return 800 - Math.abs(candidate.length() - input.length());
        }
        return 0;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record ResolvedLocation(String name, double latitude, double longitude) {
    }

    private record CandidateLocation(String name, Double latitude, Double longitude, int matchScore) {
    }
}

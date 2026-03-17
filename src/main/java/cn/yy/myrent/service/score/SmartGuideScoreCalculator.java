package cn.yy.myrent.service.score;

import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class SmartGuideScoreCalculator {

    private static final BigDecimal MATCH_WEIGHT = new BigDecimal("1000000");
    private static final BigDecimal RENT_WEIGHT = new BigDecimal("1000");

    public SmartGuideScoreResult calculate(House house,
                                           SmartGuideReqDTO reqDTO,
                                           int budgetCent,
                                           String rentKeyword) {
        BigDecimal matchScore = calculateMatchScore(house, reqDTO, rentKeyword);
        BigDecimal rentCloseScore = calculateRentCloseScore(house, budgetCent);
        LocationScore locationScore = calculateLocationScore(house, reqDTO);

        BigDecimal totalScore = matchScore.multiply(MATCH_WEIGHT)
                .add(rentCloseScore.multiply(RENT_WEIGHT))
                .add(locationScore.getLocationScore())
                .setScale(3, RoundingMode.HALF_UP);

        List<String> reasons = buildReasons(matchScore, rentCloseScore, locationScore.getDistanceKm());

        return new SmartGuideScoreResult(totalScore,
                locationScore.getDistanceKm(),
                locationScore.getCommuteMinutes(),
                reasons);
    }

    private BigDecimal calculateMatchScore(House house, SmartGuideReqDTO reqDTO, String rentKeyword) {
        String title = safeLower(house == null ? null : house.getTitle());
        String station = safeLower(reqDTO == null ? null : reqDTO.getCommuteMetroStation());
        String rent = safeLower(rentKeyword);

        BigDecimal rentModeHit = contains(title, rent) ? new BigDecimal("100") : BigDecimal.ZERO;
        BigDecimal stationHit;
        if (!StringUtils.hasText(station)) {
            stationHit = new BigDecimal("100");
        } else if (contains(title, station)) {
            stationHit = new BigDecimal("100");
        } else {
            stationHit = BigDecimal.ZERO;
        }

        return rentModeHit.multiply(new BigDecimal("0.7"))
                .add(stationHit.multiply(new BigDecimal("0.3")))
                .setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRentCloseScore(House house, int budgetCent) {
        if (house == null || house.getPrice() == null || budgetCent <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff = new BigDecimal(Math.abs((long) house.getPrice() - budgetCent));
        BigDecimal ratio = diff.divide(new BigDecimal(budgetCent), 6, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.ONE) > 0) {
            ratio = BigDecimal.ONE;
        }
        return new BigDecimal("100").multiply(BigDecimal.ONE.subtract(ratio)).setScale(3, RoundingMode.HALF_UP);
    }

    private LocationScore calculateLocationScore(House house, SmartGuideReqDTO reqDTO) {
        if (house == null
                || house.getLatitude() == null
                || house.getLongitude() == null
                || reqDTO == null
                || reqDTO.getStationLatitude() == null
                || reqDTO.getStationLongitude() == null) {
            return new LocationScore(new BigDecimal("50.000"), null, null);
        }

        double distanceKm = haversine(house.getLatitude().doubleValue(),
                house.getLongitude().doubleValue(),
                reqDTO.getStationLatitude(),
                reqDTO.getStationLongitude());

        BigDecimal distance = BigDecimal.valueOf(distanceKm).setScale(3, RoundingMode.HALF_UP);
        BigDecimal ratio = BigDecimal.valueOf(distanceKm)
                .divide(new BigDecimal("10"), 6, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.ONE) > 0) {
            ratio = BigDecimal.ONE;
        }
        BigDecimal locationScore = new BigDecimal("100")
                .multiply(BigDecimal.ONE.subtract(ratio))
                .setScale(3, RoundingMode.HALF_UP);

        int commuteMinutes = Math.max(8, (int) Math.round(distanceKm * 4.5d + 6d));
        return new LocationScore(locationScore, distance, commuteMinutes);
    }

    private List<String> buildReasons(BigDecimal matchScore, BigDecimal rentCloseScore, BigDecimal distanceKm) {
        List<String> reasons = new ArrayList<>(3);
        reasons.add("匹配度评分: " + normalizeScore(matchScore));
        reasons.add("租金贴近度评分: " + normalizeScore(rentCloseScore));
        if (distanceKm == null) {
            reasons.add("位置评分: 缺少坐标，使用中性分");
        } else {
            reasons.add("距离通勤点约 " + distanceKm.stripTrailingZeros().toPlainString() + "km");
        }
        return reasons;
    }

    private String normalizeScore(BigDecimal score) {
        if (score == null) {
            return "0";
        }
        return score.stripTrailingZeros().toPlainString();
    }

    private boolean contains(String text, String keyword) {
        return StringUtils.hasText(text) && StringUtils.hasText(keyword) && text.contains(keyword);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double radiusKm = 6371.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return radiusKm * c;
    }

    @Getter
    public static class SmartGuideScoreResult {
        private final BigDecimal score;
        private final BigDecimal distanceToMetroKm;
        private final Integer estimatedCommuteMinutes;
        private final List<String> reasons;

        public SmartGuideScoreResult(BigDecimal score,
                                     BigDecimal distanceToMetroKm,
                                     Integer estimatedCommuteMinutes,
                                     List<String> reasons) {
            this.score = score;
            this.distanceToMetroKm = distanceToMetroKm;
            this.estimatedCommuteMinutes = estimatedCommuteMinutes;
            this.reasons = reasons;
        }
    }

    @Getter
    private static class LocationScore {
        private final BigDecimal locationScore;
        private final BigDecimal distanceKm;
        private final Integer commuteMinutes;

        private LocationScore(BigDecimal locationScore, BigDecimal distanceKm, Integer commuteMinutes) {
            this.locationScore = locationScore;
            this.distanceKm = distanceKm;
            this.commuteMinutes = commuteMinutes;
        }
    }
}

package cn.yy.myrent.service.score;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.smartguide.SmartGuideQueryContext;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class SmartGuideScoreCalculator {

    private static final BigDecimal RENT_MODE_WEIGHT = new BigDecimal("10000");
    private static final BigDecimal BUDGET_WEIGHT = new BigDecimal("100");
    private static final String RENT_MODE_WHOLE = "WHOLE";
    private static final String RENT_MODE_SHARED = "SHARED";
    private static final int RENT_TYPE_WHOLE = 1;
    private static final int RENT_TYPE_SHARED = 2;

    /**
     * 计算单个房源的综合得分。
     * 这里把租住方式匹配、预算贴近度、距离目标地点的远近三部分合成一个总分，供最终排序使用。
     */
    public SmartGuideScoreResult calculate(House house,
                                           SmartGuideQueryContext queryContext) {
        BigDecimal rentModeScore = calculateRentModeScore(house, queryContext.rentMode(), queryContext.rentKeyword());
        BigDecimal budgetCloseScore = calculateBudgetCloseScore(house, queryContext);
        LocationScore locationScore = calculateLocationScore(house, queryContext);

        BigDecimal totalScore = rentModeScore.multiply(RENT_MODE_WEIGHT)
                .add(budgetCloseScore.multiply(BUDGET_WEIGHT))
                .add(locationScore.getLocationScore())
                .setScale(3, RoundingMode.HALF_UP);

        List<String> reasons = buildReasons(queryContext, rentModeScore, budgetCloseScore, locationScore.getDistanceKm());
        return new SmartGuideScoreResult(totalScore, locationScore.getDistanceKm(), locationScore.getCommuteMinutes(), reasons);
    }

    /**
     * 计算租住方式匹配分。
     * 当前版本还没有结构化的整租/合租字段，因此先用标题中是否包含对应关键词做临时判断。
     */
    private BigDecimal calculateRentModeScore(House house, String rentMode, String rentKeyword) {
        Integer rentType = house == null ? null : house.getRentType();
        if (rentType != null) {
            if (RENT_MODE_WHOLE.equalsIgnoreCase(rentMode)) {
                return rentType == RENT_TYPE_WHOLE ? new BigDecimal("100.000") : BigDecimal.ZERO;
            }
            if (RENT_MODE_SHARED.equalsIgnoreCase(rentMode)) {
                return rentType == RENT_TYPE_SHARED ? new BigDecimal("100.000") : BigDecimal.ZERO;
            }
        }
        String title = safeLower(house == null ? null : house.getTitle());
        String keyword = safeLower(rentKeyword);
        return contains(title, keyword) ? new BigDecimal("100.000") : BigDecimal.ZERO;
    }

    /**
     * 计算预算贴近度得分。
     * RENT_ONLY 按月租比较，TOTAL 按首月总成本（月租+押金）比较，越接近预算得分越高。
     */
    private BigDecimal calculateBudgetCloseScore(House house, SmartGuideQueryContext queryContext) {
        int comparableCostCent = resolveComparableCostCent(house, queryContext.totalCostScope());
        if (comparableCostCent <= 0 || queryContext.budgetCent() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal diff = new BigDecimal(Math.abs((long) comparableCostCent - queryContext.budgetCent()));
        BigDecimal ratio = diff.divide(new BigDecimal(queryContext.budgetCent()), 6, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.ONE) > 0) {
            ratio = BigDecimal.ONE;
        }
        return new BigDecimal("100").multiply(BigDecimal.ONE.subtract(ratio)).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 计算位置相关信息。
     * 包括位置分、房源到目标地点的距离，以及一个简单估算出的通勤分钟数。
     */
    private LocationScore calculateLocationScore(House house, SmartGuideQueryContext queryContext) {
        if (house == null
                || house.getLatitude() == null
                || house.getLongitude() == null) {
            return new LocationScore(new BigDecimal("50.000"), null, null);
        }

        double distanceKm = haversine(house.getLatitude().doubleValue(),
                house.getLongitude().doubleValue(),
                queryContext.targetLatitude(),
                queryContext.targetLongitude());

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

    /**
     * 组装前端展示的推荐理由。
     * 这里不直接暴露内部权重，只展示用户能理解的几个维度。
     */
    private List<String> buildReasons(SmartGuideQueryContext queryContext,
                                      BigDecimal rentModeScore,
                                      BigDecimal budgetCloseScore,
                                      BigDecimal distanceKm) {
        List<String> reasons = new ArrayList<>(3);
        reasons.add("租住方式匹配度 " + normalizeScore(rentModeScore));
        reasons.add((queryContext.totalCostScope() ? "首月总成本贴近度 " : "月租贴近度 ") + normalizeScore(budgetCloseScore));
        if (distanceKm == null) {
            reasons.add("位置评分使用中性分，房源坐标缺失");
        } else {
            reasons.add("距目标地点约 " + distanceKm.stripTrailingZeros().toPlainString() + "km");
        }
        return reasons;
    }

    /**
     * 按预算口径计算房源的可比较成本。
     * 只看月租时返回 price，看首月总成本时返回 price + depositAmount。
     */
    private int resolveComparableCostCent(House house, boolean totalCostScope) {
        if (house == null || house.getPrice() == null) {
            return 0;
        }
        int price = Math.max(house.getPrice(), 0);
        if (!totalCostScope) {
            return price;
        }
        if (house.getTotalCost() != null) {
            return Math.max(house.getTotalCost(), 0);
        }
        int deposit = house.getDepositAmount() == null ? 0 : Math.max(house.getDepositAmount(), 0);
        return price + deposit;
    }

    /**
     * 把分数转成更适合展示的字符串，去掉无意义的小数尾零。
     */
    private String normalizeScore(BigDecimal score) {
        if (score == null) {
            return "0";
        }
        return score.stripTrailingZeros().toPlainString();
    }

    /**
     * 判断文本里是否包含指定关键词。
     */
    private boolean contains(String text, String keyword) {
        return StringUtils.hasText(text) && StringUtils.hasText(keyword) && text.contains(keyword);
    }

    /**
     * 对字符串做空值保护并转成小写，便于做不区分大小写的包含判断。
     */
    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /**
     * 使用 Haversine 公式计算两组经纬度之间的球面距离，单位为公里。
     */
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

        /**
         * 封装单个房源打分后的结果对象。
         */
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

        /**
         * 封装位置维度的中间计算结果，避免在主流程里传很多零散值。
         */
        private LocationScore(BigDecimal locationScore, BigDecimal distanceKm, Integer commuteMinutes) {
            this.locationScore = locationScore;
            this.distanceKm = distanceKm;
            this.commuteMinutes = commuteMinutes;
        }
    }
}

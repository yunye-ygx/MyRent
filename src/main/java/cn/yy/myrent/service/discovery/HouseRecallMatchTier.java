package cn.yy.myrent.service.discovery;

public enum HouseRecallMatchTier {
    EXACT,
    RELAXED_BUDGET,
    RELAXED_RADIUS,
    RELAXED_BUDGET_AND_RADIUS,
    TEXT_ONLY,
    LOCATION_ONLY,
    FILTER_ONLY
}

package cn.yy.myrent.service.smartguide;

public record SmartGuideQueryContext(
        int page,
        int size,
        int budgetYuan,
        int budgetCent,
        String budgetScope,
        String rentMode,
        String rentKeyword,
        String locationName,
        double targetLatitude,
        double targetLongitude
) {

    /**
     * 判断当前预算口径是否为首月总成本模式。
     */
    public boolean totalCostScope() {
        return "TOTAL".equals(budgetScope);
    }
}

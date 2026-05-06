package cn.yy.myrent.service.ai;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateBundle;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateCollector;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateQuery;
import cn.yy.myrent.vo.AiPreviewGroupVO;
import cn.yy.myrent.vo.AiPreviewSlotPatchVO;
import cn.yy.myrent.vo.AiPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class AiPreviewServiceImpl implements AiPreviewService {

    private static final String BUDGET_SCOPE_TOTAL = "TOTAL";
    private static final String RENT_MODE_WHOLE = "WHOLE";
    private static final String RENT_MODE_SHARED = "SHARED";
    private static final int PREVIEW_CANDIDATE_LIMIT = 18;
    private static final int MIN_PREVIEW_CANDIDATE_SIZE = 2;
    private static final int MIN_FEATURE_MATCH_COUNT = 1;
    private static final int MIN_RENT_MODE_MATCH_COUNT = 2;
    private static final int MAX_GROUP_COUNT = 3;
    private static final int MAX_SAMPLE_HOUSE_IDS = 3;

    private final SmartGuideCandidateCollector candidateCollector;

    @Override
    public AiPreviewVO build(String locationName, Integer budgetYuan, String budgetScope, String rentMode) {
        SmartGuideCandidateBundle bundle = candidateCollector.collect(SmartGuideCandidateQuery.builder()
                .locationName(locationName)
                .budgetYuan(budgetYuan)
                .budgetScope(budgetScope)
                .rentMode(rentMode)
                .size(PREVIEW_CANDIDATE_LIMIT)
                .build());

        AiPreviewVO preview = new AiPreviewVO();
        preview.setLocationName(bundle.locationName());
        preview.setCandidateCount(bundle.candidates().size());
        preview.setGroups(buildGroups(bundle.candidates(), budgetScope));
        return preview;
    }

    private List<AiPreviewGroupVO> buildGroups(List<House> houses, String budgetScope) {
        if (houses == null || houses.size() < MIN_PREVIEW_CANDIDATE_SIZE) {
            return List.of();
        }

        List<AiPreviewGroupVO> groups = new ArrayList<>();
        maybeAddNearMetroGroup(groups, houses, budgetScope);
        maybeAddLowerCostGroup(groups, houses, budgetScope);
        maybeAddBooleanFeatureGroup(groups, houses,
                "private_bathroom",
                "更看重独卫",
                "这类房源更常见独立卫生间，适合优先筛独卫配置。",
                List.of("独立卫生间"),
                "privateBathroom",
                "PRIVACY",
                house -> truthy(house.getPrivateBathroom()));
        maybeAddBooleanFeatureGroup(groups, houses,
                "has_balcony",
                "更看重阳台",
                "这类房源更常见带阳台，适合优先看阳台配置。",
                List.of("带阳台"),
                "hasBalcony",
                "AMENITY",
                house -> truthy(house.getHasBalcony()));
        maybeAddBooleanFeatureGroup(groups, houses,
                "student_deposit_free",
                "更看重学生免押",
                "这类房源更常见学生免押，对首月资金安排更友好。",
                List.of("学生免押"),
                "supportStudentDepositFree",
                "PRICE",
                house -> truthy(house.getSupportStudentDepositFree()));
        maybeAddBooleanFeatureGroup(groups, houses,
                "civil_utilities",
                "更看重民水民电",
                "这类房源更常见民水民电，日常费用口径更直接。",
                List.of("民水民电"),
                "civilWaterElectric",
                "COST",
                house -> truthy(house.getCivilWaterElectric()));
        maybeAddRentModeGroup(groups, houses);

        return groups.stream()
                .limit(MAX_GROUP_COUNT)
                .toList();
    }

    private void maybeAddNearMetroGroup(List<AiPreviewGroupVO> groups, List<House> houses, String budgetScope) {
        List<House> nearMetro = filterBy(houses, house -> truthy(house.getNearSubway()));
        if (nearMetro.size() < MIN_FEATURE_MATCH_COUNT) {
            return;
        }

        groups.add(buildGroup(
                "near_metro",
                "更靠近地铁",
                "这类房源更常见近地铁，通勤更直接，但成本通常不是最低的一档。",
                List.of("近地铁", costHintLabel(budgetScope)),
                nearMetro,
                buildPatch("COMMUTE", null, List.of("nearSubway"))
        ));
    }

    private void maybeAddLowerCostGroup(List<AiPreviewGroupVO> groups, List<House> houses, String budgetScope) {
        List<House> sorted = houses.stream()
                .sorted(Comparator.comparingInt(house -> comparableCost(house, budgetScope)))
                .toList();
        List<House> cheaper = sorted.subList(0, Math.max(MIN_PREVIEW_CANDIDATE_SIZE, sorted.size() / 2));
        if (cheaper.size() < MIN_PREVIEW_CANDIDATE_SIZE) {
            return;
        }

        boolean rentOnlyScope = !BUDGET_SCOPE_TOTAL.equals(normalizeEnumValue(budgetScope));
        groups.add(buildGroup(
                "lower_total_cost",
                rentOnlyScope ? "月租压力更小" : "首月成本更低",
                rentOnlyScope
                        ? "这类房源的月租更低，预算压力会更小。"
                        : "这类房源的首月总成本更低，首付压力会更小。",
                List.of(rentOnlyScope ? "月租更低" : "首月成本更低", "预算压力更小"),
                cheaper,
                buildPatch("PRICE", null, List.of())
        ));
    }

    private void maybeAddBooleanFeatureGroup(List<AiPreviewGroupVO> groups,
                                             List<House> houses,
                                             String groupKey,
                                             String title,
                                             String summary,
                                             List<String> highlights,
                                             String preference,
                                             String priority,
                                             Predicate<House> predicate) {
        if (groups.size() >= MAX_GROUP_COUNT) {
            return;
        }

        List<House> matched = filterBy(houses, predicate);
        if (matched.size() < MIN_FEATURE_MATCH_COUNT) {
            return;
        }

        groups.add(buildGroup(
                groupKey,
                title,
                summary,
                highlights,
                matched,
                buildPatch(priority, null, List.of(preference))
        ));
    }

    private void maybeAddRentModeGroup(List<AiPreviewGroupVO> groups, List<House> houses) {
        if (groups.size() >= MAX_GROUP_COUNT) {
            return;
        }

        List<House> wholeRent = filterBy(houses, house -> Integer.valueOf(1).equals(house.getRentType()));
        List<House> sharedRent = filterBy(houses, house -> Integer.valueOf(2).equals(house.getRentType()));
        if (wholeRent.size() >= MIN_RENT_MODE_MATCH_COUNT) {
            groups.add(buildGroup(
                    "whole_rent",
                    "整租为主",
                    "这类房源更常见整租，独立性更强，但预算通常更高。",
                    List.of("整租更多"),
                    wholeRent,
                    buildPatch(null, RENT_MODE_WHOLE, List.of())
            ));
            return;
        }
        if (sharedRent.size() >= MIN_RENT_MODE_MATCH_COUNT) {
            groups.add(buildGroup(
                    "shared_rent",
                    "合租为主",
                    "这类房源更常见合租，通常对预算更友好。",
                    List.of("合租更多"),
                    sharedRent,
                    buildPatch(null, RENT_MODE_SHARED, List.of())
            ));
        }
    }

    private AiPreviewGroupVO buildGroup(String groupKey,
                                        String title,
                                        String summary,
                                        List<String> highlights,
                                        List<House> houses,
                                        AiPreviewSlotPatchVO slotPatch) {
        AiPreviewGroupVO group = new AiPreviewGroupVO();
        group.setGroupKey(groupKey);
        group.setTitle(title);
        group.setSummary(summary);
        group.setHighlights(new ArrayList<>(highlights));
        group.setSampleCount(houses.size());
        group.setSampleHouseIds(houses.stream()
                .map(House::getId)
                .filter(id -> id != null)
                .limit(MAX_SAMPLE_HOUSE_IDS)
                .toList());
        group.setSlotPatch(slotPatch);
        return group;
    }

    private AiPreviewSlotPatchVO buildPatch(String priority, String rentMode, List<String> preferences) {
        AiPreviewSlotPatchVO patch = new AiPreviewSlotPatchVO();
        patch.setPriority(priority);
        patch.setRentMode(rentMode);
        patch.setPreferences(new ArrayList<>(preferences));
        return patch;
    }

    private List<House> filterBy(List<House> houses, Predicate<House> predicate) {
        return houses.stream()
                .filter(predicate)
                .toList();
    }

    private int comparableCost(House house, String budgetScope) {
        if (house == null) {
            return Integer.MAX_VALUE;
        }

        if (BUDGET_SCOPE_TOTAL.equals(normalizeEnumValue(budgetScope))) {
            if (house.getTotalCost() != null) {
                return Math.max(house.getTotalCost(), 0);
            }
            int price = house.getPrice() == null ? 0 : Math.max(house.getPrice(), 0);
            int deposit = house.getDepositAmount() == null ? 0 : Math.max(house.getDepositAmount(), 0);
            return price + deposit;
        }

        return house.getPrice() == null ? Integer.MAX_VALUE : Math.max(house.getPrice(), 0);
    }

    private String costHintLabel(String budgetScope) {
        return BUDGET_SCOPE_TOTAL.equals(normalizeEnumValue(budgetScope)) ? "首月成本通常更高" : "月租通常更高";
    }

    private boolean truthy(Integer value) {
        return value != null && value == 1;
    }

    private String normalizeEnumValue(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}

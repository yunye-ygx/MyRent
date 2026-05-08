package cn.yy.myrent.service.search;

import cn.yy.myrent.dto.HouseKeywordSearchReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.discovery.HouseRankQuery;
import cn.yy.myrent.service.discovery.HouseRankResult;
import cn.yy.myrent.service.discovery.HouseRankedItem;
import cn.yy.myrent.service.discovery.HouseRankingProfile;
import cn.yy.myrent.service.discovery.HouseRankingService;
import cn.yy.myrent.service.discovery.HouseReasonCode;
import cn.yy.myrent.service.discovery.HouseRecallCandidate;
import cn.yy.myrent.service.discovery.HouseRecallEvidence;
import cn.yy.myrent.service.discovery.HouseRecallProfile;
import cn.yy.myrent.service.discovery.HouseRecallQuery;
import cn.yy.myrent.service.discovery.HouseRecallResult;
import cn.yy.myrent.service.discovery.HouseRecallService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HouseKeywordSearchService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final String EMPTY_RESULT_TIP = "当前未找到匹配房源";
    private static final String EMPTY_SEARCH_HOT_TIP = "当前未找到匹配房源，已为你展示当前城市热门在租房源";
    private static final String UNKNOWN_PUBLISHER_NAME = "未知发布者";
    private static final int MAX_OUTWARD_REASON_COUNT = 2;
    private static final String REASON_BOTH_TEXT_AND_LOCATION = "同时命中关键词与位置";
    private static final String REASON_TEXT_MATCH = "关键词命中";
    private static final String REASON_LOCATION_MATCH = "位置匹配";
    private static final String REASON_DISTANCE_PREFIX = "距目标地点约 ";

    private final HouseRecallService houseRecallService;
    private final HouseRankingService houseRankingService;
    private final IUserService userService;
    private final HouseHotService houseHotService;

    public HouseSearchResultVO search(HouseKeywordSearchReqDTO reqDTO) {
        String keyword = reqDTO == null || reqDTO.getKeyword() == null ? "" : reqDTO.getKeyword().trim();
        String city = reqDTO == null ? null : reqDTO.getCity();
        int page = reqDTO == null || reqDTO.getPage() == null ? DEFAULT_PAGE : Math.max(reqDTO.getPage(), 1);
        int size = reqDTO == null || reqDTO.getSize() == null
                ? DEFAULT_SIZE
                : Math.min(Math.max(reqDTO.getSize(), 1), MAX_SIZE);

        HouseRecallResult recallResult = houseRecallService.recall(HouseRecallQuery.builder()
                .keyword(keyword)
                .city(city)
                .page(page)
                .size(size)
                .recallProfile(HouseRecallProfile.KEYWORD_SEARCH)
                .build());
        HouseRankResult rankResult = houseRankingService.rank(
                recallResult.candidates(),
                HouseRankQuery.builder()
                        .page(page)
                        .size(size)
                        .rankingProfile(HouseRankingProfile.SEARCH_DEFAULT)
                        .build()
        );
        Map<Long, HouseRecallEvidence> recallEvidenceMap = buildRecallEvidenceMap(recallResult.candidates());
        List<HouseRankedItem> currentPageItems = rankResult.currentPageItems();
        if (currentPageItems.isEmpty() && StringUtils.hasText(city)) {
            return buildCityHotFallbackResult(city, page, size, recallResult.degraded());
        }

        List<HouseVO> houseVos = enrichPublisherNamesSafely(currentPageItems.stream()
                .map(item -> convertRankedItemToVo(item, recallEvidenceMap.get(item.house().getId())))
                .collect(Collectors.toList()));

        HouseSearchResultVO result = new HouseSearchResultVO();
        result.setTotal(rankResult.total());
        result.setHouses(houseVos);
        result.setEsDown(recallResult.degraded());
        result.setFallbackSource(recallResult.degraded() ? "KEYWORD_SEARCH_DEGRADED" : "KEYWORD_SEARCH");
        result.setTipMessage(currentPageItems.isEmpty() ? EMPTY_RESULT_TIP : null);
        return result;
    }

    private HouseSearchResultVO buildCityHotFallbackResult(String city, int page, int size, boolean degraded) {
        List<HouseVO> hotHouses = List.of();
        try {
            if (!houseHotService.hasHotRankingCache(city)) {
                houseHotService.rebuildHotRanking(city);
            }
            hotHouses = houseHotService.queryHotHouses(city, page - 1, size);
        } catch (Exception ex) {
            log.warn("keyword search empty fallback to city hot houses failed, city={}, page={}, size={}",
                    city, page, size, ex);
        }

        HouseSearchResultVO result = new HouseSearchResultVO();
        result.setTotal((long) hotHouses.size());
        result.setHouses(enrichPublisherNamesSafely(hotHouses));
        result.setEsDown(degraded);
        result.setFallbackSource("REDIS_HOT");
        result.setTipMessage(EMPTY_SEARCH_HOT_TIP);
        return result;
    }

    private HouseVO convertRankedItemToVo(HouseRankedItem rankedItem, HouseRecallEvidence recallEvidence) {
        HouseVO vo = convertHouseToVo(rankedItem.house());
        List<HouseReasonCode> outwardReasonCodes = rankedItem.reasonCodes().stream()
                .filter(this::isKeywordOutwardReasonCode)
                .collect(Collectors.toList());
        vo.setSearchReasonCodes(outwardReasonCodes.stream()
                .map(Enum::name)
                .collect(Collectors.toList()));
        vo.setSearchReasons(buildKeywordSearchReasons(rankedItem, recallEvidence));
        return vo;
    }

    private List<String> buildKeywordSearchReasons(HouseRankedItem rankedItem, HouseRecallEvidence recallEvidence) {
        List<HouseReasonCode> reasonCodes = rankedItem.reasonCodes();
        boolean textMatched = reasonCodes.contains(HouseReasonCode.RECALL_TEXT_MATCH);
        boolean locationMatched = reasonCodes.contains(HouseReasonCode.RECALL_LOCATION_MATCH);

        List<String> reasons = new ArrayList<>();
        if (textMatched && locationMatched) {
            reasons.add(REASON_BOTH_TEXT_AND_LOCATION);
        } else if (textMatched) {
            reasons.add(REASON_TEXT_MATCH);
        } else if (locationMatched) {
            reasons.add(REASON_LOCATION_MATCH);
        }

        if (reasonCodes.contains(HouseReasonCode.LOCATION_DISTANCE_ADVANTAGE)) {
            Double distanceMeters = recallEvidence == null ? null : recallEvidence.locationDistanceMeters();
            if (distanceMeters != null) {
                reasons.add(REASON_DISTANCE_PREFIX + formatDistanceKm(distanceMeters));
            }
        }
        return reasons.stream()
                .limit(MAX_OUTWARD_REASON_COUNT)
                .collect(Collectors.toList());
    }

    private String formatDistanceKm(double distanceMeters) {
        return BigDecimal.valueOf(distanceMeters)
                .divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP)
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "km";
    }

    private boolean isKeywordOutwardReasonCode(HouseReasonCode reasonCode) {
        return reasonCode == HouseReasonCode.RECALL_LOCATION_MATCH
                || reasonCode == HouseReasonCode.RECALL_TEXT_MATCH
                || reasonCode == HouseReasonCode.LOCATION_DISTANCE_ADVANTAGE;
    }

    private Map<Long, HouseRecallEvidence> buildRecallEvidenceMap(List<HouseRecallCandidate> candidates) {
        return (candidates == null ? List.<HouseRecallCandidate>of() : candidates).stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.house() != null)
                .filter(candidate -> candidate.house().getId() != null)
                .collect(Collectors.toMap(
                        candidate -> candidate.house().getId(),
                        HouseRecallCandidate::recallEvidence,
                        (left, right) -> left
                ));
    }

    private List<HouseVO> enrichPublisherNamesSafely(List<HouseVO> houses) {
        if (houses == null || houses.isEmpty()) {
            return houses;
        }
        try {
            return enrichPublisherNames(houses);
        } catch (Exception e) {
            log.error("batch enrich publisher names failed, houseCount={}", houses.size(), e);
            fillUnknownPublisherNames(houses);
            return houses;
        }
    }

    private List<HouseVO> enrichPublisherNames(List<HouseVO> houses) {
        if (houses.isEmpty()) {
            return houses;
        }

        List<Long> publisherIds = houses.stream()
                .map(HouseVO::getPublisherUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (publisherIds.isEmpty()) {
            return houses;
        }

        Map<Long, String> userNameMap = userService.listByIds(publisherIds).stream()
                .filter(Objects::nonNull)
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(
                        User::getId,
                        user -> StringUtils.hasText(user.getName()) ? user.getName() : UNKNOWN_PUBLISHER_NAME,
                        (left, right) -> left
                ));

        houses.forEach(house -> house.setPublisherName(
                userNameMap.getOrDefault(house.getPublisherUserId(), UNKNOWN_PUBLISHER_NAME)
        ));
        return houses;
    }

    private void fillUnknownPublisherNames(List<HouseVO> houses) {
        for (HouseVO house : houses) {
            if (house != null && !StringUtils.hasText(house.getPublisherName())) {
                house.setPublisherName(UNKNOWN_PUBLISHER_NAME);
            }
        }
    }

    private HouseVO convertHouseToVo(House house) {
        HouseVO vo = new HouseVO();
        vo.setId(house.getId());
        vo.setPublisherUserId(house.getPublisherUserId());
        vo.setTitle(house.getTitle());
        vo.setCity(house.getCity());
        vo.setRegion(house.getRegion());
        vo.setNearSubway(house.getNearSubway() != null && house.getNearSubway() == 1);
        vo.setPrivateBathroom(house.getPrivateBathroom() != null && house.getPrivateBathroom() == 1);
        vo.setHasBalcony(house.getHasBalcony() != null && house.getHasBalcony() == 1);
        vo.setCivilWaterElectric(house.getCivilWaterElectric() != null && house.getCivilWaterElectric() == 1);
        vo.setSupportStudentDepositFree(house.getSupportStudentDepositFree() != null
                && house.getSupportStudentDepositFree() == 1);
        vo.setStatus(house.getStatus());
        if (house.getPrice() != null) {
            vo.setPrice(BigDecimal.valueOf(house.getPrice()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        if (house.getDepositAmount() != null) {
            vo.setDepositAmount(BigDecimal.valueOf(house.getDepositAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return vo;
    }
}

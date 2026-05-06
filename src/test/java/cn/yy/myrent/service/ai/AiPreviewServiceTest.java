package cn.yy.myrent.service.ai;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateBundle;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateCollector;
import cn.yy.myrent.service.smartguide.SmartGuideCandidateQuery;
import cn.yy.myrent.vo.AiPreviewGroupVO;
import cn.yy.myrent.vo.AiPreviewVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPreviewServiceTest {

    @Mock
    private SmartGuideCandidateCollector candidateCollector;

    @InjectMocks
    private AiPreviewServiceImpl previewService;

    @Test
    void buildPreviewShouldCreateFactualDirectionGroupsFromRealCandidates() {
        when(candidateCollector.collect(any(SmartGuideCandidateQuery.class))).thenReturn(new SmartGuideCandidateBundle(
                "豫园",
                31.227,
                121.492,
                false,
                List.of(
                        new House().setId(101L).setTitle("近地铁整租").setPrice(420000).setTotalCost(520000).setRentType(1).setNearSubway(1).setHasBalcony(1),
                        new House().setId(102L).setTitle("预算友好合租").setPrice(260000).setTotalCost(300000).setRentType(2).setNearSubway(0).setPrivateBathroom(1),
                        new House().setId(103L).setTitle("学生免押合租").setPrice(240000).setTotalCost(240000).setRentType(2).setSupportStudentDepositFree(1)
                )
        ));

        AiPreviewVO preview = previewService.build("豫园", null, "RENT_ONLY", null);

        assertEquals("豫园", preview.getLocationName());
        assertTrue(preview.getGroups().size() >= 2);
        assertTrue(preview.getGroups().stream().map(AiPreviewGroupVO::getGroupKey).toList().contains("near_metro"));
        assertTrue(preview.getGroups().stream().map(AiPreviewGroupVO::getGroupKey).toList().contains("lower_total_cost"));
    }

    @Test
    void buildPreviewShouldOnlyUseSupportedPreviewClaims() {
        when(candidateCollector.collect(any(SmartGuideCandidateQuery.class))).thenReturn(new SmartGuideCandidateBundle(
                "豫园",
                31.227,
                121.492,
                false,
                List.of(
                        new House().setId(201L).setTitle("地铁口整租").setPrice(430000).setTotalCost(530000).setRentType(1).setNearSubway(1),
                        new House().setId(202L).setTitle("低总成本合租").setPrice(230000).setTotalCost(260000).setRentType(2)
                )
        ));

        AiPreviewVO preview = previewService.build("豫园", null, "RENT_ONLY", null);

        String mergedSummary = preview.getGroups().stream()
                .map(AiPreviewGroupVO::getSummary)
                .reduce("", String::concat);

        assertTrue(!mergedSummary.contains("安静"));
        assertTrue(!mergedSummary.contains("面积更大"));
        assertTrue(!mergedSummary.contains("采光"));
    }
}

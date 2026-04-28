package cn.yy.myrent.service.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendSessionState {

    private Long userId;

    private String sessionId;

    private AiRecommendSlots slots;

    @Builder.Default
    private List<AiRecommendTurn> history = new ArrayList<>();

    public static AiRecommendSessionState empty(Long userId) {
        return AiRecommendSessionState.builder()
                .userId(userId)
                .sessionId(buildSessionId(userId))
                .slots(AiRecommendSlots.builder()
                        .preferences(new ArrayList<>())
                        .build())
                .history(new ArrayList<>())
                .build();
    }

    public static String buildSessionId(Long userId) {
        return "ai-u" + userId;
    }
}

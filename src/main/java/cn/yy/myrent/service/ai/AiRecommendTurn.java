package cn.yy.myrent.service.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendTurn {

    private String role;

    private String content;

    public static AiRecommendTurn user(String content) {
        return new AiRecommendTurn("user", content);
    }

    public static AiRecommendTurn assistant(String content) {
        return new AiRecommendTurn("assistant", content);
    }
}

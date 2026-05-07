package cn.yy.myrent.service.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWeightedPreference {

    private String preferenceKey;

    private AiPreferenceWeightLevel weightLevel;

    private boolean relaxable;
}

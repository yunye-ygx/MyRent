package cn.yy.myrent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class AiRecommendInteractionDTO {

    @NotBlank(message = "interaction.type cannot be blank")
    private String type;

    @NotBlank(message = "interaction.groupKey cannot be blank")
    private String groupKey;

    @NotBlank(message = "interaction.label cannot be blank")
    private String label;

    @Valid
    private AiRecommendInteractionSlotPatchDTO slotPatch;

    @AssertTrue(message = "interaction.type must be PREVIEW_SELECTION")
    public boolean hasSupportedType() {
        return !StringUtils.hasText(type) || "PREVIEW_SELECTION".equals(type.trim());
    }
}

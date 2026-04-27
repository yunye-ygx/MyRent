package cn.yy.myrent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentVerificationApplyReqDTO {

    @NotBlank(message = "school name cannot be blank")
    private String schoolName;

    @NotBlank(message = "student no cannot be blank")
    private String studentNo;

    @NotNull(message = "graduation date cannot be null")
    private LocalDate graduationDate;
}

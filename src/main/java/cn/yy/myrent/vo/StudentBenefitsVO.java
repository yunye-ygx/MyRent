package cn.yy.myrent.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class StudentBenefitsVO {

    private String status;

    private VerificationInfo verification;

    private List<String> benefits = new ArrayList<>();

    public static StudentBenefitsVO unverified() {
        StudentBenefitsVO vo = new StudentBenefitsVO();
        vo.setStatus("UNVERIFIED");
        return vo;
    }

    public static StudentBenefitsVO pending(VerificationInfo verification) {
        StudentBenefitsVO vo = new StudentBenefitsVO();
        vo.setStatus("PENDING");
        vo.setVerification(verification);
        return vo;
    }

    public static StudentBenefitsVO approved(VerificationInfo verification, List<String> benefits) {
        StudentBenefitsVO vo = new StudentBenefitsVO();
        vo.setStatus("APPROVED");
        vo.setVerification(verification);
        vo.setBenefits(new ArrayList<>(benefits));
        return vo;
    }

    public static StudentBenefitsVO rejected(VerificationInfo verification) {
        StudentBenefitsVO vo = new StudentBenefitsVO();
        vo.setStatus("REJECTED");
        vo.setVerification(verification);
        return vo;
    }

    @Data
    public static class VerificationInfo {

        private String schoolName;

        private String studentNo;

        private LocalDate graduationDate;

        private LocalDateTime applyTime;

        private LocalDateTime reviewTime;

        private String rejectReason;
    }
}

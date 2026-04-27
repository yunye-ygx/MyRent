package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("student_verification")
public class StudentVerification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private Integer status;

    @TableField("school_name")
    private String schoolName;

    @TableField("student_no")
    private String studentNo;

    @TableField("graduation_date")
    private LocalDate graduationDate;

    @TableField("apply_time")
    private LocalDateTime applyTime;

    @TableField("review_time")
    private LocalDateTime reviewTime;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

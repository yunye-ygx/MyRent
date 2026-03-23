package cn.yy.myrent.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 房源信息表
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("house")
public class House implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("publisher_user_id")
    private Long publisherUserId;

    /**
     * 房源标题(供ES全文搜索)
     */
    private String title;

    @TableField("rent_type")
    private Integer rentType;

    /**
     * 月租金(分)
     */
    private Integer price;

    /**
     * 锁定定金金额(分) - 【新增：锁房核心】
     */
    private Integer depositAmount;

    @TableField("total_cost")
    private Integer totalCost;

    /**
     * 经度 - 【核心：供ES做LBS附近搜索】
     */
    private BigDecimal longitude;

    /**
     * 纬度 - 【核心：供ES做LBS附近搜索】
     */
    private BigDecimal latitude;

    /**
     * 状态: 1-可租(上架), 2-已锁定(有人交定金)
     */
    private Integer status;

    /**
     * 乐观锁版本号 - 【核心：防一房多租】
     */
    private Integer version;

    private LocalDateTime createTime;


}

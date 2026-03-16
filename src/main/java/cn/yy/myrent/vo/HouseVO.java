package cn.yy.myrent.vo;




import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * C端首页附近房源卡片展示对象 (View Object)
 */
@Data

public class HouseVO {

    // -----------------------------------------------------------
    // 1. 基础信息区 (原样暴露)
    // -----------------------------------------------------------


    // 【架构师细节】：雪花算法生成的长整型ID传给前端JS会丢失精度，必须转成String给前端！
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long publisherUserId;


    private String title;

    // -----------------------------------------------------------
    // 2. 资金转换区 (屏蔽底层设计，转化为前端可读)
    // -----------------------------------------------------------


    // 【核心变化】：底层存的是Integer(分)，VO里必须转成前端直接展示的BigDecimal(元)
    private BigDecimal price;


    private BigDecimal depositAmount;

    // -----------------------------------------------------------
    // 3. LBS 空间计算区 (后端计算结果，代替原始经纬度)
    // -----------------------------------------------------------


    // 【核心变化】：隐藏了纬度和经度，直接给前端计算好的距离字符串
    private String distance;

    // -----------------------------------------------------------
    // 4. 业务状态区 (用于前端UI交互控制)
    // -----------------------------------------------------------

    private Integer status;



    // -----------------------------------------------------------
    // 5. 【进阶扩展】宽索引应该有的额外字段 (你的数据库主表没有，但UI需要)
    // -----------------------------------------------------------


}

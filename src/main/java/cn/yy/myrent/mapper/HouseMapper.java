package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.House;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jakarta.validation.constraints.NotNull;

/**
 * <p>
 * 房源信息表 Mapper 接口
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
public interface HouseMapper extends BaseMapper<House> {

    House selectForUpdateById( Long id);
}

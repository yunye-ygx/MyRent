package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IHouseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 房源信息表 服务实现类
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
@Service
public class HouseServiceImpl extends ServiceImpl<HouseMapper, House> implements IHouseService {

}

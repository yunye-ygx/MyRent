package cn.yy.myrent.service;

import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.vo.HouseVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 房源信息表 服务类
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
public interface IHouseService extends IService<House> {


    List<HouseVO> searchNearbyHouse(SearchHouseReqDTO reqDTO);
}

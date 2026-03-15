package cn.yy.myrent.service;

import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.vo.HouseSearchResultVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 房源信息表 服务类
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
public interface IHouseService extends IService<House> {


    HouseSearchResultVO searchNearbyHouse(SearchHouseReqDTO reqDTO);

    boolean createHouseWithSync(House house);

    boolean updateHouseWithSync(Long id, House house);

    boolean deleteHouseWithSync(Long id);

    boolean updateHouseStatusWithSync(Long houseId, Integer expectedStatus, Integer targetStatus, String reason);
}

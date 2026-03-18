package cn.yy.myrent.service;

import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 房源查询服务接口。
 */
public interface IHouseService extends IService<House> {

    HouseSearchResultVO searchNearbyHouse(SearchHouseReqDTO reqDTO);

    /**
     * 根据预算、租住方式和通勤偏好返回排序后的房源推荐结果。
     */
    SmartGuideResultVO smartGuide(SmartGuideReqDTO reqDTO);
}

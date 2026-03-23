package cn.yy.myrent.service;

import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IHouseService extends IService<House> {

    HouseSearchResultVO searchNearbyHouse(SearchHouseReqDTO reqDTO);

    HouseSearchResultVO hotHouses(Integer page, Integer size);

    SmartGuideResultVO smartGuide(SmartGuideReqDTO reqDTO);
}

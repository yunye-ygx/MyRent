package cn.yy.myrent.service;

import cn.yy.myrent.entity.HouseFavorite;
import cn.yy.myrent.vo.HouseFavoriteStatusVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IHouseFavoriteService extends IService<HouseFavorite> {

    HouseFavoriteStatusVO favorite(Long houseId, Long userId);

    HouseFavoriteStatusVO unfavorite(Long houseId, Long userId);

    HouseFavoriteStatusVO getFavoriteStatus(Long houseId, Long userId);
}

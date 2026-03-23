package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.HouseFavorite;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IHouseFavoriteService;
import cn.yy.myrent.vo.HouseFavoriteStatusVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HouseFavoriteServiceImpl extends ServiceImpl<HouseFavoriteMapper, HouseFavorite> implements IHouseFavoriteService {

    private static final int FAVORITE_STATUS_ACTIVE = 1;

    private final HouseMapper houseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HouseFavoriteStatusVO favorite(Long houseId, Long userId) {
        House house = requireHouse(houseId);
        if (house.getStatus() == null || house.getStatus() != 1) {
            throw new IllegalStateException("当前房源不可收藏");
        }

        LocalDateTime now = LocalDateTime.now();
        HouseFavorite existing = this.lambdaQuery()
                .eq(HouseFavorite::getUserId, userId)
                .eq(HouseFavorite::getHouseId, houseId)
                .one();

        if (existing == null) {
            HouseFavorite favorite = new HouseFavorite()
                    .setUserId(userId)
                    .setHouseId(houseId)
                    .setStatus(FAVORITE_STATUS_ACTIVE)
                    .setFavoriteTime(now)
                    .setCancelTime(null)
                    .setCreateTime(now)
                    .setUpdateTime(now);
            this.save(favorite);
        } else if (existing.getStatus() == null || existing.getStatus() != FAVORITE_STATUS_ACTIVE) {
            existing.setStatus(FAVORITE_STATUS_ACTIVE);
            existing.setFavoriteTime(now);
            existing.setCancelTime(null);
            this.updateById(existing);
        }

        return buildStatus(houseId, userId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HouseFavoriteStatusVO unfavorite(Long houseId, Long userId) {
        requireHouse(houseId);
        HouseFavorite existing = this.lambdaQuery()
                .eq(HouseFavorite::getUserId, userId)
                .eq(HouseFavorite::getHouseId, houseId)
                .one();
        if (existing != null && existing.getStatus() != null && existing.getStatus() == FAVORITE_STATUS_ACTIVE) {
            existing.setStatus(0);
            existing.setCancelTime(LocalDateTime.now());
            this.updateById(existing);
        }
        return buildStatus(houseId, userId, false);
    }

    @Override
    public HouseFavoriteStatusVO getFavoriteStatus(Long houseId, Long userId) {
        requireHouse(houseId);
        HouseFavorite relation = null;
        if (userId != null) {
            relation = this.lambdaQuery()
                    .eq(HouseFavorite::getUserId, userId)
                    .eq(HouseFavorite::getHouseId, houseId)
                    .one();
        }

        long favoriteCount = this.lambdaQuery()
                .eq(HouseFavorite::getHouseId, houseId)
                .eq(HouseFavorite::getStatus, FAVORITE_STATUS_ACTIVE)
                .count();

        HouseFavoriteStatusVO statusVO = new HouseFavoriteStatusVO();
        statusVO.setHouseId(houseId);
        statusVO.setFavorited(relation != null
                && relation.getStatus() != null
                && relation.getStatus() == FAVORITE_STATUS_ACTIVE);
        statusVO.setFavoriteCount(favoriteCount);
        return statusVO;
    }

    private HouseFavoriteStatusVO buildStatus(Long houseId, Long userId, boolean defaultFavorited) {
        HouseFavoriteStatusVO statusVO = getFavoriteStatus(houseId, userId);
        if (statusVO.getFavorited() == null) {
            statusVO.setFavorited(defaultFavorited);
        }
        return statusVO;
    }

    private House requireHouse(Long houseId) {
        House house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new IllegalArgumentException("房源不存在");
        }
        return house;
    }
}

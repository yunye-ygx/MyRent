package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.House;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 房源信息表 Mapper 接口
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
public interface HouseMapper extends BaseMapper<House> {

    House selectForUpdateById(Long id);

    List<House> selectListFilterPage(@Param("city") String city,
                                     @Param("region") String region,
                                     @Param("rentType") Integer rentType,
                                     @Param("minPriceCent") Integer minPriceCent,
                                     @Param("maxPriceCent") Integer maxPriceCent,
                                     @Param("nearSubway") Boolean nearSubway,
                                     @Param("privateBathroom") Boolean privateBathroom,
                                     @Param("hasBalcony") Boolean hasBalcony,
                                     @Param("civilWaterElectric") Boolean civilWaterElectric,
                                     @Param("supportStudentDepositFree") Boolean supportStudentDepositFree,
                                     @Param("offset") Integer offset,
                                     @Param("size") Integer size);

    List<Long> selectSmartGuideCandidateIds(@Param("candidateIds") List<Long> candidateIds,
                                            @Param("availableStatus") Integer availableStatus,
                                            @Param("rentType") Integer rentType,
                                            @Param("totalCostScope") boolean totalCostScope,
                                            @Param("maxComparableCostCent") Integer maxComparableCostCent,
                                            @Param("targetLatitude") double targetLatitude,
                                            @Param("targetLongitude") double targetLongitude,
                                            @Param("minLatitude") double minLatitude,
                                            @Param("maxLatitude") double maxLatitude,
                                            @Param("minLongitude") double minLongitude,
                                            @Param("maxLongitude") double maxLongitude,
                                            @Param("radiusKm") double radiusKm,
                                            @Param("limit") Integer limit);
}

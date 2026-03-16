package cn.yy.myrent.sync.house.classifier;

import cn.yy.myrent.entity.House;

public interface HouseChangeClassifier {

    /**
     * 对比变更前后房源，输出字段变更分类结果。
     */
    HouseChangeClassificationResult classify(Long houseId, House oldHouse, House newHouse);
}


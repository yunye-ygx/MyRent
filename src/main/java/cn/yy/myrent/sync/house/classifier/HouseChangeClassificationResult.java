package cn.yy.myrent.sync.house.classifier;

import cn.yy.myrent.entity.House;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HouseChangeClassificationResult {

    /**
     * 用于 updateById 的补丁对象，只包含发生变化的字段。
     */
    private House updatePatch;

    /**
     * 是否存在任意字段变化。
     */
    private boolean changed;

    /**
     * 是否包含核心字段变化。
     */
    private boolean coreChanged;

    /**
     * 发生变化的字段名列表，便于日志排查。
     */
    private List<String> changedFields = new ArrayList<>();
}


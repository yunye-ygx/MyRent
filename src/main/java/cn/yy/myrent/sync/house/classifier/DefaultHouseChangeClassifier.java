package cn.yy.myrent.sync.house.classifier;

import cn.yy.myrent.entity.House;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DefaultHouseChangeClassifier implements HouseChangeClassifier {

    @Override
    public HouseChangeClassificationResult classify(Long houseId, House oldHouse, House newHouse) {
        HouseChangeClassificationResult result = new HouseChangeClassificationResult();
        House patch = new House();
        patch.setId(houseId);

        if (newHouse == null || oldHouse == null || houseId == null) {
            result.setUpdatePatch(patch);
            result.setChanged(false);
            result.setCoreChanged(false);
            return result;
        }

        applyTitleChange(oldHouse, newHouse, patch, result);
        applyRentTypeChange(oldHouse, newHouse, patch, result);
        applyPriceChange(oldHouse, newHouse, patch, result);
        applyDepositAmountChange(oldHouse, newHouse, patch, result);
        applyLatitudeChange(oldHouse, newHouse, patch, result);
        applyLongitudeChange(oldHouse, newHouse, patch, result);
        applyStatusChange(oldHouse, newHouse, patch, result);

        result.setChanged(!result.getChangedFields().isEmpty());
        result.setUpdatePatch(patch);
        return result;
    }

    private void applyTitleChange(House oldHouse,
                                  House newHouse,
                                  House patch,
                                  HouseChangeClassificationResult result) {
        if (newHouse.getTitle() == null || Objects.equals(newHouse.getTitle(), oldHouse.getTitle())) {
            return;
        }
        patch.setTitle(newHouse.getTitle());
        result.getChangedFields().add("title");
    }

    private void applyPriceChange(House oldHouse,
                                  House newHouse,
                                  House patch,
                                  HouseChangeClassificationResult result) {
        if (newHouse.getPrice() == null || Objects.equals(newHouse.getPrice(), oldHouse.getPrice())) {
            return;
        }
        patch.setPrice(newHouse.getPrice());
        result.getChangedFields().add("price");
        result.setCoreChanged(true);
    }

    private void applyRentTypeChange(House oldHouse,
                                     House newHouse,
                                     House patch,
                                     HouseChangeClassificationResult result) {
        if (newHouse.getRentType() == null || Objects.equals(newHouse.getRentType(), oldHouse.getRentType())) {
            return;
        }
        patch.setRentType(newHouse.getRentType());
        result.getChangedFields().add("rentType");
        result.setCoreChanged(true);
    }

    private void applyDepositAmountChange(House oldHouse,
                                          House newHouse,
                                          House patch,
                                          HouseChangeClassificationResult result) {
        if (newHouse.getDepositAmount() == null || Objects.equals(newHouse.getDepositAmount(), oldHouse.getDepositAmount())) {
            return;
        }
        patch.setDepositAmount(newHouse.getDepositAmount());
        result.getChangedFields().add("depositAmount");
        result.setCoreChanged(true);
    }

    private void applyLatitudeChange(House oldHouse,
                                     House newHouse,
                                     House patch,
                                     HouseChangeClassificationResult result) {
        if (newHouse.getLatitude() == null || Objects.equals(newHouse.getLatitude(), oldHouse.getLatitude())) {
            return;
        }
        patch.setLatitude(newHouse.getLatitude());
        result.getChangedFields().add("latitude");
        result.setCoreChanged(true);
    }

    private void applyLongitudeChange(House oldHouse,
                                      House newHouse,
                                      House patch,
                                      HouseChangeClassificationResult result) {
        if (newHouse.getLongitude() == null || Objects.equals(newHouse.getLongitude(), oldHouse.getLongitude())) {
            return;
        }
        patch.setLongitude(newHouse.getLongitude());
        result.getChangedFields().add("longitude");
        result.setCoreChanged(true);
    }

    private void applyStatusChange(House oldHouse,
                                   House newHouse,
                                   House patch,
                                   HouseChangeClassificationResult result) {
        if (newHouse.getStatus() == null || Objects.equals(newHouse.getStatus(), oldHouse.getStatus())) {
            return;
        }
        patch.setStatus(newHouse.getStatus());
        result.getChangedFields().add("status");
        result.setCoreChanged(true);
    }
}


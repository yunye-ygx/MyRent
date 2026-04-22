package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.INotificationService;
import cn.yy.myrent.sync.house.HouseSyncDispatcher;
import cn.yy.myrent.sync.house.classifier.HouseChangeClassificationResult;
import cn.yy.myrent.sync.house.classifier.HouseChangeClassifier;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseCommandServiceImplTest {

    @Mock
    private HouseSyncDispatcher houseSyncDispatcher;

    @Mock
    private HouseChangeClassifier houseChangeClassifier;

    @Mock
    private INotificationService notificationService;

    @InjectMocks
    private HouseCommandServiceImpl houseCommandService;

    @Test
    void createHouseShouldDispatchNewHouseNotification() {
        House house = new House().setId(8L).setPublisherUserId(9L).setTitle("New listing").setPrice(4300).setStatus(1);
        doNothing().when(notificationService).notifyHouseCreated(any(House.class));

        HouseCommandServiceImpl spy = org.mockito.Mockito.spy(houseCommandService);
        doReturn(true).when(spy).save(any(House.class));

        assertTrue(spy.createHouseWithSync(house));

        verify(notificationService).notifyHouseCreated(house);
    }

    @Test
    void updateHouseShouldDispatchUpdatedHouseNotification() {
        House oldHouse = new House().setId(7L).setTitle("Old title").setPrice(5200).setStatus(1);
        House newHouse = new House().setId(7L).setTitle("Old title").setPrice(5000).setStatus(1).setVersion(2);
        House updatedHouse = new House().setId(7L).setTitle("Old title").setPrice(5000).setStatus(1).setVersion(2);

        HouseChangeClassificationResult result = new HouseChangeClassificationResult();
        result.setChanged(true);
        result.setCoreChanged(true);
        result.setUpdatePatch(new House().setId(7L).setPrice(5000).setVersion(2));

        HouseCommandServiceImpl spy = org.mockito.Mockito.spy(houseCommandService);
        doReturn(oldHouse).doReturn(updatedHouse).when(spy).getById(7L);
        doReturn(true).when(spy).updateById(any(House.class));
        doNothing().when(notificationService).notifyHouseUpdated(any(House.class), any(House.class));
        when(houseChangeClassifier.classify(7L, oldHouse, newHouse)).thenReturn(result);

        assertTrue(spy.updateHouseWithSync(7L, newHouse));

        verify(notificationService).notifyHouseUpdated(oldHouse, updatedHouse);
    }

    @Test
    void deleteHouseShouldDispatchDeletedHouseNotification() {
        House oldHouse = new House().setId(7L).setTitle("Old title").setPrice(5200).setStatus(1);

        HouseCommandServiceImpl spy = org.mockito.Mockito.spy(houseCommandService);
        doReturn(oldHouse).when(spy).getById(7L);
        doReturn(true).when(spy).removeById(7L);
        doNothing().when(notificationService).notifyHouseDeleted(any(House.class));

        assertTrue(spy.deleteHouseWithSync(7L));

        verify(notificationService).notifyHouseDeleted(oldHouse);
    }

    @Test
    void updateHouseStatusShouldDispatchUpdatedHouseNotification() {
        House oldHouse = new House().setId(7L).setTitle("Old title").setPrice(5200).setStatus(1).setVersion(2);
        House newHouse = new House().setId(7L).setTitle("Old title").setPrice(5200).setStatus(2).setVersion(3);
        @SuppressWarnings("unchecked")
        LambdaUpdateChainWrapper<House> updateChain = mock(LambdaUpdateChainWrapper.class);

        HouseCommandServiceImpl spy = org.mockito.Mockito.spy(houseCommandService);
        doReturn(oldHouse).doReturn(newHouse).when(spy).getById(7L);
        doReturn(updateChain).when(spy).lambdaUpdate();
        doNothing().when(notificationService).notifyHouseUpdated(any(House.class), any(House.class));

        when(updateChain.eq(any(), eq(7L))).thenReturn(updateChain);
        when(updateChain.eq(eq(true), any(), eq(1))).thenReturn(updateChain);
        when(updateChain.set(any(), eq(2))).thenReturn(updateChain);
        when(updateChain.setSql(any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);

        assertTrue(spy.updateHouseStatusWithSync(7L, 1, 2, "order-paid-lock"));

        verify(notificationService).notifyHouseUpdated(oldHouse, newHouse);
    }
}

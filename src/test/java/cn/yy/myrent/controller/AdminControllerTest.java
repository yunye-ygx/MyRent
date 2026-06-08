package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.service.IPaymentService;
import cn.yy.myrent.service.IUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private IUserService userService;

    @Mock
    private IHouseService houseService;

    @Mock
    private IHouseCommandService houseCommandService;

    @Mock
    private IOrderService orderService;

    @Mock
    private IPaymentService paymentService;

    @InjectMocks
    private AdminController adminController;

    @Test
    void createHouseShouldUseCommandServiceForSync() {
        House house = new House()
                .setId(99L)
                .setTitle("Admin created house")
                .setPublisherUserId(9L);
        AtomicReference<House> submittedRef = new AtomicReference<>();
        when(houseCommandService.createHouseWithSync(any(House.class))).thenAnswer(invocation -> {
            House saved = invocation.getArgument(0);
            submittedRef.set(new House()
                    .setId(saved.getId())
                    .setStatus(saved.getStatus())
                    .setVersion(saved.getVersion())
                    .setAuditStatus(saved.getAuditStatus())
                    .setCreateTime(saved.getCreateTime())
                    .setTotalCost(saved.getTotalCost()));
            saved.setId(1001L);
            return true;
        });

        Result<Long> result = adminController.createHouse(house);

        ArgumentCaptor<House> captor = ArgumentCaptor.forClass(House.class);
        verify(houseCommandService).createHouseWithSync(captor.capture());
        verify(houseService, never()).save(any(House.class));
        House savedHouse = submittedRef.get();
        assertEquals(null, savedHouse.getId());
        assertEquals(1, savedHouse.getStatus());
        assertEquals(0, savedHouse.getVersion());
        assertEquals(1, savedHouse.getAuditStatus());
        assertNotNull(savedHouse.getCreateTime());
        assertEquals(null, savedHouse.getTotalCost());
        assertEquals(200, result.getCode());
        assertEquals(1001L, result.getData());
    }

    @Test
    void updateHouseShouldUseCommandServiceForSync() {
        House house = new House()
                .setTitle("Updated by admin")
                .setStatus(2)
                .setVersion(5);
        when(houseCommandService.updateHouseWithSync(any(Long.class), any(House.class))).thenReturn(true);

        Result<Void> result = adminController.updateHouse(7L, house);

        ArgumentCaptor<House> captor = ArgumentCaptor.forClass(House.class);
        verify(houseCommandService).updateHouseWithSync(org.mockito.ArgumentMatchers.eq(7L), captor.capture());
        verify(houseService, never()).updateById(any(House.class));
        House updatedHouse = captor.getValue();
        assertEquals(7L, updatedHouse.getId());
        assertEquals(null, updatedHouse.getStatus());
        assertEquals(null, updatedHouse.getVersion());
        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
    }

    @Test
    void deleteHouseShouldUseCommandServiceForSync() {
        when(houseCommandService.deleteHouseWithSync(7L)).thenReturn(true);

        Result<Void> result = adminController.deleteHouse(7L);

        verify(houseCommandService).deleteHouseWithSync(7L);
        verify(houseService, never()).removeById(7L);
        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
    }
}

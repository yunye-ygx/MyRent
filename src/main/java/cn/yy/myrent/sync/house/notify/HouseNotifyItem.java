package cn.yy.myrent.sync.house.notify;

public record HouseNotifyItem(
        Long userId,
        String type,
        String title,
        String content,
        String bizKey,
        Long targetId,
        String extraJson
) {
}

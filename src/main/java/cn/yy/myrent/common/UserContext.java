package cn.yy.myrent.common;

public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setCurrentUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getCurrentUserId() {
        return USER_ID_HOLDER.get();
    }

    public static Long requireCurrentUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) {
            throw new IllegalStateException("未登录或用户上下文缺失");
        }
        return userId;
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}

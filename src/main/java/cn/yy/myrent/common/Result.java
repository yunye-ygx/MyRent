package cn.yy.myrent.common;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    // 响应状态码：200=成功，400=参数错误，500=服务器错误等
    private Integer code;

    // 响应消息
    private String message;

    // 响应数据
    private T data;

    // 私有构造器，防止外部 new
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ========== 成功响应 ==========

    /** 无数据的成功响应 */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /** 带数据的成功响应 */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /** 自定义消息的成功响应 */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    // ========== 失败响应 ==========

    /** 通用失败（默认500） */
    public static <T> Result<T> error() {
        return new Result<>(500, "操作失败", null);
    }

    /** 自定义错误消息 */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    /** 自定义错误码和消息 */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
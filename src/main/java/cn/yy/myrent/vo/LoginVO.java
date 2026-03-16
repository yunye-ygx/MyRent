package cn.yy.myrent.vo;

import lombok.Data;

@Data
public class LoginVO {

    private String token;

    private Long userId;

    private String phone;

    private String name;
}

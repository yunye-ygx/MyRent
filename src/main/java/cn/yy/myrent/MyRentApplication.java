package cn.yy.myrent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.yy.myrent.mapper")
public class MyRentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyRentApplication.class, args);
    }

}

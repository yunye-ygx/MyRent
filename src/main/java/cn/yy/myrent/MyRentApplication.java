package cn.yy.myrent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("cn.yy.myrent.mapper")
@EnableScheduling
public class MyRentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyRentApplication.class, args);
    }

}

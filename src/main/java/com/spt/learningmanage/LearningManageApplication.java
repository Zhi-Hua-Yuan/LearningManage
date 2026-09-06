package com.spt.learningmanage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.spt.learningmanage.mapper")
public class LearningManageApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningManageApplication.class, args);
    }

}

package com.spt.learningmanage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.spt.learningmanage.mapper")
@EnableScheduling
public class LearningManageApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningManageApplication.class, args);
    }

}

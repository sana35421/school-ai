package com.schoolai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 校园智能问答助手启动类。
 */
@SpringBootApplication
@EnableMethodSecurity
@MapperScan("com.schoolai.mapper")
public class SchoolAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolAiApplication.class, args);
    }
}
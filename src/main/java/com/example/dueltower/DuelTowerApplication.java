package com.example.dueltower;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class DuelTowerApplication {
	public static void main(String[] args) {
		SpringApplication.run(DuelTowerApplication.class, args);
        log.info("듀얼 타워 실행됨");
	}
}

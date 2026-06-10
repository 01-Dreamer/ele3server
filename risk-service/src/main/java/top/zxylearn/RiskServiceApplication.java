package top.zxylearn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@EnableRabbit
@SpringBootApplication
public class RiskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskServiceApplication.class, args);
    }
}

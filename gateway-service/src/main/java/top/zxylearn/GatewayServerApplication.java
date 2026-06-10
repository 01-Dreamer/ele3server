package top.zxylearn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
		basePackages = "top.zxylearn",
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.REGEX,
				pattern = "top\\.zxylearn\\.(config|interceptor)\\..*"
		)
)
public class GatewayServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayServerApplication.class, args);
	}

}

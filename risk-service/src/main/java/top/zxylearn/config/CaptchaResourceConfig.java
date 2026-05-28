package top.zxylearn.config;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaptchaResourceConfig {

    private static final int LOCAL_SLIDER_IMAGE_COUNT = 10;

    @Bean
    public ResourceStore captchaResourceStore() {
        LocalMemoryResourceStore resourceStore = new LocalMemoryResourceStore();
        for (int i = 1; i <= LOCAL_SLIDER_IMAGE_COUNT; i++) {
            resourceStore.addResource(
                    CaptchaTypeConstant.SLIDER,
                    new Resource("classpath", "captcha/slider/" + i + ".png", "default")
            );
        }
        return resourceStore;
    }
}

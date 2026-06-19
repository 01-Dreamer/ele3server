package top.zxylearn.config;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SensitiveWordConfig {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordConfig.class);
    private static final String WORDS_FILE = "sensitive-words.txt";

    @Bean
    public SensitiveWordBs sensitiveWordBs() {
        List<String> words = loadWords();
        log.info("加载自定义敏感词库完成 count={}", words.size());
        return SensitiveWordBs.newInstance()
                .wordDeny(() -> words)
                .init();
    }

    private List<String> loadWords() {
        ClassPathResource resource = new ClassPathResource(WORDS_FILE);
        if (!resource.exists()) {
            throw new IllegalStateException("敏感词库文件不存在: " + WORDS_FILE);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .distinct()
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("敏感词库文件读取失败: " + WORDS_FILE, ex);
        }
    }
}

package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.zxylearn.dto.AgentChatRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.AgentChatService;
import top.zxylearn.service.PowService;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/agent")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final PowService powService;
    private final AgentChatService agentChatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiController(PowService powService, AgentChatService agentChatService) {
        this.powService = powService;
        this.agentChatService = agentChatService;
    }

    @Operation(summary = "获取WASM模块")
    @GetMapping("/wasm-module")
    public Result<Map<String, String>> getWasmModule() {
        try {
            String js = new String(new ClassPathResource("wasm/agent_pow.js").getInputStream().readAllBytes());
            String wasm = Base64.getEncoder().encodeToString(
                    new ClassPathResource("wasm/agent_pow.wasm").getInputStream().readAllBytes());
            return Result.success(Map.of("js", js, "wasm", wasm));
        } catch (IOException ex) {
            return Result.fail(500, "WASM模块加载失败");
        }
    }

    @Operation(summary = "获取PoW挑战")
    @GetMapping("/challenge")
    public Result<Map<String, Object>> getChallenge(@RequestHeader("X-User-Id") String userId) {
        try {
            return Result.success(powService.getChallenge(userId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "挑战获取失败");
        }
    }

    @Operation(summary = "与Agent对话")
    @PostMapping(value = "/chat", consumes = {"application/json", "text/plain", "*/*"})
    public Result<Map<String, String>> chat(@RequestHeader("X-User-Id") String userId,
                                             @RequestHeader(value = "X-Agent-Pow-Response", required = false) String powResponse,
                                             @RequestBody(required = false) String body) {
        try {
            if (powResponse == null || powResponse.isBlank()) {
                return Result.fail(400, "缺少 X-Agent-Pow-Response");
            }
            if (!powService.verifyPow(userId, powResponse, 3)) {
                return Result.fail(403, "PoW验证失败");
            }
            AgentChatRequest request;
            if (body != null && !body.isBlank()) {
                request = objectMapper.readValue(body, AgentChatRequest.class);
            } else {
                return Result.fail(400, "请求内容不能为空");
            }
            if (request.getContent() == null || request.getContent().isBlank()) {
                return Result.fail(400, "content 不能为空");
            }
            return Result.success(Map.of("reply", agentChatService.chat(userId, request)));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (IOException ex) {
            return Result.fail(400, "请求格式不正确");
        } catch (RuntimeException ex) {
            log.error("Agent对话失败", ex);
            return Result.fail(500, "Agent对话失败");
        }
    }
}

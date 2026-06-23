package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.zxylearn.client.OrderToolClient;
import top.zxylearn.client.ShopToolClient;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.AgentChatRequest;
import top.zxylearn.dto.risk.RiskTextRecordCreateEventDTO;
import top.zxylearn.entity.AgentChat;
import top.zxylearn.mapper.AgentChatMapper;
import top.zxylearn.result.Result;
import top.zxylearn.vo.CursorPageVO;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AgentChatService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);

    private static final String ROLE_USER = "USER";
    private static final String ROLE_AGENT = "AGENT";
    private static final String ROLE_TOOL = "TOOL";
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final int HISTORY_SIZE = 30;
    private static final String HISTORY_KEY_PREFIX = "agent:chat:history:";
    private static final Duration HISTORY_TTL = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final PromptTemplate promptTemplate;
    private final AgentChatMapper agentChatMapper;
    private final ShopToolClient shopToolClient;
    private final OrderToolClient orderToolClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final PowService powService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AgentChatService(@Value("${openai.api-key}") String apiKey,
                            @Value("${openai.base-url}") String baseUrl,
                            @Value("${openai.model}") String model,
                            PromptTemplate promptTemplate,
                            AgentChatMapper agentChatMapper,
                            ShopToolClient shopToolClient,
                            OrderToolClient orderToolClient,
                            StringRedisTemplate stringRedisTemplate,
                            RabbitTemplate rabbitTemplate,
                            PowService powService) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.promptTemplate = promptTemplate;
        this.agentChatMapper = agentChatMapper;
        this.shopToolClient = shopToolClient;
        this.orderToolClient = orderToolClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.powService = powService;
        this.restClient = RestClient.builder().build();
    }

    // ==== 历史记录（缓存 + MySQL）====

    private List<Map<String, String>> loadHistory(Long userId) {
        String key = HISTORY_KEY_PREFIX + userId;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null && !cached.isBlank()) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception ignored) {}
        }
        List<AgentChat> records = agentChatMapper.selectList(
                new LambdaQueryWrapper<AgentChat>()
                        .eq(AgentChat::getUserId, userId)
                        .in(AgentChat::getRole, List.of("USER", "AGENT"))
                        .orderByDesc(AgentChat::getId)
                        .last("LIMIT " + HISTORY_SIZE));
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = records.size() - 1; i >= 0; i--) {
            AgentChat r = records.get(i);
            history.add(Map.of("role", r.getRole(), "content",
                    r.getContent() != null ? r.getContent() : ""));
        }
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(history), HISTORY_TTL);
        } catch (Exception ignored) {}
        return history;
    }

    private void evictHistoryCache(Long userId) {
        stringRedisTemplate.delete(HISTORY_KEY_PREFIX + userId);
    }

    // ==== 游标分页 ====

    public CursorPageVO<Map<String, String>> listChatHistory(String userId, String cursor, Integer size) {
        Long uid = parseUserId(userId);
        int pageSize = size != null && size > 0 ? Math.min(size, 100) : 20;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            try { cursorId = Long.valueOf(cursor.trim()); } catch (NumberFormatException ignored) {}
        }

        LambdaQueryWrapper<AgentChat> wrapper = new LambdaQueryWrapper<AgentChat>()
                .eq(AgentChat::getUserId, uid)
                .in(AgentChat::getRole, List.of("USER", "AGENT"))
                .orderByDesc(AgentChat::getId)
                .last("LIMIT " + (pageSize + 1));
        if (cursorId != null) {
            wrapper.lt(AgentChat::getId, cursorId);
        }

        List<AgentChat> list = agentChatMapper.selectList(wrapper);
        boolean hasMore = list.size() > pageSize;
        if (hasMore) list = list.subList(0, pageSize);

        List<Map<String, String>> items = list.stream()
                .map(r -> Map.of("id", String.valueOf(r.getId()),
                        "role", r.getRole(),
                        "content", r.getContent() != null ? r.getContent() : "",
                        "createTime", r.getCreateTime() != null ? r.getCreateTime().toString() : ""))
                .collect(Collectors.toList());

        String nextCursor = hasMore && !items.isEmpty()
                ? items.get(items.size() - 1).get("id") : null;
        return new CursorPageVO<>(items, nextCursor, hasMore);
    }

    @Async
    public void saveChatAsync(Long userId, String role, String content) {
        AgentChat record = new AgentChat();
        record.setUserId(userId);
        record.setRole(role);
        record.setContent(content);
        record.setCreateTime(LocalDateTime.now());
        agentChatMapper.insert(record);
    }

    @SuppressWarnings("unchecked")
    public SseEmitter chat(String userId, String nonce, AgentChatRequest request) {
        Long uid = parseUserId(userId);
        currentUserId = userId;
        SseEmitter emitter = new SseEmitter(120_000L);
        String finalNonce = nonce;

        emitter.onCompletion(() -> powService.consumeChallenge(userId, finalNonce));
        emitter.onTimeout(() -> powService.consumeChallenge(userId, finalNonce));
        emitter.onError(e -> powService.consumeChallenge(userId, finalNonce));

        CompletableFuture.runAsync(() -> {
            try {
                List<Map<String, Object>> messages = new ArrayList<>();

                // system prompt
                addMessage(messages, "system",
                        promptTemplate.buildSystemPrompt() + "\n\n" + promptTemplate.buildToolsDescription());

                // history
                List<Map<String, String>> history = loadHistory(uid);
                for (Map<String, String> h : history) {
                    addMessage(messages, h.get("role").equals("AGENT") ? "assistant" : "user", h.get("content"));
                }

                // current context
                String ctx = promptTemplate.buildContext(request.getPageContext(), request.getAddress(),
                        request.getLongitude() != null ? request.getLongitude().toString() : null,
                        request.getLatitude() != null ? request.getLatitude().toString() : null);
                if (ctx != null) {
                    addMessage(messages, "system", ctx);
                }
                addMessage(messages, "user", request.getContent());

                for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("model", model);
                    body.put("messages", messages);
                    body.put("tools", buildToolDefinitions());
                    body.put("stream", false); // 工具调用阶段不流式

                    Map<String, Object> response = callOpenAi(body);
                    Map<String, Object> choice = extractChoice(response);
                    if (choice == null) {
                        emitter.send(SseEmitter.event().name("error").data("Agent暂时无法响应"));
                        break;
                    }
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    if (message == null) {
                        emitter.send(SseEmitter.event().name("error").data("Agent暂时无法响应"));
                        break;
                    }

                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        messages.add(message);
                        for (Map<String, Object> tc : toolCalls) {
                            String toolName = (String) ((Map<String, Object>) tc.get("function")).get("name");
                            String toolArgs = (String) ((Map<String, Object>) tc.get("function")).get("arguments");
                            emitter.send(SseEmitter.event().name("tool_call")
                                    .data(toolCallMessage(toolName, toolArgs)));
                            String toolResult = executeTool(toolName, toolArgs, uid);
                            emitter.send(SseEmitter.event().name("tool_result")
                                    .data(toolResultSummary(toolName, toolResult)));
                            Map<String, Object> toolMsg = new LinkedHashMap<>();
                            toolMsg.put("role", "tool");
                            toolMsg.put("tool_call_id", tc.get("id"));
                            toolMsg.put("content", toolResult);
                            messages.add(toolMsg);
                        }
                        continue;
                    }

                    // 最后一轮：流式输出
                    String content = (String) message.getOrDefault("content", null);
                    if (content != null) {
                        emitter.send(SseEmitter.event().name("reply").data(content));
                        saveChatAsync(uid, ROLE_USER, request.getContent());
                        saveChatAsync(uid, ROLE_AGENT, content);
                        evictHistoryCache(uid);
                        publishRiskText(userId, request.getContent());
                    }
                    emitter.complete();
                    return;
                }
                emitter.send(SseEmitter.event().name("error").data("Agent思考轮次过多"));
                emitter.complete();
            } catch (Exception ex) {
                log.error("Agent SSE error", ex);
                try { emitter.send(SseEmitter.event().name("error").data("Agent异常: " + ex.getMessage())); } catch (IOException ignored) {}
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    // ---- ReAct 内部 ----

    private List<Map<String, Object>> buildInitialMessages(AgentChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();

        String systemContent = promptTemplate.buildSystemPrompt() + "\n\n" + promptTemplate.buildToolsDescription();
        addMessage(messages, "system", systemContent);

        String ctx = promptTemplate.buildContext(
                request.getPageContext(), request.getAddress(),
                request.getLongitude() != null ? request.getLongitude().toString() : null,
                request.getLatitude() != null ? request.getLatitude().toString() : null);
        if (ctx != null) {
            addMessage(messages, "system", ctx);
        }
        addMessage(messages, "user", request.getContent());
        return messages;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callOpenAi(Map<String, Object> body) {
        return restClient.post()
                .uri(baseUrl + "/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractChoice(Map<String, Object> response) {
        if (response == null) return null;
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return null;
        return choices.get(0);
    }

    private String executeTool(String toolName, String args, Long userId) {
        String result;
        try {
            result = switch (toolName) {
                case "search_shops" -> searchShops(args);
                case "get_shop_detail" -> getShopDetail(args);
                case "get_order_status" -> getOrderStatus(args);
                case "recommend_items" -> recommendItems(args);
                case "get_hot_search" -> getHotSearch();
                default -> "{\"error\":\"未知工具: " + toolName + "\"}";
            };
        } catch (Exception ex) {
            result = "{\"error\":\"" + ex.getMessage() + "\"}";
        }
        saveChatAsync(userId, ROLE_TOOL, "调用工具: " + toolName + "(" + args + ") → 结果: " + result);
        return result;
    }

    // ---- 工具函数 ----

    private String searchShops(String args) {
        Map<String, Object> params = parseArgs(args);
        Result<Map<String, Object>> result = shopToolClient.searchShops(
                currentUserId,
                str(params, "keyword"),
                bd(params, "longitude"), bd(params, "latitude"),
                str(params, "sort"),
                str(params, "cursor"),
                num(params, "size") != null ? num(params, "size").intValue() : null);
        return toJson(callResult(result));
    }

    private String getShopDetail(String args) {
        Map<String, Object> params = parseArgs(args);
        Result<Map<String, Object>> result = shopToolClient.getShopDetail(currentUserId, str(params, "shopId"));
        return toJson(callResult(result));
    }

    private String getOrderStatus(String args) {
        Map<String, Object> params = parseArgs(args);
        String orderId = str(params, "orderId");
        if (orderId != null) {
            Result<Map<String, Object>> result = orderToolClient.getOrderDetail(currentUserId, orderId);
            return toJson(callResult(result));
        }
        Integer limit = num(params, "limit") != null ? num(params, "limit").intValue() : null;
        Result<Map<String, Object>> result = orderToolClient.listRecentOrders(currentUserId, limit);
        return toJson(callResult(result));
    }

    private String getHotSearch() {
        Result<List<String>> result = shopToolClient.hotSearch();
        return toJson(callResult(result));
    }

    private String recommendItems(String args) {
        Map<String, Object> params = parseArgs(args);
        Result<Map<String, Object>> result = shopToolClient.searchShops(
                currentUserId,
                str(params, "keyword"),
                bd(params, "longitude"), bd(params, "latitude"),
                "rating", null, 5);
        return toJson(callResult(result));
    }

    // ---- 内部结果提取 ----

    private <T> T callResult(Result<T> result) {
        if (result == null) return null;
        if (result.getCode() == null || result.getCode() != 200) return null;
        return result.getData();
    }

    // ---- args 解析 ----

    private String currentUserId;

    private Map<String, Object> parseArgs(String args) {
        try {
            return objectMapper.readValue(args, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v != null ? v.toString() : null;
    }

    private BigDecimal bd(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v instanceof String s) return new BigDecimal(s);
        return null;
    }

    private Long num(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.valueOf(s);
        return null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, String> toolCallMessage(String name, String args) {
        String desc = switch (name) {
            case "search_shops" -> "正在搜索附近店铺";
            case "get_shop_detail" -> "正在获取店铺详情";
            case "get_order_status" -> "正在查询订单状态";
            case "recommend_items" -> "正在智能推荐商品";
            case "get_hot_search" -> "正在获取今日热搜";
            default -> "正在执行" + name;
        };
        return Map.of("tool", name, "status", desc, "args", args != null ? args : "");
    }

    private String toolResultSummary(String name, String result) {
        if (result == null || result.isBlank()) return "无结果";
        try {
            Map<String, Object> map = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            if (map.containsKey("error")) return "执行出错";
            if ("search_shops".equals(name) || "recommend_items".equals(name)) {
                Object records = map.get("records");
                if (records instanceof List<?> list) {
                    return "找到 " + list.size() + " 个结果";
                }
            }
            if ("get_shop_detail".equals(name)) {
                Object nameField = map.get("name");
                return nameField != null ? "已获取店铺「" + nameField + "」详情" : "已获取店铺详情";
            }
            if ("get_order_status".equals(name)) {
                if (map.containsKey("orderId")) return "已获取订单详情";
                Object records = map.get("records");
                if (records instanceof List<?> list) return "找到 " + list.size() + " 条订单";
            }
            return "执行完成";
        } catch (Exception ex) {
            return "执行完成";
        }
    }

    // ---- 工具定义 ----

    private List<Map<String, Object>> buildToolDefinitions() {
        return List.of(
                toolDef("search_shops", "搜索附近店铺。参数：keyword(关键词,可选)，longitude(可选)，latitude(可选)，sort(排序:rating/sales,可选)，size(条数,可选)",
                        Map.of("keyword", "string", "longitude", "number", "latitude", "number",
                                "sort", "string", "size", "integer")),
                toolDef("get_shop_detail", "获取店铺详情和商品列表。参数：shopId",
                        Map.of("shopId", "string")),
                toolDef("get_order_status", "查询自己订单。如传orderId查单个订单详情，否则传limit查最近N条。参数：orderId(可选)，limit(可选,默认5)",
                        Map.of("orderId", "string", "limit", "integer")),
                toolDef("recommend_items", "智能推荐附近高评分商品。参数：keyword(可选)，longitude(可选)，latitude(可选)",
                        Map.of("keyword", "string", "longitude", "number", "latitude", "number")),
                toolDef("get_hot_search", "获取今日热搜关键词列表。无参数。",
                        Map.of())
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolDef(String name, String desc, Map<String, String> props) {
        Map<String, Object> properties = new LinkedHashMap<>();
        props.forEach((k, t) -> properties.put(k, Map.of("type", t)));

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", desc);
        function.put("parameters", Map.of("type", "object", "properties", properties));

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    // ---- 存库 ----

    private String reply(Long uid, String userContent, String agentContent) {
        saveChatAsync(uid, ROLE_USER, userContent);
        saveChatAsync(uid, ROLE_AGENT, agentContent);
        return agentContent;
    }

    private void addMessage(List<Map<String, Object>> messages, String role, String content) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        messages.add(msg);
    }

    private void publishRiskText(String userId, String content) {
        if (content == null || content.isBlank()) return;
        try {
            rabbitTemplate.convertAndSend(MqConstants.RISK_EXCHANGE, MqConstants.RISK_TEXT_RECORD_ROUTING_KEY,
                    new RiskTextRecordCreateEventDTO("AGENT_CHAT", "0", userId, content));
        } catch (RuntimeException ex) {
            log.warn("风控文本事件发送失败", ex);
        }
    }

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}

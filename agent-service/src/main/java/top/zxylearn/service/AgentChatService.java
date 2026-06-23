package top.zxylearn.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import top.zxylearn.client.OrderToolClient;
import top.zxylearn.client.ShopToolClient;
import top.zxylearn.dto.AgentChatRequest;
import top.zxylearn.entity.AgentChat;
import top.zxylearn.mapper.AgentChatMapper;
import top.zxylearn.result.Result;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentChatService {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_AGENT = "AGENT";
    private static final String ROLE_TOOL = "TOOL";
    private static final int MAX_TOOL_ROUNDS = 5;

    private final RestClient restClient;
    private final PromptTemplate promptTemplate;
    private final AgentChatMapper agentChatMapper;
    private final ShopToolClient shopToolClient;
    private final OrderToolClient orderToolClient;
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
                            OrderToolClient orderToolClient) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.promptTemplate = promptTemplate;
        this.agentChatMapper = agentChatMapper;
        this.shopToolClient = shopToolClient;
        this.orderToolClient = orderToolClient;
        this.restClient = RestClient.builder().build();
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
    public String chat(String userId, AgentChatRequest request) {
        Long uid = parseUserId(userId);
        currentUserId = userId;
        List<Map<String, Object>> messages = buildInitialMessages(request);

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("tools", buildToolDefinitions());

            Map<String, Object> response = callOpenAi(body);
            Map<String, Object> choice = extractChoice(response);

            if (choice == null) {
                return reply(uid, request.getContent(), "Agent暂时无法响应");
            }

            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            if (message == null) {
                return reply(uid, request.getContent(), "Agent暂时无法响应");
            }

            // 有 tool_calls → 执行工具并继续循环
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                messages.add(message);
                for (Map<String, Object> tc : toolCalls) {
                    String toolName = (String) ((Map<String, Object>) tc.get("function")).get("name");
                    String toolArgs = (String) ((Map<String, Object>) tc.get("function")).get("arguments");
                    String toolResult = executeTool(toolName, toolArgs, uid);
                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", tc.get("id"));
                    toolMsg.put("content", toolResult);
                    messages.add(toolMsg);
                }
                continue;
            }

            // 纯文本回复
            String content = (String) message.getOrDefault("content", "Agent暂时无法响应");
            return reply(uid, request.getContent(), content);
        }

        return reply(uid, request.getContent(), "Agent思考轮次过多，请简化问题重试");
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
                        Map.of("keyword", "string", "longitude", "number", "latitude", "number"))
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

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}

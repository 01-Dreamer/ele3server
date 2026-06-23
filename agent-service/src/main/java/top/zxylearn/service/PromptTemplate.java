package top.zxylearn.service;

import org.springframework.stereotype.Component;

import java.util.StringJoiner;

@Component
public class PromptTemplate {

    private static final String SYSTEM_PROMPT = """
            你是 ELE3 外卖平台智能助手。
            职责：
            - 帮助用户找店、推荐商品、解释订单状态。
            - 当用户询问时，优先推荐评分高、销量大、距离近的店铺和商品。
            约束：
            - 回答要简洁、准确，控制在 150 字以内。
            - 不能引导用户绕过平台交易，不能承诺平台不提供的优惠或服务。
            - 可以利用经纬度信息推荐附近的店铺和菜品。
            - 可以根据当前页面信息提供更精准的建议。
            工具使用规则：
            - 你可以调用下方定义的工具来查询实时数据。
            - 必须使用工具获取数据，不能编造没有查询过的信息。
            - 工具调用结果会以 TOOL: 前缀出现在对话中。
            """;

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildToolsDescription() {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("你可以使用以下工具：");
        sj.add("1. search_shops - 搜索附近店铺。参数：keyword(关键词)，sort(排序方式：rating/sales)。");
        sj.add("2. get_shop_detail - 获取店铺详情和商品列表。参数：shopId。");
        sj.add("3. get_order_status - 查询订单状态。参数：orderId。");
        sj.add("4. recommend_items - 智能推荐附近高评分商品。参数：keyword(可选)，longitude(可选)，latitude(可选)。");
        sj.add("5. get_hot_search - 获取今日热搜关键词列表。无参数。");
        return sj.toString();
    }

    public String buildContext(String pageContext, String address, String longitude, String latitude) {
        StringBuilder sb = new StringBuilder("当前请求上下文：");
        boolean hasAny = false;
        if (address != null && !address.isBlank()) {
            sb.append("\n地址：").append(address).append("。");
            hasAny = true;
        }
        if (longitude != null && latitude != null) {
            sb.append("\n经度：").append(longitude).append("，纬度：").append(latitude).append("。");
            hasAny = true;
        }
        if (pageContext != null && !pageContext.isBlank()) {
            sb.append("\n当前页面信息：").append(pageContext).append("。");
            hasAny = true;
        }
        return hasAny ? sb.toString() : null;
    }
}

package top.zxylearn.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
@Schema(description = "HTTP请求风控事件")
public class HttpRiskEventDTO implements Serializable {

    @Schema(description = "事件ID，由网关生成 UUID")
    private String eventId;

    @Schema(description = "用户ID，雪花ID使用字符串传输")
    private String userId;

    @Schema(description = "请求IP")
    private String ip;

    @Schema(description = "请求方法：GET / POST / PUT / DELETE", example = "GET")
    private String method;

    @Schema(description = "请求路径", example = "/api/shop/get-shop/2067907527248560130")
    private String path;

    @Schema(description = "请求头")
    private Map<String, String> headers;

    @Schema(description = "查询参数")
    private Map<String, String> queryParams;

    @Schema(description = "事件时间戳，毫秒")
    private Long timestamp;
}

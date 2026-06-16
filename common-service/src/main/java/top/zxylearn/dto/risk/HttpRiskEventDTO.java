package top.zxylearn.dto.risk;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class HttpRiskEventDTO implements Serializable {

    /**
     * 事件ID，由网关生成 UUID
     */
    private String eventId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 请求IP
     */
    private String ip;

    /**
     * 请求方法：GET / POST / PUT / DELETE
     */
    private String method;

    /**
     * 请求路径
     */
    private String path;

    /**
     * 请求头
     */
    private Map<String, String> headers;

    /**
     * 查询参数
     */
    private Map<String, String> queryParams;

    /**
     * 事件时间戳
     */
    private Long timestamp;
}

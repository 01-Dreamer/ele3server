package top.zxylearn.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentChatRequest {

    private String content;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String address;

    private String pageContext;
}

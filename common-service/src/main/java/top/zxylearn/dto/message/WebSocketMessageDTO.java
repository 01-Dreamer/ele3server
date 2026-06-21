package top.zxylearn.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "WebSocket通用消息")
public class WebSocketMessageDTO<T> implements Serializable {

    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_NOTICE = "NOTICE";
    public static final String TYPE_PING = "PING";
    public static final String TYPE_PONG = "PONG";

    @Schema(description = "消息类型：CHAT聊天消息，NOTICE通知消息，PING心跳请求，PONG心跳响应", example = "CHAT")
    private String type;

    @Schema(description = "发送者用户ID，系统发送时固定为-1，雪花ID使用字符串传输", example = "2066777636767580162")
    private String senderId;

    @Schema(description = "接收者用户ID，雪花ID使用字符串传输", example = "2066777636767580163")
    private String receiverId;

    @Schema(description = "消息数据")
    private T data;

    @Schema(description = "消息时间戳，毫秒")
    private Long timestamp;

    public static <T> WebSocketMessageDTO<T> of(String type, String senderId, String receiverId, T data) {
        return new WebSocketMessageDTO<>(type, senderId, receiverId, data, System.currentTimeMillis());
    }

    public static WebSocketMessageDTO<Void> ping(String senderId, String receiverId) {
        return of(TYPE_PING, senderId, receiverId, null);
    }

    public static WebSocketMessageDTO<Void> pong(String senderId, String receiverId) {
        return of(TYPE_PONG, senderId, receiverId, null);
    }

    public static <T> WebSocketMessageDTO<T> chat(String senderId, String receiverId, T data) {
        return of(TYPE_CHAT, senderId, receiverId, data);
    }

    public static <T> WebSocketMessageDTO<T> notice(String receiverId, T data) {
        return of(TYPE_NOTICE, "-1", receiverId, data);
    }
}

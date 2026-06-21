package top.zxylearn.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建用户钱包请求")
public class PaymentWalletCreateRequest implements Serializable {

    @Schema(description = "用户ID，雪花ID使用字符串传输", example = "2066777636767580162")
    private String userId;
}

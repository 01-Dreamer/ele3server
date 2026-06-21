package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "图片直传授权请求")
public class DirectUploadPolicyRequest {

    @Schema(description = "原始文件名，用于识别图片后缀", example = "avatar.png")
    private String originalFilename;
}

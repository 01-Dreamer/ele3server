package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OSS 图片直传授权结果")
public class DirectUploadPolicyVO {

    @Schema(description = "OSS 表单上传地址")
    private String host;

    @Schema(description = "OSS 对象名，由后端生成")
    private String objectName;

    @Schema(description = "文件访问地址")
    private String url;

    @Schema(description = "OSS AccessKeyId")
    private String accessKeyId;

    @Schema(description = "Base64 后的上传策略")
    private String policy;

    @Schema(description = "上传策略签名")
    private String signature;

    @Schema(description = "过期时间戳，单位秒")
    private Long expire;

    @Schema(description = "上传成功后 OSS 返回状态码")
    private String successActionStatus;

    @Schema(description = "前端直传时需要携带的 Content-Type")
    private String contentType;
}

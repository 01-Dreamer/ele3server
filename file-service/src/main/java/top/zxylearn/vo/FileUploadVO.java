package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件上传结果")
public class FileUploadVO {

    @Schema(description = "OSS 对象名")
    private String objectName;

    @Schema(description = "文件访问地址")
    private String url;

    @Schema(description = "原始文件名")
    private String originalFilename;

    @Schema(description = "文件大小，单位字节")
    private Long size;

    @Schema(description = "文件 Content-Type")
    private String contentType;
}

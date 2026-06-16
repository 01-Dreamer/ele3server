package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.zxylearn.dto.DirectUploadPolicyRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.FileService;
import top.zxylearn.vo.DirectUploadPolicyVO;
import top.zxylearn.vo.FileUploadVO;

@Tag(name = "文件服务")
@RestController
@RequestMapping("/api/file")
public class ApiController {

    private final FileService fileService;

    public ApiController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "上传文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileUploadVO> upload(@RequestHeader("X-User-Id") String userId,
                                       @Parameter(description = "文件") @RequestParam("file") MultipartFile file) {
        try {
            return Result.success(fileService.upload(userId, file));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "文件上传失败");
        }
    }


    @Operation(summary = "获取图片直传授权")
    @PostMapping("/upload-policy")
    public Result<DirectUploadPolicyVO> createUploadPolicy(@RequestHeader("X-User-Id") String userId,
                                                           @RequestBody DirectUploadPolicyRequest request) {
        try {
            return Result.success(fileService.createDirectUploadPolicy(userId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "直传授权生成失败");
        }
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/delete")
    public Result<?> delete(@RequestHeader("X-User-Id") String userId,
                            @Parameter(description = "文件访问地址") @RequestParam("objectName") String objectName) {
        try {
            fileService.delete(userId, objectName);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "文件删除失败");
        }
    }
}

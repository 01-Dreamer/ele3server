package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.result.Result;
import top.zxylearn.service.FileService;

@Tag(name = "文件服务管理员接口")
@RestController
@RequestMapping("/api/file/admin")
public class AdminController {

    private final FileService fileService;

    public AdminController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/delete")
    public Result<?> delete(@Parameter(description = "文件访问地址") @RequestParam("url") String url) {
        try {
            fileService.adminDelete(url);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "文件删除失败");
        }
    }
}

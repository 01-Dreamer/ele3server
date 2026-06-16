package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.user.UserCreateRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.UserInternalService;

@Tag(name = "用户内部接口")
@RestController
@RequestMapping("/internal/user")
public class InternalController {

    private final UserInternalService userInternalService;

    public InternalController(UserInternalService userInternalService) {
        this.userInternalService = userInternalService;
    }

    @Operation(summary = "创建用户资料")
    @PostMapping("/create-user")
    public Result<?> createUser(@RequestBody UserCreateRequest request) {
        try {
            userInternalService.createUser(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户资料创建失败");
        }
    }
}

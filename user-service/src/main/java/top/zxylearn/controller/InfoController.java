package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.LoginUser;
import top.zxylearn.dto.UserUpdateRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.UserService;

@Tag(name = "信息修改模块")
@RestController
@RequestMapping("/info")
public class InfoController {

    private final UserService userService;

    public InfoController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "修改用户资料")
    @PutMapping("/profile")
    public Result<LoginUser> updateProfile(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestBody UserUpdateRequest request) {
        try {
            return Result.success(userService.updateProfile(authorization, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户资料修改失败");
        }
    }
}

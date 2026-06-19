package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.result.Result;
import top.zxylearn.service.UserService;
import top.zxylearn.vo.UserVO;

@Tag(name = "管理员接口")
@RestController
@RequestMapping("/api/user/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "根据用户ID获取用户资料")
    @GetMapping("/profile")
    public Result<UserVO> getUser(@RequestParam("userId") String userId) {
        try {
            return Result.success(userService.getUser(userId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户资料获取失败");
        }
    }
}

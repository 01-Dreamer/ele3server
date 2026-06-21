package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.UserLocationCreateRequest;
import top.zxylearn.dto.UserUpdateRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.UserService;
import top.zxylearn.vo.PageVO;
import top.zxylearn.vo.UserBriefVO;
import top.zxylearn.vo.UserLocationVO;
import top.zxylearn.vo.UserVO;

@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/user")
public class ApiController {

    private final UserService userService;

    public ApiController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "获取自己的用户资料")
    @GetMapping("/profile")
    public Result<UserVO> getProfile(@RequestHeader("X-User-Id") String userId) {
        try {
            return Result.success(userService.getUser(userId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户资料获取失败");
        }
    }

    @Operation(summary = "修改自己的用户资料")
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@RequestHeader("X-User-Id") String userId,
                                        @RequestBody UserUpdateRequest request) {
        try {
            return Result.success(userService.updateUser(userId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户资料修改失败");
        }
    }


    @Operation(summary = "根据用户ID获取头像和昵称")
    @GetMapping("/brief/{userId}")
    public Result<UserBriefVO> getUserBrief(@PathVariable String userId) {
        try {
            return Result.success(userService.getUserBrief(userId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户头像昵称获取失败");
        }
    }

    @Operation(summary = "新增自己的收货地址")
    @PostMapping("/add-location")
    public Result<UserLocationVO> addLocation(@RequestHeader("X-User-Id") String userId,
                                              @RequestBody UserLocationCreateRequest request) {
        try {
            return Result.success(userService.addLocation(userId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "收货地址新增失败");
        }
    }

    @Operation(summary = "获取自己的某个收货地址，不传locationId返回最新创建的一个")
    @GetMapping("/get-location")
    public Result<UserLocationVO> getLocation(@RequestHeader("X-User-Id") String userId,
                                               @RequestParam(value = "locationId", required = false) String locationId) {
        try {
            return Result.success(userService.getLocation(userId, locationId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "收货地址获取失败");
        }
    }

    @Operation(summary = "删除自己的收货地址")
    @DeleteMapping("/delete-location/{locationId}")
    public Result<?> deleteLocation(@RequestHeader("X-User-Id") String userId,
                                    @PathVariable String locationId) {
        try {
            userService.deleteLocation(userId, locationId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "收货地址删除失败");
        }
    }

    @Operation(summary = "获取自己的收货地址列表")
    @GetMapping("/list-location")
    public Result<PageVO<UserLocationVO>> listLocations(@RequestHeader("X-User-Id") String userId,
                                                         @RequestParam(value = "page", required = false) Integer page,
                                                         @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(userService.listLocations(userId, page, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "收货地址列表获取失败");
        }
    }
}

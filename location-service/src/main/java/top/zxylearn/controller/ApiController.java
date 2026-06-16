package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.CoordinateUploadRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.LocationService;
import top.zxylearn.vo.CoordinateVO;

@Tag(name = "位置服务")
@RestController
@RequestMapping("/api/location")
public class ApiController {

    private final LocationService locationService;

    public ApiController(LocationService locationService) {
        this.locationService = locationService;
    }

    @Operation(summary = "上传用户经纬度")
    @PostMapping("/coordinate")
    public Result<CoordinateVO> uploadCoordinate(@RequestHeader("X-User-Id") String userId,
                                                 @RequestBody CoordinateUploadRequest request) {
        try {
            return Result.success(locationService.uploadCoordinate(userId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "经纬度上传失败");
        }
    }

    @Operation(summary = "获取指定用户经纬度")
    @GetMapping("/coordinate")
    public Result<CoordinateVO> getCoordinate(@RequestParam("userId") String userId) {
        try {
            CoordinateVO coordinate = locationService.getCoordinate(userId);
            if (coordinate == null) {
                return Result.fail(404, "用户经纬度不存在");
            }
            return Result.success(coordinate);
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "经纬度获取失败");
        }
    }
}

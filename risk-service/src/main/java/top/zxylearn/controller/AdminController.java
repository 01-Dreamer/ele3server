package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.RiskTextHandleRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.RiskTextService;
import top.zxylearn.vo.PageVO;
import top.zxylearn.vo.RiskTextRecordVO;

@Tag(name = "管理员接口")
@RestController
@RequestMapping("/api/risk/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final RiskTextService riskTextService;

    public AdminController(RiskTextService riskTextService) {
        this.riskTextService = riskTextService;
    }

    @Operation(summary = "获取风控文本记录列表（偏移分页）")
    @GetMapping("/text-records")
    public Result<PageVO<RiskTextRecordVO>> listTextRecords(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(riskTextService.listRecords(status, page, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("风控记录获取失败", ex);
            return Result.fail(500, "风控记录获取失败");
        }
    }

    @Operation(summary = "处理风控文本记录")
    @PostMapping("/handle-text-record")
    public Result<?> handleTextRecord(@RequestBody RiskTextHandleRequest request) {
        try {
            riskTextService.handleRecord(request.getId(), request.getHandleOpinion());
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("风控记录处理失败", ex);
            return Result.fail(500, "风控记录处理失败");
        }
    }
}

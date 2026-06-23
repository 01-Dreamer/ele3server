package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.entity.RiskTextRecord;
import top.zxylearn.mapper.RiskTextRecordMapper;
import top.zxylearn.vo.PageVO;
import top.zxylearn.vo.RiskTextRecordVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RiskTextService {

    private final RiskTextRecordMapper riskTextRecordMapper;

    public RiskTextService(RiskTextRecordMapper riskTextRecordMapper) {
        this.riskTextRecordMapper = riskTextRecordMapper;
    }

    public PageVO<RiskTextRecordVO> listRecords(Integer status, Integer page, Integer size) {
        long pageNum = page != null && page > 0 ? page : 1;
        long pageSize = size != null && size > 0 ? Math.min(size, 100) : 20;

        LambdaQueryWrapper<RiskTextRecord> wrapper = new LambdaQueryWrapper<RiskTextRecord>()
                .eq(status != null, RiskTextRecord::getStatus, status)
                .orderByDesc(RiskTextRecord::getCreateTime);

        Page<RiskTextRecord> result = riskTextRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<RiskTextRecordVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private RiskTextRecordVO toVO(RiskTextRecord r) {
        return new RiskTextRecordVO(
                String.valueOf(r.getId()), r.getSourceType(),
                r.getSourceId() != null ? String.valueOf(r.getSourceId()) : null,
                r.getUserId() != null ? String.valueOf(r.getUserId()) : null,
                r.getContent(), r.getStatus(), r.getHandleOpinion(),
                r.getHandleTime(), r.getCreateTime(), r.getUpdateTime());
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleRecord(String id, String handleOpinion) {
        Long recordId = parseLongId(id, "风控记录ID");
        if (handleOpinion == null || handleOpinion.isBlank()) {
            throw new IllegalArgumentException("处理意见不能为空");
        }
        int updated = riskTextRecordMapper.update(null, new LambdaUpdateWrapper<RiskTextRecord>()
                .set(RiskTextRecord::getStatus, 1)
                .set(RiskTextRecord::getHandleOpinion, handleOpinion.trim())
                .set(RiskTextRecord::getHandleTime, LocalDateTime.now())
                .eq(RiskTextRecord::getId, recordId)
                .eq(RiskTextRecord::getStatus, 0));
        if (updated <= 0) {
            throw new IllegalArgumentException("风控记录不存在或已处理");
        }
    }

    private Long parseLongId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + "格式不正确");
        }
    }
}

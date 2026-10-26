package com.graduation.mentor.service;

import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.entity.OperLog;
import com.graduation.mentor.mapper.OperLogMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 操作日志。
 *
 * <p>设计约束：日志写入失败绝不阻塞主流程（catch 掉并告警）， 因为审计是旁路能力，不能反过来影响点评提交。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogService {

    private final OperLogMapper operLogMapper;
    private final AuthContext authContext;

    public void record(
            String action, String targetType, Long targetId, String detail, boolean success) {
        try {
            OperLog l = new OperLog();
            try {
                l.setUserId(authContext.userId());
                l.setUsername(authContext.currentUser().getUsername());
                l.setRole(authContext.currentUser().getRole());
            } catch (Exception ignored) {
                // 未登录场景（如无 token 打接口）允许留空
            }
            l.setAction(success ? action : action + "(拒绝)");
            l.setTargetType(targetType);
            l.setTargetId(targetId);
            l.setDetail(detail);
            l.setCreatedAt(LocalDateTime.now());
            operLogMapper.insert(l);
        } catch (Exception e) {
            log.warn("操作日志写入失败（不影响主流程）：{}", e.getMessage());
        }
    }

    public void record(String action, String targetType, Long targetId, String detail) {
        record(action, targetType, targetId, detail, true);
    }
}

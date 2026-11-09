package com.graduation.mentor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.common.BizException;
import com.graduation.mentor.common.PageResult;
import com.graduation.mentor.dto.OperLogDtos;
import com.graduation.mentor.entity.OperLog;
import com.graduation.mentor.mapper.OperLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志。
 *
 * <p>设计约束：日志写入失败绝不阻塞主流程（catch 掉并告警）， 因为审计是旁路能力，不能反过来影响点评提交。
 *
 * <p>查询能力同样只对教务开放：日志里含操作人、IP 与被退回点评的上下文， 对导师或学生开放等于给了一条查看他人操作轨迹的旁路。
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

    /**
     * 分页查询操作日志（仅教务）。
     *
     * <p>排序固定为「时间倒序 + 主键倒序」：同一秒内产生的多条日志（如批量审核） 若只按时间排，翻页时顺序不稳定，会出现同一条记录在第 1 页和第 2 页各出现一次。
     */
    public PageResult<OperLog> page(OperLogDtos.Query q) {
        requireAdmin();

        LambdaQueryWrapper<OperLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getAction())) {
            w.like(OperLog::getAction, q.getAction().trim());
        }
        if (StringUtils.hasText(q.getUsername())) {
            w.like(OperLog::getUsername, q.getUsername().trim());
        }
        if (StringUtils.hasText(q.getRole())) {
            w.eq(OperLog::getRole, q.getRole().trim());
        }
        if (StringUtils.hasText(q.getStartDate())) {
            w.ge(OperLog::getCreatedAt, q.getStartDate() + " 00:00:00");
        }
        if (StringUtils.hasText(q.getEndDate())) {
            w.le(OperLog::getCreatedAt, q.getEndDate() + " 23:59:59");
        }
        w.orderByDesc(OperLog::getCreatedAt).orderByDesc(OperLog::getId);

        Page<OperLog> p = operLogMapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return PageResult.of(p.getRecords(), p.getTotal(), q.getPage(), q.getSize());
    }

    /**
     * 出现过的动作名（供前端下拉，避免筛选框写死一份可能过期的清单）。
     *
     * <p>含「(拒绝)」后缀的条目由写入侧生成，这里原样返回，前端按原文筛选即可。
     */
    public List<String> actions() {
        requireAdmin();
        return operLogMapper
                .selectList(
                        new QueryWrapper<OperLog>().select("DISTINCT action").orderByAsc("action"))
                .stream()
                .map(OperLog::getAction)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void requireAdmin() {
        if (!authContext.isAdmin()) {
            throw BizException.forbidden("仅教务管理员可查看操作日志");
        }
    }
}

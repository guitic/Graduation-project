package com.graduation.mentor.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常处理：保证前端拿到的永远是统一 JSON，而不是 HTML 错误页或堆栈。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        return R.fail(401, "登录已失效，请重新登录");
    }

    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException e) {
        return R.fail(403, "当前角色无权访问该功能");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValid(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + " " + fe.getDefaultMessage();
        return R.fail(400, msg);
    }

    /**
     * 路径不存在 → 404。
     *
     * <p>不单独处理的话会被下面的 {@code Exception} 兜底成 500「系统繁忙」： 前端拼错路径或版本不匹配时，页面只显示"系统繁忙"，把"接口根本不存在"这个
     * 唯一有效线索掩盖掉，排查会绕很远。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNotFound(NoResourceFoundException e) {
        return R.fail(404, "接口不存在：" + e.getResourcePath());
    }

    /** 请求方法不匹配（如用 GET 调 POST 接口）→ 405，同样不能笼统报 500 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(405, "请求方法不支持：" + e.getMethod());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return R.fail(500, "系统繁忙，请稍后重试");
    }
}

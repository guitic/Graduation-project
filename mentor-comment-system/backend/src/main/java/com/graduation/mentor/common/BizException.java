package com.graduation.mentor.common;

import lombok.Getter;

/** 业务异常。code 语义： 400 参数错误 / 401 未登录 / 403 无权限 / 404 不存在 / 409 状态冲突 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException forbidden(String msg) {
        return new BizException(403, msg);
    }

    public static BizException notFound(String msg) {
        return new BizException(404, msg);
    }

    public static BizException conflict(String msg) {
        return new BizException(409, msg);
    }
}

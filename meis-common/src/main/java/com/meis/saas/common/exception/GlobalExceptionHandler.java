package com.meis.saas.common.exception;

import com.meis.saas.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public Result<Void> handle(BizException e) {
        if (e.getCode() >= 500) {
            log.error("BizException {}: {}", e.getCode(), e.getMessage());
        }
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String detail = root.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = e.getMessage();
        }
        // Spring 包装 SQL 异常时常只有 “bad SQL grammar [SQL]”，把根因拼进返回，便于排查
        String top = e.getMessage();
        String msg = (top != null && detail != null && !top.contains(detail))
                ? top + " | " + detail
                : (detail != null ? detail : "服务器错误");
        log.error("Unhandled exception: {}", msg, e);
        return Result.fail(msg);
    }
}

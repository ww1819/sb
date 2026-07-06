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
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return Result.fail(e.getMessage());
    }
}

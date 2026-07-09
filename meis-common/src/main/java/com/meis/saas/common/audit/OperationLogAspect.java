package com.meis.saas.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meis.saas.common.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        String status = "success";
        String error = null;
        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            status = "fail";
            error = t.getMessage();
            throw t;
        } finally {
            try {
                saveLog(pjp, opLog, start, status, error);
            } catch (Exception ignored) {}
        }
    }

    private void saveLog(ProceedingJoinPoint pjp, OperationLog opLog, long start, String status, String error) throws Exception {
        if ("public".equals(TenantContext.getSchemaName())) return;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest req = attrs != null ? attrs.getRequest() : null;
        String userId = TenantContext.getUserId();
        jdbc.update("""
            INSERT INTO sys_operation_log (user_id, operation_type, module_name, operation_desc,
                request_method, request_url, request_params, execution_time, status, error_msg)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            """,
                userId != null ? userId : null,
                pjp.getSignature().getName(),
                opLog.module(),
                opLog.description(),
                req != null ? req.getMethod() : null,
                req != null ? req.getRequestURI() : null,
                mapper.writeValueAsString(pjp.getArgs()),
                (int) (System.currentTimeMillis() - start),
                status,
                error);
    }
}

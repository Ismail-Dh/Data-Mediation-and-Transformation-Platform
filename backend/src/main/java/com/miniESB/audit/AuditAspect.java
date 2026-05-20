package com.miniESB.audit;

import com.miniESB.dto.auditlog.AuditEntry;
import com.miniESB.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Arrays;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogService auditLogService;

    /**
     * Replaces the previous @AfterReturning + @AfterThrowing pair with a single
     * @Around advice so we can measure execution time (duration_ms) in both
     * success and error cases.
     */
    @Around("@annotation(auditable)")
    public Object auditAround(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {

        long start = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            saveAudit(joinPoint, auditable, result, duration, null);

            return result;

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;

            saveAuditOnException(joinPoint, auditable, duration, ex);

            throw ex; // re-throw so Spring exception handlers still fire
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void saveAudit(ProceedingJoinPoint joinPoint, Auditable auditable,
                           Object result, long durationMs, Exception ex) {
        try {
            Authentication auth  = getAuthentication();
            Integer httpStatus   = null;
            String  errorMessage = null;
            String  errorCode    = null;

            if (result instanceof ResponseEntity<?> re) {
                httpStatus = re.getStatusCode().value();
                if (re.getBody() instanceof ErrorResponse error) {
                    errorMessage = error.message();
                    errorCode    = error.code();
                }
            }

            auditLogService.save(new AuditEntry(
                    resolveUsername(auth),
                    resolveRole(auth),
                    auditable.action(),
                    auditable.targetEntity(),
                    resolveTargetId(joinPoint.getArgs()),
                    buildDetails(joinPoint.getArgs()),
                    httpStatus,
                    errorMessage,
                    errorCode,
                    durationMs
            ));

        } catch (Exception e) {
            log.error("[AUDIT] Logging failed for {}: {}",
                    joinPoint.getSignature().getName(), e.getMessage());
        }
    }

    private void saveAuditOnException(ProceedingJoinPoint joinPoint, Auditable auditable,
                                      long durationMs, Exception ex) {
        try {
            Authentication auth = getAuthentication();

            auditLogService.save(new AuditEntry(
                    resolveUsername(auth),
                    resolveRole(auth),
                    auditable.action(),
                    auditable.targetEntity(),
                    resolveTargetId(joinPoint.getArgs()),
                    buildDetails(joinPoint.getArgs()),
                    500,
                    ex.getMessage(),
                    ex.getClass().getSimpleName(),
                    durationMs
            ));

        } catch (Exception e) {
            log.error("[AUDIT] Logging failed on exception for {}: {}",
                    joinPoint.getSignature().getName(), e.getMessage());
        }
    }

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private String resolveUsername(Authentication auth) {
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    private String resolveRole(Authentication auth) {
        if (auth == null) return "UNKNOWN";
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("UNKNOWN");
    }

    private String resolveTargetId(Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof Long id) {
            return String.valueOf(id);
        }
        return null;
    }

    private String buildDetails(Object[] args) {
        if (args == null || args.length == 0) return null;
        return Arrays.stream(args)
                .filter(arg -> arg != null && !(arg instanceof Authentication))
                .map(this::sanitize)
                .reduce((a, b) -> a + " | " + b)
                .orElse(null);
    }

    private String sanitize(Object arg) {
        if (arg instanceof String || arg instanceof Number) {
            return arg.toString();
        }
        StringBuilder sb = new StringBuilder(arg.getClass().getSimpleName()).append("{");
        for (Field field : arg.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                String value = field.isAnnotationPresent(Sensitive.class)
                        ? "***"
                        : String.valueOf(field.get(arg));
                sb.append(field.getName()).append("=").append(value).append(", ");
            } catch (IllegalAccessException e) {
                sb.append(field.getName()).append("=??, ");
            }
        }
        if (sb.toString().endsWith(", ")) sb.setLength(sb.length() - 2);
        return sb.append("}").toString();
    }
}
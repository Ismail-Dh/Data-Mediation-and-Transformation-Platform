package com.miniESB.audit;

import com.miniESB.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogService auditLogService;

    @AfterReturning(
        pointcut = "@annotation(auditable)",
        returning = "result"
    )
    public void logAudit(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            String username = resolveUsername();
            String role     = resolveRole();
            String targetId = resolveTargetId(joinPoint.getArgs());
            String details  = buildDetails(joinPoint.getArgs());

            auditLogService.save(
                    username,
                    role,
                    auditable.action(),
                    auditable.targetEntity(),
                    targetId,
                    details
            );
        } catch (Exception e) {
            log.error("Audit logging failed for method {}: {}",
                    joinPoint.getSignature().getName(), e.getMessage());
        }
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    private String resolveRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "UNKNOWN";
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
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
            .filter(arg -> arg != null && !(arg instanceof org.springframework.security.core.Authentication))
            .map(Object::toString)
            .reduce((a, b) -> a + " | " + b)
            .orElse(null);
   }
}
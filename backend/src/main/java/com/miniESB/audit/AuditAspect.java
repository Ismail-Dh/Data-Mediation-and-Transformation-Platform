package com.miniESB.audit;

import com.miniESB.dto.auditlog.AuditEntry;
import com.miniESB.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
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

    // ─── Cas normal — méthode retourne une réponse ────────────────────────────

    @AfterReturning(
        pointcut = "@annotation(auditable)",
        returning = "result"
    )
    public void logAudit(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            Authentication auth = getAuthentication();
            Integer httpStatus  = null;
            String errorMessage = null;
            String errorCode    = null;

            if (result instanceof ResponseEntity<?> re) {
                httpStatus = re.getStatusCode().value();

                // Si la réponse contient un body d'erreur structuré
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
                    errorCode
            ));

        } catch (Exception e) {
            log.error("Audit logging failed for method {}: {}",
                    joinPoint.getSignature().getName(), e.getMessage());
        }
    }

    // ─── Cas exception — méthode lance une exception ──────────────────────────

    @AfterThrowing(
        pointcut = "@annotation(auditable)",
        throwing  = "ex"
    )
    public void logAuditOnException(JoinPoint joinPoint, Auditable auditable, Exception ex) {
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
                    ex.getClass().getSimpleName()
            ));

        } catch (Exception e) {
            log.error("Audit logging failed on exception for method {}: {}",
                    joinPoint.getSignature().getName(), e.getMessage());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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
    // Types primitifs et String — pas de champs à masquer
    if (arg instanceof String || arg instanceof Number) {
        return arg.toString();
    }

    StringBuilder sb = new StringBuilder(arg.getClass().getSimpleName()).append("{");
    Field[] fields = arg.getClass().getDeclaredFields();

    for (Field field : fields) {
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

    // Supprimer la dernière virgule
    if (sb.toString().endsWith(", ")) {
        sb.setLength(sb.length() - 2);
    }

    return sb.append("}").toString();
}
}
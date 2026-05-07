package com.miniESB.audit;

public record ErrorResponse(
        String message,
        String code
) {}
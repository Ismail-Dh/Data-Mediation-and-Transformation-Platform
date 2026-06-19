package com.miniESB.dto.sandbox;

public record MappingRuleSummary(
    String  mappingType,
    String  sourceField,
    String  targetField,
    String  expression,
    boolean applied,
    String  valueBefore,
    String  valueAfter 
) {}
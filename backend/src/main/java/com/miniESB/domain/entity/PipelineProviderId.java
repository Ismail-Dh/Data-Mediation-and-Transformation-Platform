package com.miniESB.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Clé composite de {@link PipelineProvider} : (pipeline, provider).
 * Les noms des champs doivent correspondre exactement aux champs @Id
 * de PipelineProvider (requis par @IdClass).
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PipelineProviderId implements Serializable {
    private Long pipeline;
    private Long provider;
}
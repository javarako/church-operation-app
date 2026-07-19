package com.church.operation.service;

public record RestoreVerification(
    long collectionCount,
    long documentCount,
    long indexCount
) {
}

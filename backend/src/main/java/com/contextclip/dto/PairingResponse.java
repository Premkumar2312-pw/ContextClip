package com.contextclip.dto;

public record PairingResponse(
    String code,
    long expiresInSeconds,
    String pairUrl
) {}

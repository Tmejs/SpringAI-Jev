package com.tmejs.springaijev.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}

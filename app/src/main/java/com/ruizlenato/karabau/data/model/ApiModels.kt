package com.ruizlenato.karabau.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ExchangeKeyRequest(
    val email: String,
    val password: String,
    val keyName: String
)

@Serializable
data class ExchangeKeyResponse(
    val id: String,
    val name: String,
    val key: String,
    val createdAt: String
)

@Serializable
data class ValidateKeyRequest(
    val apiKey: String
)

@Serializable
data class ValidateKeyResponse(
    val success: Boolean
)

@Serializable
data class RevokeKeyRequest(
    val id: String
)

@Serializable
data class ApiError(
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class HealthCheckResponse(
    val status: String,
    val version: String? = null
)

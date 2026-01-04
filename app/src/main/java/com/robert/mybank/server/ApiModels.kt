package com.robert.mybank.server

data class ApiOk(
    val ok: Boolean,
    val error: String? = null
)

data class RegisterRequest(val login: String, val pass: String, val name: String)
data class RegisterResponse(val ok: Boolean, val user_id: Int? = null, val error: String? = null)

data class ConfirmRequest(val login: String, val code: String)
data class ConfirmResponse(val ok: Boolean, val error: String? = null)

data class LoginRequest(val login: String, val pass: String)
data class UserDto(val id: Int, val name: String)
data class LoginResponse(val ok: Boolean, val token: String? = null, val user: UserDto? = null, val error: String? = null)

package com.ruizlenato.karabau.data.model

data class WhoAmIResponse(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val image: String? = null
)

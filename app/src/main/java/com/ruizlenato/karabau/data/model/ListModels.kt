package com.ruizlenato.karabau.data.model

data class SavedListItem(
    val id: String,
    val name: String,
    val description: String?,
    val icon: String,
    val parentId: String?,
    val type: String,
    val query: String?,
    val isPublic: Boolean,
    val hasCollaborators: Boolean,
    val userRole: String
)

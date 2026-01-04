package com.robert.mybank.server


data class CategoryDto(
    val id: Int,
    val name: String
)

data class CategoriesResponse(
    val ok: Boolean,
    val items: List<CategoryDto> = emptyList(),
    val error: String? = null
)

data class CreateCategoryRequest(val name: String)
data class CreateCategoryResponse(
    val ok: Boolean,
    val id: Int? = null,
    val error: String? = null
)

data class ApiOkResponse(
    val ok: Boolean,
    val error: String? = null
)

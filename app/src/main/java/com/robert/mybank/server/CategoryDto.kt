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
data class RecordDto(
    val id: Int,
    val product_id: Int,
    val price: Double,
    val date: String,
    val product_name: String,
    val category_id: Int,
    val category_name: String
)

data class RecordsResponse(
    val ok: Boolean,
    val items: List<RecordDto> = emptyList(),
    val error: String? = null
)

data class RecordItemIn(
    val category_id: Int,
    val name: String,
    val price: Double
)

data class CreateRecordsRequest(
    val date: String,                // "YYYY-MM-DD HH:MM:SS"
    val items: List<RecordItemIn>
)

data class CreateRecordsResponse(
    val ok: Boolean,
    val records: List<Int> = emptyList(),
    val created_products: List<Int> = emptyList(),
    val error: String? = null
)
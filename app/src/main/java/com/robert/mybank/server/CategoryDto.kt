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

data class IncomeDto(
    val id: Int,
    val title: String,
    val amount: Double,
    val date: String
)

data class IncomeGetResponse(
    val ok: Boolean,
    val items: List<IncomeDto> = emptyList(),
    val error: String? = null
)

data class IncomeItemIn(
    val title: String,
    val amount: Double,
    val date: String
)

data class IncomeCreateRequest(
    val items: List<IncomeItemIn>
)

data class IncomeCreateResponse(
    val ok: Boolean,
    val ids: List<Int> = emptyList(),
    val error: String? = null
)
data class IncomeUpdateRequest(
    val title: String? = null,
    val amount: Double? = null,
    val date: String? = null
)
data class GetTargetResponse(
    val ok: Boolean,
    val item: TargetDto? = null,
    val error: String? = null
)

data class CreateTargetRequest(val name: String, val cost: Double)
data class CreateTargetResponse(val ok: Boolean, val id: Int? = null, val error: String? = null)

data class UpdateTargetRequest(
    val name: String? = null,
    val cost: Double? = null,
    val status: Int? = null
)


data class TargetsCurrentResponse(val ok:Boolean, val items: Map<String, TargetDto?>, val error:String? = null)

data class TargetIn(val type:Int, val name:String, val cost:Double)

data class TargetsSummaryResponse(
    val ok: Boolean,
    val error: String? = null,
    val month: MonthBlock? = null,
    val items: ItemsBlock? = null
)

data class MonthBlock(
    val start: String,
    val end: String,
    val expense: Double,
    val income: Double
)

data class ItemsBlock(
    val saving: SummaryItem? = null,
    val expense: SummaryItem? = null,
    val income: SummaryItem? = null
)
data class TargetCurrentItemIn(val type: Int, val name: String, val cost: Double)
data class TargetsCurrentPutRequest(val items: List<TargetCurrentItemIn>)
data class TargetsCurrentPutResponse(val ok: Boolean, val error: String? = null, val ids: List<Int> = emptyList())
data class SummaryItem(
    val target_id: Int,
    val type: Int,
    val target_name: String,
    val target_cost: Double,
    val created_at: String,
    val current_value: Double,
    val percent: Double
)
data class TargetsCurrentGetResponse(
    val ok: Boolean,
    val error: String? = null,
    val items: Map<String, TargetDto?> = emptyMap()
)

data class TargetDto(
    val id: Int,
    val type: Int,
    val name: String,
    val cost: Double,
    val status: Int,
    val created_at: String
)
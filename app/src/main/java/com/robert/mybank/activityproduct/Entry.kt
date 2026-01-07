package com.robert.mybank.activityproduct

data class Entry(
    var name: String,
    var value: String,
    var id: Int? = null // для income: id записи, для расходов null
)
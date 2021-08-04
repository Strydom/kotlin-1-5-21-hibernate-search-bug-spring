package com.example.kotlin1521hibernatesearchbugspring

interface IndexationDefinition {

    val fields: Set<FieldDefinition>

    fun getFieldDefinitions(type: String): List<FieldDefinition>
}

package com.example.kotlin1521hibernatesearchbugspring

interface FieldTransformer {
    fun transform(context: IndexTransformerContext): Any?
}

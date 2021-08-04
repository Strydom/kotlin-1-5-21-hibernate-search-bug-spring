package com.example.kotlin1521hibernatesearchbugspring

import kotlin.reflect.KClass
import kotlin.reflect.KProperty

interface IndexValueProviderContext {
    val transformer: KClass<out FieldTransformer>
    val parameters: Map<String, String>

    operator fun getValue(
        transformerContext: IndexTransformerContext,
        property: KProperty<*>
    ): KClass<out FieldTransformer> = transformer
}

package com.example.kotlin1521hibernatesearchbugspring

import org.hibernate.search.engine.backend.types.Aggregable
import org.hibernate.search.engine.backend.types.Projectable
import org.hibernate.search.engine.backend.types.Searchable
import org.hibernate.search.engine.backend.types.dsl.IndexFieldTypeFactory
import org.hibernate.search.engine.backend.types.dsl.StandardIndexFieldTypeOptionsStep
import org.hibernate.search.engine.backend.types.dsl.StringIndexFieldTypeOptionsStep
import java.util.Locale
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure

interface IFieldDefinition {
    val name: String
    val shared: Boolean
    val nodeType: String
    val type: FieldType
    val valueProviderContext: IndexValueProviderContext
    val projectable: Projectable
    val searchable: Searchable
    val aggregable: Aggregable
    val indexFieldName: String
}

abstract class FieldDefinition(
    override val name: String,
    override val shared: Boolean,
    override val nodeType: String,
    override val type: FieldType,
    override val valueProviderContext: IndexValueProviderContext,
    override val projectable: Projectable,
    override val searchable: Searchable,
    override val aggregable: Aggregable
) : IFieldDefinition {

    override val indexFieldName get() = if (shared) name else "${nodeType.lowercase(Locale.getDefault())}-$name"

    protected open fun setupStringIndexFieldType(
        indexFieldType: StringIndexFieldTypeOptionsStep<*>
    ) = indexFieldType

    protected open fun setupStandardIndexFieldType(
        indexFieldType: StandardIndexFieldTypeOptionsStep<*, *>
    ) = indexFieldType

    fun buildIndexDefinition(
        typeFactory: IndexFieldTypeFactory
    ): StandardIndexFieldTypeOptionsStep<out StandardIndexFieldTypeOptionsStep<*, out Any>, out Any> {
        val indexType = if (type.indexType == String::class.starProjectedType) {
            typeFactory.asString()
                .let(::setupStringIndexFieldType)
        } else {
            typeFactory.`as`(type.indexType.jvmErasure.javaObjectType)
                .let(::setupStandardIndexFieldType)
        }

        if (projectable != Projectable.DEFAULT) indexType.projectable(projectable)
        if (searchable != Searchable.DEFAULT) indexType.searchable(searchable)
        if (aggregable != Aggregable.DEFAULT) indexType.aggregable(aggregable)

        return indexType
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        other as FieldDefinition

        if (name != other.name) return false
        if (shared != other.shared) return false

        if (!shared && nodeType != other.nodeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + shared.hashCode()
        result = 31 * result + nodeType.hashCode()
        return result
    }

    override fun toString(): String {
        return "FieldDefinition(name='$name', shared=$shared, nodeType='$nodeType')"
    }
}

data class FieldType(
    val dataType: KType,
    val collection: Boolean = false,
    val distinct: Boolean = false,
    val firstParameterType: KType? = null
) {
    val indexType: KType get() = firstParameterType ?: dataType
}

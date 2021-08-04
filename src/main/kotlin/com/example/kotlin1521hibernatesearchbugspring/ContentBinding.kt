package com.example.kotlin1521hibernatesearchbugspring

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.hibernate.search.engine.backend.document.DocumentElement
import org.hibernate.search.engine.backend.document.IndexFieldReference
import org.hibernate.search.engine.backend.types.Aggregable
import org.hibernate.search.engine.backend.types.Norms
import org.hibernate.search.engine.backend.types.Projectable
import org.hibernate.search.engine.backend.types.Searchable
import org.hibernate.search.engine.backend.types.Sortable
import org.hibernate.search.engine.backend.types.TermVector
import org.hibernate.search.engine.environment.bean.BeanReference
import org.hibernate.search.mapper.pojo.bridge.TypeBridge
import org.hibernate.search.mapper.pojo.bridge.binding.TypeBindingContext
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.TypeBinderRef
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.TypeBinder
import org.hibernate.search.mapper.pojo.bridge.runtime.TypeBridgeWriteContext
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.TypeBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

val objectMapper: ObjectMapper = ObjectMapper()
    .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
    .registerModule(Jdk8Module())
    .registerModule(KotlinModule())
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)

@Retention
@Target(CLASS)
@MustBeDocumented
@TypeBinding(binder = TypeBinderRef(type = ContentBinder::class))
annotation class ContentBinding

class ContentBinder : TypeBinder {
    override fun bind(context: TypeBindingContext) {
        context.dependencies()
            .use("content")

        val indexConfiguration =
            context.beanResolver().resolve(BeanReference.of(IndexationDefinition::class.java)).get()
        val fieldReferenceProvider = FieldReferenceProvider(context, indexConfiguration)
        val transformerProvider =
            context.beanResolver().resolve(BeanReference.of(TransformerProvider::class.java)).get()
        val contentMappingProcessor = ContentMappingProcessor(transformerProvider)
        val contentAppender = ContentAppender(fieldReferenceProvider, contentMappingProcessor, indexConfiguration)

        context.bridge(Node::class.java, ContentBridge(contentAppender))
    }
}

class ContentBridge(private val contentAppender: ContentAppender) : TypeBridge<Node> {
    override fun write(
        target: DocumentElement,
        bridgedElement: Node,
        context: TypeBridgeWriteContext
    ) = contentAppender.append(
        toDocument = target,
        fromNode = bridgedElement
    )
}

@Suppress("NestedBlockDepth")
class ContentAppender(
    private val fieldReferenceProvider: FieldReferenceProvider,
    private val contentMappingProcessor: ContentMappingProcessor,
    private val indexDefinition: IndexationDefinition
) {
    fun append(fromNode: Node, toDocument: DocumentElement) {
        val fields = indexDefinition.getFieldDefinitions(fromNode.type)

        toDocument.addValue(fieldReferenceProvider.plainContentField, fromNode.content)

        if (fields.isEmpty()) {
            return
        }
        val fieldValues = associateFieldsWithValue(fields, fromNode)

        fieldValues.forEach { entry ->
            if (entry.value != null) {
                val fieldReference = fieldReferenceProvider.fieldReference(entry.key)
                if (entry.key.type.collection) {
                    var value = entry.value as Collection<*>
                    if (entry.key.type.distinct) {
                        value = (entry.value as Collection<*>).distinct()
                    }

                    value.forEach {
                        toDocument.addValue(fieldReference, it)
                    }
                } else {
                    toDocument.addValue(fieldReference, entry.value)
                }
            }
        }
    }

    private fun associateFieldsWithValue(fieldDefinitions: List<FieldDefinition>, node: Node) =
        fieldDefinitions.associateWith { fieldDefinition ->
            contentMappingProcessor.process(fieldDefinition, node)
        }
}

class FieldReferenceProvider(private val context: TypeBindingContext, indexDefinition: IndexationDefinition) {
    val plainContentField: IndexFieldReference<String> = context
        .indexSchemaElement()
        .field<String>("content") { factory ->
            factory
                .asString()
                .analyzer(null)
                .searchAnalyzer(null)
                .norms(Norms.NO)
                .termVector(TermVector.NO)
                .projectable(Projectable.NO)
                .sortable(Sortable.NO)
                .searchable(Searchable.NO)
                .aggregable(Aggregable.NO)
        }
        .toReference()

    private val indexFieldReferenceByFieldName: Map<FieldDefinition, IndexFieldReference<Any>>

    init {

        val indexedFields = hashMapOf<String, IndexFieldReference<Any>>()

        indexFieldReferenceByFieldName = indexDefinition
            .fields.associateWith {
                if (!indexedFields.containsKey(it.indexFieldName)) {
                    indexedFields[it.indexFieldName] = indexSchemaFieldOptionsStep(it)
                }

                indexedFields[it.indexFieldName]!!
            }
    }

    fun fieldReference(fieldDefinition: FieldDefinition) = indexFieldReferenceByFieldName[fieldDefinition]

    private fun indexSchemaFieldOptionsStep(fieldDefinition: FieldDefinition) =
        context
            .indexSchemaElement()
            .field(fieldDefinition.indexFieldName, fieldDefinition.buildIndexDefinition(context.typeFactory()))
            .let { if (fieldDefinition.type.collection) it.multiValued() else it }
            .toReference() as IndexFieldReference<Any>
}

@Bean
@Lazy
fun transformerProvider() = TransformerProvider()

class ContentMappingProcessor(private val transformerProvider: TransformerProvider) {
    fun process(fieldDefinition: FieldDefinition, node: Node): Any? {
        val jsonContent = objectMapper.readTree(node.content)
        val transformerContext = IndexTransformerContext(fieldDefinition, jsonContent, node)
        val transformer = transformerProvider.retrieve(fieldDefinition.valueProviderContext.transformer)

        return transformer.transform(transformerContext)
    }
}

class TransformerProvider {
    private val cachedTransformerInstances: MutableMap<KClass<out FieldTransformer>, FieldTransformer> = mutableMapOf()

    fun retrieve(transformerKClass: KClass<out FieldTransformer>): FieldTransformer {
        if (cachedTransformerInstances[transformerKClass] == null) {
            cachedTransformerInstances[transformerKClass] = transformerKClass.createInstance()
        }

        return cachedTransformerInstances[transformerKClass]!!
    }
}

package com.example.kotlin1521hibernatesearchbugspring

import com.fasterxml.jackson.databind.JsonNode

data class IndexTransformerContext(
    val fieldDefinition: FieldDefinition,
    val content: JsonNode,
    val node: Node
) : IndexValueProviderContext by fieldDefinition.valueProviderContext, IFieldDefinition by fieldDefinition

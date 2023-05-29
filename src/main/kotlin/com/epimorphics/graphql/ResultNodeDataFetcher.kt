package com.epimorphics.graphql

import graphql.schema.*

/**
 * Implementation of the graphql-java [DataFetcher] interface which extracts values from [ResultNode]s.
 * This implementation enables the graphql-java framework to render a [ResultNode] tree of any depth.
 */
class ResultNodeDataFetcher: DataFetcher<Any?> {

    /**
     * Extract values from a source [ResultNode] as required by the [DataFetcher] interface.
     * Return object values in the form of nested [ResultNode]s.
     * Otherwise, return scalar values as raw Java objects.
     *
     * This implementation uses the field definition provided by the [DataFetchingEnvironment] to determine the multiplicity and optionality of result values.
     */
    override fun get(env: DataFetchingEnvironment): Any? {
        val source = env.getSource<ResultNode>()
        val field = env.fieldDefinition
        val valueNodes = source.get(field.name)

        return formatValues(field.type, valueNodes)
    }

    private fun formatValues(type: GraphQLType, nodes: List<ResultNode>): Any? {
        val nullableType = GraphQLTypeUtil.unwrapNonNull(type)
        return if (nullableType is GraphQLList) {
            val singularType = GraphQLTypeUtil.unwrapNonNull(nullableType.wrappedType)
            nodes.map { node ->
                formatSingleValue(singularType, node)
            }
        } else {
            formatSingleValue(nullableType, nodes)
        }
    }

    private fun formatSingleValue(type: GraphQLType, node: ResultNode): Any? {
        return if (type is GraphQLImplementingType) {
            // The output type is an object type, so just return the result node itself.
            // Graphql-java will invoke this DataFetcher again with this node as the new source (root) object.
            node
        } else {
            // The output type is a scalar, so return the raw value.
            node.rawValue
        }
    }

    private fun formatSingleValue(type: GraphQLType, nodes: List<ResultNode>): Any? {
        // Check that there are the correct number of values in the result.
        if (nodes.size > 1) {
            println("Warning: Multiple values found on single-valued field - including only the first value!")
        }
        // Obtain the first node, if it exists.
        val node = nodes.firstOrNull()
        return if (node != null) {
            // Format the given node.
            formatSingleValue(type, node)
        } else {
            null
        }
    }
}
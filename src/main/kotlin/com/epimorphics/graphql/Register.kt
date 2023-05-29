package com.epimorphics.graphql

import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import org.apache.jena.rdfconnection.RDFConnection
import java.util.function.Supplier

fun register(schema: GraphQLSchema, registry: GraphQLCodeRegistry.Builder) {
    // Register the query data fetcher.
    val connector = databaseConnector()
    val queryDataFetcher = GraphQueryDataFetcher(connector)
    registry.dataFetcher(FieldCoordinates.coordinates(schema.queryType, "BookList"), queryDataFetcher)

    // Register the result node data fetcher.
    val nodeDataFetcher = ResultNodeDataFetcher()
    schema.additionalTypes.forEach { type ->
        if (type is GraphQLObjectType) {
            type.fieldDefinitions.forEach { field ->
                registry.dataFetcher(FieldCoordinates.coordinates(type, field), nodeDataFetcher)
            }
        }
    }
}

fun databaseConnector(): Supplier<RDFConnection> {
    //  Instantiate a connector for the RDF graph database.
    TODO()
}
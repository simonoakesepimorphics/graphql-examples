package com.epimorphics.graphql

import graphql.schema.*
import org.apache.jena.query.ResultSet
import org.apache.jena.rdfconnection.RDFConnection
import java.util.function.Supplier

/**
 * Implementation of the graphql-java [DataFetcher] interface which builds and performs a query against the graph database.
 * The database query obtains all of the root objects, related objects and their expansions as required by the incoming GraphQL query.
 * Query results are returned as [ResultNode]s, which are then accessed by [ResultNodeDataFetcher].
 * @param connector A supplier of connections to the graph database.
 */
class GraphQueryDataFetcher(
    private val connector: Supplier<RDFConnection>
): DataFetcher<List<ResultNode>> {

    override fun get(env: DataFetchingEnvironment): List<ResultNode> {
        // Obtain the GraphQL query selection and initialize the query builder.
        val selectionSet = env.field.selectionSet
        val queryBuilder = SparqlQueryBuilder("Book")

        // Build the query for the entire GraphQL selection.
        GraphQuerySelector(env).select(selectionSet, queryBuilder)

        // Perform the query and return the tree(s) of results in ResultNode format.
        return connector.get().use { connection ->
            val query = queryBuilder.build()
            val rawResults = connection.query(query).execSelect()

            resultNodes(rawResults)
        }
    }

    private fun resultNodes(results: ResultSet): List<ResultNode> {
        // Transform the query results into a list of ResultNodes which represent the root objects in the result tree.
        // How you coalesce the query results into query tree nodes depends on your query builder implementation.
        TODO()
    }
}
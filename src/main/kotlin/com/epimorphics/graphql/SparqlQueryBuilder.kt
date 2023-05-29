package com.epimorphics.graphql

import org.apache.jena.query.Query

/**
 * This is a stubbed placeholder for a query builder which generates a SPARQL query.
 * @param type The type of resource to query.
 */
class SparqlQueryBuilder(
    type: String
): GraphQueryBuilder {
    override fun select(field: String, build: (GraphQueryBuilder) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun select(field: String) {
        TODO("Not yet implemented")
    }

    fun build(): Query {
        TODO("Not yet implemented")
    }
}
package com.epimorphics.graphql

import graphql.language.Field
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.SelectionSet
import graphql.schema.DataFetchingEnvironment

/**
 * This class is responsible for building up a query against your graph database based on the selection in a GraphQL query.
 * All representations of GraphQL objects are provided by the graphql-java library.
 * @param env The data fetching environment obtained from a graphql-java [DataFetcher.get] implementation.
 */
class GraphQuerySelector(
    private val env: DataFetchingEnvironment
) {
    /**
     * Take the given [selectionSet], and add the corresponding fields to the given [queryBuilder].
     * @param selectionSet A generic GraphQL query selection set containing one or more fields or fragments (defined by graphql-java).
     * @param queryBuilder A query building interface that builds queries against your graph database (eg. a SPARQL query builder).
     */
    fun select(selectionSet: SelectionSet, queryBuilder: GraphQueryBuilder) {
        selectionSet.selections.forEach { selection ->
            when (selection) {
                is Field -> selectField(selection, queryBuilder)
                is InlineFragment -> selectInlineFragment(selection, queryBuilder)
                is FragmentSpread -> selectExternalFragment(selection, queryBuilder)
                else -> { /* Do nothing - there are no other selection types that we know about! */ }
            }
        }
    }

    private fun selectField(field: Field, queryBuilder: GraphQueryBuilder) {
        val nestedSelection = field.selectionSet
        if (nestedSelection != null) {
            // Field is object-valued
            queryBuilder.select(field.name) { nestedQueryBuilder ->
                // Repeat the selection process for the nested selection.
                select(nestedSelection, nestedQueryBuilder)
            }
        } else {
            // Field is scalar-valued
            queryBuilder.select(field.name)
        }
    }

    private fun selectInlineFragment(fragment: InlineFragment, queryBuilder: GraphQueryBuilder) {
        // Obtain the contents of the fragment repeat the selection process.
        select(fragment.selectionSet, queryBuilder)
    }

    private fun selectExternalFragment(fragment: FragmentSpread, queryBuilder: GraphQueryBuilder) {
        // Look up the fragment definition
        val fragmentDefinition = env.fragmentsByName[fragment.name]
        if (fragmentDefinition != null) {
            // Obtain the contents of the fragment repeat the selection process.
            select(fragmentDefinition.selectionSet, queryBuilder)
        }
    }
}
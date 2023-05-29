package com.epimorphics.graphql

/**
 * A generic query builder for queries against a graph database.
 */
interface GraphQueryBuilder {
    /**
     * Select the given field, then build a nested query over the values of that field.
     * @param field The unique name of the field to select.
     * @param build A function which builds the nested query.
     */
    fun select(field: String, build: (GraphQueryBuilder) -> Unit)

    /**
     * Select the given field.
     * @param field The unique name of the field to select.
     */
    fun select(field: String)
}
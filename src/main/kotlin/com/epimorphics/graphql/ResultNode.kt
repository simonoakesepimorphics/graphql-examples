package com.epimorphics.graphql

/**
 * A node in a result tree queried from a graph.
 * A node can represent either an object or a scalar node.
 */
interface ResultNode {
    /**
     * If this node represents a scalar value, the raw value in a graphq-java compatible format (eg. [String], [Int]).
     */
    val rawValue: Any?

    /**
     * Obtain a list of nodes that are related to this node by the given field.
     * @param field The uniquely name of the field to obtain values for.
     * @return A list of values of the given field, represented as [ResultNode]s.
     */
    fun get(field: String): List<ResultNode>
}
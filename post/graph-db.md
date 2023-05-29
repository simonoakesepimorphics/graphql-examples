# Implementing a GraphQL API for a Graph Database

GraphQL is a useful standard for developing intuitive and versatile APIs.
Behind every GraphQL API is an implementation which answers the incoming queries,
often by retrieving data from databases and other back-end resources.

Many tutorials and documents on this topic focus on implementations which pull their data from traditional tabular databases.
In this article, we will walk through a generic GraphQL API implementation written in [Kotlin](https://kotlinlang.org/)
which uses an RDF graph database (such as [Fuseki](https://jena.apache.org/documentation/fuseki2/))
as its underlying data source.

The [SPARQL](https://www.w3.org/TR/rdf-sparql-query/) query language enables us to retrieve RDF data for all of the nodes in a GraphQL query tree in a single database query.
In order to achieve this optimisation, our GraphQL API implementation must build the entire SPARQL query up front.
As such, we must scan the selection of fields in the incoming GraphQL query to determine which property paths we must select in our SPARQL query.
This article is not concerned with the construction of the SPARQL query itself,
but rather with the process of deconstructing GraphQL expressions.

We will utilise the open source [graphql-java](https://www.graphql-java.com/documentation/getting-started/) library to this end.
This library takes care of parsing and orchestrating the execution of incoming GraphQL queries. It also permits us to explore and deconstruct those queries in our implementation.

This article includes: cooking up an example GraphQL schema, defining some useful Kotlin / Java interfaces,
writing two `graphql-java` data fetchers, and finally wiring them all into GraphQL API.
Although this article specifically references RDF and SPARQL technologies,
the design and concepts can easily be adapted to other graph databases.

All code examples are written in Kotlin and are available in this
[Github repo](https://github.com/simonoakesepimorphics/graphql-examples).
If you prefer to read them in Java, see [this document](https://www.baeldung.com/kotlin/convert-to-java) for how to convert Kotlin code into Java.

## Prerequisites

In order to get the most out of this article, you will need to be familiar with the following:
* [GraphQL Basics](https://graphql.org/learn/)
* [Graphql-java](https://www.graphql-java.com/documentation/getting-started/) and [Data Fetchers](https://www.graphql-java.com/documentation/data-fetching/)
* Java or [Kotlin](https://kotlinlang.org/) programming language.

## Example Schema

Before we can start writing Kotlin code, we must first look at the GraphQL schema which defines our API.
We will use an example of an API which lets the user query lists of descriptions of books that are stored in our database.

```graphqls
schema {
    query: Query
}

type Query {
    BookList: [Book!]!
}

type Book {
    isbn: String!
    title: String!
    pages: Int!
    description: String
    author: Person!
    reviews: [Review!]!
}

type Person {
    name: String!
    email: String
    associates: [Person!]!
}

type Review {
    rating: Int!
    timestamp: String!
    comments: [String!]!
}
```

This example doesn't support more complex schema features like interface and union types.
In practice, we can expand our implementation to support those features,
but we will stick with the basics for now.

Since we are using a Fuseki RDF database, each object type represents a type of RDF resource, and each field represents a RDF property.
In order to build the required SPARQL queries, must have a way of mapping GraphQL fields to RDF property URIs.
For example, we could provide an external data model, or annotate our GraphQL schema
with directives containing property information.
For simplicity, this article will not go into the specifics of how this mapping works.

## Wiring up the API

When using the `graphql-java`, library, we adapt our API implementation to the `DataFetcher` interface,
and use the `GraphQLCodeRegistry` class to wire our data fetchers into each of the output fields of our GraphQL API.

We need to register two `DataFetcher` implementations, which will cover all output fields in our GraphQL schema:
* We register a "query" data fetcher on the `BookList` query field.
    * This data fetcher builds and performs the entire SPARQL query as required by the field selection in the incoming GraphQL query.
    * This data fetcher returns results as a list of "result nodes", where each node represents a `Book` resource.
* We register a "node" data fetcher on all other output fields on all object types, such as `title`, `name`, `comments`, etc.
    * This data fetcher accepts a "result node" (see above) as its data source.
    * On object-valued fields, this data fetcher returns the related result node(s) which represent the resource values of the field.
    * On scalar-valued fields, this data fetcher returns the raw value(s) of the fields.

The following sections will walk through the design and implementation of those data fetchers.

## Writing a Query Data Fetcher

As described above, we will write a query data fetcher which builds and executes a SPARQL query based on the fields and
related objects requested by an incoming GraphQL query.
To assist with this, we define a generic `GraphQueryBuilder` interface, as follows:

```kotlin
/**
 * A generic query builder for queries against a graph database.
 */
interface GraphQueryBuilder {
    /**
     * Select the given field.
     * @param field The unique name of the field to select.
     */
    fun select(field: String)

    /**
     * Select the given field, then build a nested query over the values of that field.
     * @param field The unique name of the field to select.
     * @param build A function which builds the nested query.
     */
    fun select(field: String, build: (GraphQueryBuilder) -> Unit)
}
```

This can be adapted for other graph databases and query languages.

Then, we write a class, `GraphQuerySelector`, which is responsible for adding the output field selections in the
GraphQL query to the graph query builder.
This class must support three types of selection, as defined in the GraphQL standard;
[fields](https://graphql.org/learn/queries/#fields),
[inline fragments](https://graphql.org/learn/queries/#inline-fragments)
and [external fragments](https://graphql.org/learn/queries/#fragments).
Our class has the following outline:

```kotlin
/**
 * This class is responsible for building up a query against the graph database based on the selection in a GraphQL query.
 * All representations of GraphQL query elements are provided by the graphql-java library.
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
        // TODO
    }

    private fun selectInlineFragment(fragment: InlineFragment, queryBuilder: GraphQueryBuilder) {
        // TODO
    }

    private fun selectExternalFragment(fragment: FragmentSpread, queryBuilder: GraphQueryBuilder) {
        // TODO
    }
}
```

### Selecting Fields

Our selector implementation must perform the following procedure for output fields:

For each selected field:
* Select the field in the SPARQL query.
* If the field is object-valued:
    * Obtain the nested selection on that field in the GraphQL query.
    * Repeat this process for the nested selection of fields.
* If the field is scalar-valued, just move on.

This `GraphQuerySelector` method implements this procedure:

```kotlin
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
```

### Selecting Fragments

Then, we can support more complex GraphQL structures like fragments.
In GraphQL, fragments are either [inline](https://graphql.org/learn/queries/#inline-fragments) or [external](https://graphql.org/learn/queries/#fragments).
We can use `graphql-java` features to detect and deconstruct fragments of both types.

For each fragment:
* If the fragment is inline:
    * Obtain the list of selections (fields and nested fragments) in the fragment.
* Otherwise, if the fragment is external:
    * Look up the fragment definition from the data fetching environment (provided by `graphql-java`).
    * Obtain the list of selections (fields and nested fragments) in the fragment.
* For each selection in the fragment, repeat this process.

These `GraphQuerySelector` functions implement this procedure:

```kotlin
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
```

See the resulting Kotlin class [here](https://github.com/simonoakesepimorphics/graphql-examples/blob/main/src/main/kotlin/com/epimorphics/graphql/GraphQuerySelector.kt).

### Handling the Results

Now that we have converted the incoming GraphQL query into a query against our graph database,
our data fetcher must run the query and obtain a set of results from the database.
We need to treat the result set as a "tree" of result "nodes" which reflect the tree-like structure of the GraphQL response we want to send
(note that GraphQL responses must be JSON-serialisable).

The process of converting the raw result set into a tree-like structure depends on the
specifics of the database query, which we will not cover in this article.
In general, a result tree must be made up of nodes which satisfy the following requirements:

* If the node represents an object value:
    * We can obtain the values of each selected field as a list of nested nodes.
* If the node represents a scalar value:
    * We can obtain the raw scalar value as a runtime object that is compatible with `graphql-java`'s result rendering (eg. `String`, `Int`).

We define a Kotlin interface which satisfies these requirements as follows,
and will refer to it in the rest of the implementation:

```kotlin
/**
 * A node in a result tree queried from a graph database.
 * A node can represent either an object or a scalar value.
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
```

Finally, [this Kotlin class](https://github.com/simonoakesepimorphics/graphql-examples/blob/main/src/main/kotlin/com/epimorphics/graphql/GraphQueryDataFetcher.kt)
pulls together the previous sections into a complete `DataFetcher` implementation.
The details of the SPARQL query builder, and conversion of raw results into result nodes, are stubbed because they are not directly relevant
to this article - in practice, we would fill in these implementations according to the design of our SPARQL query and graph database.

## Writing a Node Data Fetcher

As described in the [wiring up the API](#wiring-up-the-api) section,
we will write a second data fetcher,
which extracts data from the tree of result nodes that the first data fetcher acquired from the graph database.
The "source" nodes for this data fetcher are the same values that are returned by the first data fetcher,
i.e. instances of `ResultNode`.

We will implement a result node `DataFetcher` which performs the following procedure for a given GraphQL output field:

* Obtain the `ResultNode` which is the "source" of the data to be fetched.
* Obtain the set of `ResultNode`s which represent the values of the field on the source node.
* If the output field is non-nullable, find out the underlying output type.
* If the output field has a list type, find out the underlying singular output type.
* If the underlying output type is a scalar type:
    * Return the raw value(s) of the value node(s).
* If the underlying output type is an object type:
    * Return the value node(s) as they are.
    * The `graphql-java` framework will invoke the same `DataFetcher` again with each of these nodes as the new "source".

[This Kotlin class](https://github.com/simonoakesepimorphics/graphql-examples/blob/main/src/main/kotlin/com/epimorphics/graphql/ResultNodeDataFetcher.kt)
implements this procedure.

Finally, we simply wire both data fetchers into the `GraphQLCodeRegistry` for our API as follows:

```kotlin
fun register(schema: GraphQLSchema, registry: GraphQLCodeRegistry.Builder) {
    // Register the query data fetcher.
    val connector = databaseConnector() //  Instantiate a connector for the RDF graph database.
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
```

The `GraphQLSchema` parameter is the `graphql-java` representation of our schema.

## Conclusion

In this article, we have designed, written and wired up two data fetchers
which optimise our GraphQL API implementation for querying an RDF graph database.
The conceptual interfaces and some implementation details can be generalised to
other databases and other `graph-java` based applications.
As we have shown, the `graphql-java` library provides many useful facilities for analysing and deconstructing GraphQL queries.

We abbreviated the API implementation by working with a simplified schema example,
however the data fetchers described here can be incrementally enhanced to support
more advanced use cases like inheritance, union types, and even filter arguments,
which may be covered in a future article.
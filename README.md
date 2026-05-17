# MapPath

A JSON path utility library that applies JSON path querying to typed Kotlin data structures. This library works with `Map<String, *>` where
`*` could be a `String`, `Number`, `Boolean`, `null`, or another nested `Map`.

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
  implementation("io.github.yesitskev:mappath:1.1.0")
}
```

## Overview

MapPath provides a fluent Kotlin DSL for querying and manipulating JSON-like data structures in a type-safe manner. Instead of working with
raw JSON strings, this library operates directly on Kotlin's Map-based representations, making it ideal for working with deserialized JSON
data.

## JSON Path Grammar Support

| Supported | Keyword            | Example                              | Description                               |
|-----------|--------------------|--------------------------------------|-------------------------------------------|
| ✓         | `$`                | `$.store.book[0]`                    | Root node                                 |
| ✓         | `@`                | `[?(@.price < 10)]`                  | Current node (used in filter expressions) |
| ✓         | `.`                | `$.store.bicycle.color`              | Dot notation for child access             |
| ✓         | `[]`               | `$['store']['bicycle']`              | Bracket notation for child access         |
| ✓         | `*`                | `$.store.book[*].title`              | Wildcard (all elements)                   |
| ✓         | `..`               | `$..price`                           | Recursive descent (deep scan)             |
| ✓         | `[n]`              | `$.store.book[0]`                    | Array index access                        |
| ✓         | `[-n]`             | `$.store.book[-1]`                   | Negative array index (from end)           |
| ✓         | `[start:end]`      | `$.numbers[0:3]`                     | Array slice                               |
| ✓         | `[start:end:step]` | `$.numbers[0:10:2]`                  | Array slice with step                     |
| ✓         | `[::-1]`           | `$.numbers[::-1]`                    | Reverse array (negative step)             |
| ✓         | `[,]`              | `$.book[0,2].title`                  | Union operator (multiple indices)         |
| ✓         | `['a','b']`        | `$.bicycle['color','price']`         | Union operator (property names)           |
| ✓         | `[*]`              | `$.store.book[*]`                    | Array wildcard (all elements)             |
| ✓         | `[?(expr)]`        | `$.book[?(@.price < 10)]`            | Filter expression                         |
| ✓         | `==`               | `[?(@.category == 'fiction')]`       | Equality comparison                       |
| ✓         | `!=`               | `[?(@.category != 'fiction')]`       | Inequality comparison                     |
| ✓         | `<`                | `[?(@.price < 10)]`                  | Less than                                 |
| ✓         | `<=`               | `[?(@.price <= 8.99)]`               | Less than or equal                        |
| ✓         | `>`                | `[?(@.price > 20)]`                  | Greater than                              |
| ✓         | `>=`               | `[?(@.price >= 12.99)]`              | Greater than or equal                     |
| ✓         | `=~`               | `[?(@.title =~ /.*Lord.*/i)]`        | Regular expression match                  |
| ✓         | `in`               | `[?('red' in @.tags)]`               | Check if value is in array                |
| ✓         | `nin`              | `[?('red' nin @.tags)]`              | Check if value is not in array            |
| ✗         | `subsetof`         | `[?(@.tags subsetof @.all)]`         | Check if array is subset of another       |
| ✗         | `anyof`            | `[?(@.tags anyof @.required)]`       | Check if any element matches              |
| ✗         | `noneof`           | `[?(@.tags noneof @.banned)]`        | Check if no element matches               |
| ✓         | `size`             | `[?(@.tags.size == 3)]`              | Array/string size property                |
| ✓         | `empty`            | `[?(@.tags.empty)]`                  | Check if array/string is empty            |
| ✓         | `&&`               | `[?(@.price > 10 && @.price < 20)]`  | Logical AND                               |
| ✓         | `\|\|`             | `[?(@.price < 9 \|\| @.price > 20)]` | Logical OR                                |
| ✓         | `!`                | `[?(!(@.price > 10))]`               | Logical NOT                               |
| ✓         | `min()`            | `$.numbers.min()`                    | Minimum value from array                  |
| ✓         | `max()`            | `$.numbers.max()`                    | Maximum value from array                  |
| ✓         | `avg()`            | `$.numbers.avg()`                    | Average value from array                  |
| ✓         | `sum()`            | `$.numbers.sum()`                    | Sum of values in array                    |
| ✓         | `length()`         | `$.store.book.length()`              | Length of array or string                 |
| ✓         | `keys()`           | `$.store.keys()`                     | Object keys as array                      |
| ✓         | `concat()`         | `$.arrays.concat()`                  | Concatenate arrays or strings             |

## API Usage Examples

The API is a set of extension functions on `Map<String, Any>` (read operations) and `MutableMap<String, Any>` (modification operations). Import the functions you need:

```kotlin
import io.github.yesitskev.mappath.query
import io.github.yesitskev.mappath.queryOrNull
import io.github.yesitskev.mappath.queryOrDefault
import io.github.yesitskev.mappath.queryAsResult
import io.github.yesitskev.mappath.containsPath
import io.github.yesitskev.mappath.set
import io.github.yesitskev.mappath.add
import io.github.yesitskev.mappath.delete
```

### Read Operations

| Function          | Behavior on miss / type mismatch                                |
|-------------------|-----------------------------------------------------------------|
| `query<T>`        | Throws `NoSuchElementException` / `IllegalArgumentException`    |
| `queryOrNull<T>`  | Returns `null`                                                  |
| `queryOrDefault<T>` | Returns the supplied default                                  |
| `queryAsResult<T>` | Returns `Result.failure` wrapping the exception                |
| `containsPath`    | Returns `Boolean` — distinguishes "key absent" from "value null" |

`query` and its variants take a reified type parameter. Use `query<Any>(path)` when the value type is genuinely unknown.

### Basic Path Queries

```kotlin
// Sample data structure
val data = mapOf(
  "store" to mapOf(
    "book" to listOf(
      mapOf(
        "category" to "reference",
        "author" to "Nigel Rees",
        "title" to "Sayings of the Century",
        "price" to 8.95,
        "isbn" to "0-553-21311-3"
      ),
      mapOf(
        "category" to "fiction",
        "author" to "Evelyn Waugh",
        "title" to "Sword of Honour",
        "price" to 12.99,
        "isbn" to "0-553-21312-1"
      ),
      mapOf(
        "category" to "fiction",
        "author" to "Herman Melville",
        "title" to "Moby Dick",
        "price" to 8.99,
        "isbn" to "0-553-21311-3"
      ),
      mapOf(
        "category" to "fiction",
        "author" to "J. R. R. Tolkien",
        "title" to "The Lord of the Rings",
        "price" to 22.99,
        "isbn" to "0-395-19395-8"
      )
    ),
    "bicycle" to mapOf(
      "color" to "red",
      "price" to 19.95
    )
  )
)

// Root node access
val root = data.query<Map<String, Any>>("$")
// Result: entire data structure

// Dot notation
val color = data.query<String>("$.store.bicycle.color")
// Result: "red"

// Bracket notation
val sameColor = data.query<String>("$['store']['bicycle']['color']")
// Result: "red"

// Array index
val firstBook = data.query<String>("$.store.book[0].title")
// Result: "Sayings of the Century"

// Negative index (from end)
val lastBook = data.query<String>("$.store.book[-1].title")
// Result: "The Lord of the Rings"

// Wildcard
val allTitles = data.query<List<String>>("$.store.book[*].title")
// Result: ["Sayings of the Century", "Sword of Honour", "Moby Dick", "The Lord of the Rings"]

// Null-safe variants
val missing = data.queryOrNull<String>("$.store.missing")           // null
val fallback = data.queryOrDefault("$.store.missing", "unknown")    // "unknown"
val result = data.queryAsResult<String>("$.store.bicycle.color")    // Result.success("red")

// Existence check — distinguishes "key absent" from "value is null"
val hasColor = data.containsPath("$.store.bicycle.color")           // true
val hasMissing = data.containsPath("$.store.missing")               // false
```

### Array Operations

```kotlin
// Array slice
val firstTwo = data.query<List<String>>("$.store.book[0:2].title")
// Result: ["Sayings of the Century", "Sword of Honour"]

val numbers = mapOf("numbers" to listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))

// Slice with step
val everyOther = numbers.query<List<Int>>("$.numbers[0:10:2]")
// Result: [1, 3, 5, 7, 9]

// Reverse array
val reversed = numbers.query<List<Int>>("$.numbers[::-1]")
// Result: [10, 9, 8, 7, 6, 5, 4, 3, 2, 1]

// Union operator (multiple indices)
val specific = data.query<List<String>>("$.store.book[0,2].title")
// Result: ["Sayings of the Century", "Moby Dick"]

// Union with property names
val bikeProps = data.query<List<Any>>("$.store.bicycle['color','price']")
// Result: ["red", 19.95]
```

### Recursive Descent

```kotlin
// Find all prices at any depth
val allPrices = data.query<List<Double>>("$..price")
// Result: [8.95, 12.99, 8.99, 22.99, 19.95]

// Find all authors
val allAuthors = data.query<List<String>>("$..author")
// Result: ["Nigel Rees", "Evelyn Waugh", "Herman Melville", "J. R. R. Tolkien"]
```

### Filter Expressions

```kotlin
// Basic filter
val cheapBooks = data.query<List<String>>("$.store.book[?(@.price < 10)].title")
// Result: ["Sayings of the Century", "Moby Dick"]

// String equality
val fiction = data.query<List<String>>("$.store.book[?(@.category == 'fiction')].title")
// Result: ["Sword of Honour", "Moby Dick", "The Lord of the Rings"]

// Comparison operators
val expensive = data.query<List<String>>("$.store.book[?(@.price > 20)].title")
// Result: ["The Lord of the Rings"]

// Regex matching
val lordBooks = data.query<List<String>>("$.store.book[?(@.title =~ /.*Lord.*/i)].title")
// Result: ["The Lord of the Rings"]

// IN operator
val testData = mapOf(
  "items" to listOf(
    mapOf("category" to "A", "tags" to listOf("red", "blue")),
    mapOf("category" to "B", "tags" to listOf("green", "yellow"))
  )
)
val withRedTag = testData.query<List<String>>("$.items[?('red' in @.tags)].category")
// Result: ["A"]

// Logical AND
val midPriced = data.query<List<String>>("$.store.book[?(@.price > 10 && @.price < 20)].title")
// Result: ["Sword of Honour"]

// Logical OR
val extremePrices = data.query<List<String>>("$.store.book[?(@.price < 9 || @.price > 20)].title")
// Result: ["Sayings of the Century", "Moby Dick", "The Lord of the Rings"]

// Logical NOT
val notExpensive = data.query<List<String>>("$.store.book[?(!(@.price > 10))].title")
// Result: ["Sayings of the Century", "Moby Dick"]

// Complex expressions
val complexFilter = data.query<List<String>>("$.store.book[?((@.price < 10 || @.price > 20) && @.category == 'fiction')].title")
// Result: ["Moby Dick", "The Lord of the Rings"]
```

### Functions

```kotlin
val numbers = mapOf("numbers" to listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))

// Aggregation functions
val min = numbers.query<Number>("$.numbers.min()")
// Result: 1

val max = numbers.query<Number>("$.numbers.max()")
// Result: 10

val avg = numbers.query<Number>("$.numbers.avg()")
// Result: 5.5

val sum = numbers.query<Number>("$.numbers.sum()")
// Result: 55

// Length
val bookCount = data.query<Int>("$.store.book.length()")
// Result: 4

// Keys
val storeKeys = data.query<List<String>>("$.store.keys()")
// Result: ["book", "bicycle"]

// Concat
val arrays = mapOf(
  "arrays" to mapOf(
    "first" to listOf(1, 2, 3),
    "second" to listOf(4, 5, 6)
  )
)
val combined = arrays.query<List<Int>>("$.arrays.concat()")
// Result: [1, 2, 3, 4, 5, 6]
```

### Modification Operations

`set`, `add`, and `delete` are extension functions on `MutableMap<String, Any>`. They mutate the
receiver in place and return the *previous* value at the leaf (or `null` if there was none),
matching the contract of [`MutableMap.put`] / [`MutableMap.remove`]. Any nested `Map`/`List`
traversed must itself be mutable.

| Function | Auto-creates missing intermediates? | List indices                |
|----------|-------------------------------------|-----------------------------|
| `set`    | No — intermediates must already exist | Must be in bounds         |
| `add`    | Yes — missing intermediate maps are created | Must be in bounds (does not grow lists) |
| `delete` | No — intermediates must already exist | Must be in bounds (not a no-op) |

```kotlin
// Set - replaces an existing leaf value (intermediates must exist)
val testData = mutableMapOf<String, Any>("name" to "John", "age" to 30)
val previousName = testData.set("$.name", "Jane")
// previousName == "John"
// testData is now {"name": "Jane", "age": 30}

// Set nested property
val userData = mutableMapOf<String, Any>(
  "user" to mutableMapOf<String, Any>(
    "name" to "John",
    "address" to mutableMapOf<String, Any>(
      "city" to "Seattle",
      "zip" to "98101"
    )
  )
)
userData.set("$.user.address.city", "Portland")
// userData["user"]["address"]["city"] is now "Portland"

// Add - like set, but creates missing intermediate maps along the path
testData.add("$.email", "john@example.com")
// testData is now {"name": "Jane", "age": 30, "email": "john@example.com"}

val empty = mutableMapOf<String, Any>()
empty.add("$.level1.level2.level3", "deep value")
// empty is now {"level1": {"level2": {"level3": "deep value"}}}

// Delete - removes a leaf; returns the removed value or null if absent
val removedAge = testData.delete("$.age")
// removedAge == 30
// testData no longer contains "age"
```

[`MutableMap.put`]: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-map/put.html
[`MutableMap.remove`]: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-map/remove.html

## License

See the main project LICENSE file.

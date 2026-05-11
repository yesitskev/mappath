package io.github.yesitskev.mappath

import org.intellij.lang.annotations.Language

@PublishedApi
internal fun Map<String, Any>.evaluatePath(path: String): Any? = MapPathEvaluator.evaluate(path, this)

/**
 * Evaluates a JsonPath expression on this map and returns the matched value as `T`.
 *
 * Throws if the path does not resolve, resolves to `null`, or resolves to a value of the wrong
 * type. Use [queryOrNull] for `null`-on-miss semantics, or [queryAsResult] to get the outcome
 * as a [Result] without exception handling.
 *
 * Use `query<Any>(path)` when the value type is genuinely unknown.
 *
 * ### Supported syntax
 *
 * | Expression                    | Meaning                                                       |
 * |-------------------------------|---------------------------------------------------------------|
 * | `$`                           | Root of the document                                          |
 * | `.name` / `['name']`          | Child property (dot or bracket notation)                      |
 * | `..name`                      | Recursive descent — every `name` at any depth                 |
 * | `*`                           | Wildcard — every child of an object or array                  |
 * | `[n]` / `[-n]`                | Array index (negative counts from the end)                    |
 * | `[start:end]` / `[start:end:step]` | Array slice                                              |
 * | `[a,b,c]`                     | Union of indices or property names                            |
 * | `[?(...)]`                    | Filter expression evaluated against each array element        |
 *
 * Inside a filter, `@` refers to the current element. Supported operators:
 * `==`, `!=`, `<`, `<=`, `>`, `>=`, `&&`, `||`, `!(...)`, `in`, `nin`, `=~` (regex,
 * trailing `/i` for case-insensitive), plus `.size` and `.empty` on collections.
 *
 * ### Examples
 *
 * Given:
 * ```kotlin
 * val data = mapOf(
 *   "store" to mapOf(
 *     "book" to listOf(
 *       mapOf("category" to "fiction", "author" to "Tolkien", "price" to 22.99),
 *       mapOf("category" to "fiction", "author" to "Melville", "price" to 8.99),
 *       mapOf("category" to "reference", "author" to "Rees",   "price" to 8.95),
 *     ),
 *     "bicycle" to mapOf("color" to "red", "price" to 19.95),
 *   ),
 * )
 * ```
 *
 * ```kotlin
 * data.query<String>("$.store.bicycle.color")             // "red"
 * data.query<String>("$.store.book[0].author")            // "Tolkien"
 * data.query<String>("$.store.book[-1].author")           // "Rees"        (last element)
 * data.query<List<*>>("$.store.book[0:2]")                // first two books
 * data.query<List<*>>("$.store.book[*].author")           // ["Tolkien", "Melville", "Rees"]
 * data.query<List<*>>("$..price")                         // every price at any depth
 * data.query<List<*>>("$.store.book[?(@.price < 10)]")    // books cheaper than $10
 * data.query<List<*>>("$.store.book[?(@.category == 'fiction' && @.price > 10)]")
 * data.query<List<*>>("$.store.book[?(@.author =~ /tolk.*\/i)]") // regex, case-insensitive
 * data.query<List<*>>("$.store.book[?(@.category in ['fiction','reference'])]")
 * ```
 *
 * @param path The JsonPath expression to evaluate.
 * @return The matched value as `T`.
 * @throws NoSuchElementException If no value is found at [path].
 * @throws IllegalArgumentException If the value at [path] is not of type `T`.
 */
inline fun <reified T> Map<String, Any>.query(@Language("JsonPath") path: String): T {
  val raw = evaluatePath(path) ?: throw NoSuchElementException("No value at path: $path")
  require(raw is T) { "Expected result to be of type ${T::class.java}, but got ${raw::class.java}" }
  return raw
}

/**
 * Like [query] but returns `null` when the path does not resolve or the value is not of type `T`.
 *
 * See [query] for the full JsonPath syntax reference.
 *
 * ```kotlin
 * data.queryOrNull<String>("$.store.bicycle.color")    // "red"
 * data.queryOrNull<String>("$.store.missing")          // null
 * data.queryOrNull<Int>("$.store.bicycle.color")       // null (wrong type)
 * ```
 *
 * @param path The JsonPath expression to evaluate.
 * @return The matched value as `T`, or `null` if unresolved or of a different type.
 */
inline fun <reified T> Map<String, Any>.queryOrNull(@Language("JsonPath") path: String): T? =
  evaluatePath(path) as? T

/**
 * Like [query] but returns [default] when the path does not resolve or the value is not of type `T`.
 *
 * Useful when the caller has a sensible fallback and doesn't want to deal with `null` or exceptions.
 * Use [containsPath] if you specifically need to distinguish "key absent" from "key present with
 * a null value" — this function returns [default] for both.
 *
 * See [query] for the full JsonPath syntax reference.
 *
 * ```kotlin
 * data.queryOrDefault("$.store.bicycle.color", "unknown")    // "red"
 * data.queryOrDefault("$.store.missing", "unknown")          // "unknown"
 * data.queryOrDefault<Int>("$.store.bicycle.color", -1)      // -1 (wrong type)
 * ```
 *
 * @param path The JsonPath expression to evaluate.
 * @param default The value to return if the path does not resolve or the type does not match.
 * @return The matched value as `T`, or [default].
 */
inline fun <reified T> Map<String, Any>.queryOrDefault(@Language("JsonPath") path: String, default: T): T =
  queryOrNull<T>(path) ?: default

/**
 * Like [query] but wraps the outcome in a [Result] instead of throwing.
 *
 * Returns [Result.success] with a value of type `T`, or [Result.failure] holding a
 * [NoSuchElementException] (path did not resolve) or [IllegalArgumentException] (wrong type).
 *
 * See [query] for the full JsonPath syntax reference.
 *
 * ```kotlin
 * data.queryAsResult<String>("$.store.bicycle.color")
 *   .onSuccess { println(it) }
 *   .onFailure { println("missing: ${it.message}") }
 *
 * val price: Double = data.queryAsResult<Double>("$.store.book[0].price").getOrThrow()
 * ```
 *
 * @param path The JsonPath expression to evaluate.
 * @return [Result.success] with the matched value, or [Result.failure] if unresolved or mistyped.
 */
inline fun <reified T> Map<String, Any>.queryAsResult(@Language("JsonPath") path: String): Result<T> =
  runCatching { query<T>(path) }

/**
 * Sets the value at [path] in this map, mutating it in place. Behaves like [MutableMap.put]:
 * if the leaf key already exists its value is replaced; otherwise the key is added to its parent.
 *
 * Intermediate containers along [path] must already exist; use [add] if you want missing
 * intermediate maps to be created automatically. For array steps the index must be in bounds.
 * Any nested [Map]/[List] traversed must be mutable — passing an unmodifiable collection results
 * in an [UnsupportedOperationException] from the underlying `put`/`set`.
 *
 * @param path The JsonPath expression indicating the location to update.
 * @param value The value to store at [path].
 * @return The previous value at [path], or `null` if the leaf had no prior mapping.
 * @throws IllegalArgumentException If an intermediate path step is missing, or if a list index
 *   along [path] is out of bounds or not a number.
 */
fun MutableMap<String, Any>.set(@Language("JsonPath") path: String, value: Any): Any? =
  setMutating(this, path, value)

/**
 * Adds [value] at [path] in this map, mutating it in place. Behaves like [MutableMap.put] but
 * creates missing intermediate [MutableMap]s along [path] as needed.
 *
 * Auto-creation only fills in missing map intermediates. For array steps the index must already
 * be in bounds — `add` does not extend a list.
 *
 * @param path The JsonPath expression indicating where to store [value].
 * @param value The value to store.
 * @return The previous value at [path], or `null` if the leaf had no prior mapping.
 * @throws IllegalArgumentException If a list step is out of bounds or not a number.
 */
fun MutableMap<String, Any>.add(@Language("JsonPath") path: String, value: Any): Any? =
  addMutating(this, path, value)

/**
 * Removes the value at [path] from this map, mutating it in place. Behaves like
 * [MutableMap.remove]: if the leaf key is present it is removed and its previous value returned;
 * if absent the call is a no-op and returns `null`.
 *
 * Intermediate containers along [path] must exist. For array steps the index must be in bounds —
 * out-of-bounds is an error rather than a no-op (matches [MutableList.removeAt]).
 *
 * @param path The JsonPath expression indicating the value to remove.
 * @return The removed value, or `null` if the leaf key was not present on a map parent.
 * @throws IllegalArgumentException If an intermediate path step is missing, or a list index is out
 *   of bounds or not a number.
 */
fun MutableMap<String, Any>.delete(@Language("JsonPath") path: String): Any? =
  deleteMutating(this, path)

/**
 * Returns `true` if [path] is queryable on this map — that is, every navigation step resolves to a
 * value (which may itself be `null`).
 *
 * Unlike `queryOrNull(path) != null`, this distinguishes "key absent" from "key present with an
 * explicit `null` value": a property whose key exists in its parent map returns `true` even if the
 * value is `null`. A path that matches an empty collection (e.g. a filter with no matches) still
 * returns `true` because the collection itself was produced. Out-of-bounds array indices, missing
 * keys, and navigation through `null` all return `false`.
 *
 * See [query] for the full JsonPath syntax reference.
 *
 * ```kotlin
 * data.containsPath("$.store.bicycle.color")              // true
 * data.containsPath("$.store.missing")                    // false
 * data.containsPath("$.store.book[0]")                    // true
 * data.containsPath("$.store.book[10]")                   // false (out of bounds)
 * data.containsPath("$.store.book[?(@.price > 1000)]")    // true (empty list, not null)
 *
 * val withNull = mapOf("value" to null) as Map<String, Any>
 * withNull.containsPath("$.value")                        // true (key present, value null)
 * withNull.containsPath("$.missing")                      // false (key absent)
 * ```
 *
 * @param path The JsonPath expression to test.
 * @return `true` if every navigation step resolves; `false` if any step fails to find its target.
 */
fun Map<String, Any>.containsPath(@Language("JsonPath") path: String): Boolean =
  MapPathEvaluator.contains(path, this)

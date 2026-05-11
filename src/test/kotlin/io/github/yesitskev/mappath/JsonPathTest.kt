package io.github.yesitskev.mappath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Nested

class JsonPathTest {

  companion object {
    val sampleData = mapOf(
      "store" to mapOf(
        "book" to listOf(
          mapOf(
            "category" to "reference",
            "author" to "Nigel Rees",
            "title" to "Sayings of the Century",
            "price" to 8.95,
            "isbn" to "0-553-21311-3",
          ),
          mapOf(
            "category" to "fiction",
            "author" to "Evelyn Waugh",
            "title" to "Sword of Honour",
            "price" to 12.99,
            "isbn" to "0-553-21312-1",
          ),
          mapOf(
            "category" to "fiction",
            "author" to "Herman Melville",
            "title" to "Moby Dick",
            "price" to 8.99,
            "isbn" to "0-553-21311-3",
          ),
          mapOf(
            "category" to "fiction",
            "author" to "J. R. R. Tolkien",
            "title" to "The Lord of the Rings",
            "price" to 22.99,
            "isbn" to "0-395-19395-8",
          ),
        ),
        "bicycle" to mapOf(
          "color" to "red",
          "price" to 19.95,
        ),
      ),
      "warehouse" to mapOf(
        "location" to "Seattle",
        "inventory" to mapOf(
          "books" to 1500,
          "bicycles" to 50,
        ),
      ),
    )

    val arrayData = mapOf(
      "numbers" to listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
      "fruits" to listOf("apple", "banana", "cherry", "date", "elderberry"),
    )

    val nestedData = mapOf(
      "level1" to mapOf(
        "price" to 10.0,
        "level2" to mapOf(
          "price" to 20.0,
          "level3" to mapOf(
            "price" to 30.0,
            "name" to "deep",
          ),
        ),
        "items" to listOf(
          mapOf("price" to 5.0),
          mapOf("price" to 15.0),
        ),
      ),
    )
  }

  @Nested
  inner class RootOperatorTest {

    @Test
    fun `$ should access root node`() {
      val result = sampleData.query<Map<String, Any>>("$")
        assertEquals(sampleData, result)
    }

    @Test
    fun `$ should work as prefix for all queries`() {
      val result = sampleData.query<Map<String, Any>>("$.store")
        assertEquals(sampleData["store"], result)
    }
  }

  @Nested
  inner class DotNotationTest {
    @Test
    fun `dot notation should access child property`() {
      val result = sampleData.query<String>("$.store.bicycle.color")
        assertEquals("red", result)
    }

    @Test
    fun `dot notation should access nested properties`() {
      val result = sampleData.query<String>("$.warehouse.location")
        assertEquals("Seattle", result)
    }

    @Test
    fun `dot notation should return null for non-existent property`() {
      val result = sampleData.queryOrNull<Any>("$.store.nonexistent")
        assertNull(result)
    }
  }

  @Nested
  inner class BracketNotationTest {
    @Test
    fun `bracket notation should access child property`() {
      val result = sampleData.query<String>("$['store']['bicycle']['color']")
        assertEquals("red", result)
    }

    @Test
    fun `bracket notation should work with double quotes`() {
      val result = sampleData.query<String>("$[\"store\"][\"bicycle\"][\"color\"]")
        assertEquals("red", result)
    }

    @Test
    fun `bracket and dot notation should be interchangeable`() {
      val dotResult = sampleData.query<String>("$.store.bicycle.color")
      val bracketResult = sampleData.query<String>("$['store']['bicycle']['color']")
        assertEquals(dotResult, bracketResult)
    }
  }

  @Nested
  inner class WildcardOperatorTest {
    @Test
    fun `wildcard should return all elements in array`() {
      val result = sampleData.query<List<*>>("$.store.book[*].title")
        assertEquals(
            listOf(
                "Sayings of the Century",
                "Sword of Honour",
                "Moby Dick",
                "The Lord of the Rings",
            ),
            result,
        )
    }

    @Test
    fun `wildcard should return all properties in object`() {
      val result = sampleData.query<List<*>>("$.store.*")
        assertEquals(2, result.size)
    }
  }

  @Nested
  inner class RecursiveDescentTest {
    @Test
    fun `recursive descent should find all matching properties at any depth`() {
      val result = sampleData.query<List<*>>("$..price")
        assertEquals(
            listOf(8.95, 12.99, 8.99, 22.99, 19.95),
            result,
        )
    }

    @Test
    fun `recursive descent should find nested properties`() {
      val result = nestedData.query<List<*>>("$..price")
        assertEquals(
            listOf(10.0, 20.0, 30.0, 5.0, 15.0),
            result,
        )
    }

    @Test
    fun `recursive descent should work with specific property name`() {
      val result = sampleData.query<List<*>>("$..author")
        assertEquals(
            listOf("Nigel Rees", "Evelyn Waugh", "Herman Melville", "J. R. R. Tolkien"),
            result,
        )
    }
  }

  @Nested
  inner class ArrayIndexTest {
    @Test
    fun `array index should access specific element`() {
      val result = sampleData.query<String>("$.store.book[0].title")
        assertEquals("Sayings of the Century", result)
    }

    @Test
    fun `array index should access last element with positive index`() {
      val result = sampleData.query<String>("$.store.book[3].title")
        assertEquals("The Lord of the Rings", result)
    }

    @Test
    fun `array index should return null for out of bounds`() {
      val result = sampleData.queryOrNull<Any>("$.store.book[10].title")
        assertNull(result)
    }
  }

  @Nested
  inner class NegativeArrayIndexTest {
    @Test
    fun `negative index should access element from end`() {
      val result = sampleData.query<String>("$.store.book[-1].title")
        assertEquals("The Lord of the Rings", result)
    }

    @Test
    fun `negative index -2 should access second to last element`() {
      val result = sampleData.query<String>("$.store.book[-2].title")
        assertEquals("Moby Dick", result)
    }

    @Test
    fun `negative index should work with simple arrays`() {
      val result = arrayData.query<String>("$.fruits[-1]")
        assertEquals("elderberry", result)
    }
  }

  @Nested
  inner class ArraySliceTest {
    @Test
    fun `array slice should return range of elements`() {
      val result = sampleData.query<List<*>>("$.store.book[0:2].title")
        assertEquals(
            listOf("Sayings of the Century", "Sword of Honour"),
            result,
        )
    }

    @Test
    fun `array slice with start only should return from start to end`() {
      val result = arrayData.query<List<*>>("$.numbers[5:]")
        assertEquals(listOf(6, 7, 8, 9, 10), result)
    }

    @Test
    fun `array slice with end only should return from start to end`() {
      val result = arrayData.query<List<*>>("$.numbers[:3]")
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `array slice with negative indices should work`() {
      val result = arrayData.query<List<*>>("$.numbers[-3:-1]")
        assertEquals(listOf(8, 9), result)
    }
  }

  @Nested
  inner class ArraySliceWithStepTest {
    @Test
    fun `array slice with step should return every nth element`() {
      val result = arrayData.query<List<*>>("$.numbers[0:10:2]")
        assertEquals(listOf(1, 3, 5, 7, 9), result)
    }

    @Test
    fun `array slice with step 3 should return every third element`() {
      val result = arrayData.query<List<*>>("$.numbers[0:10:3]")
        assertEquals(listOf(1, 4, 7, 10), result)
    }

    @Test
    fun `array slice with negative step should return elements in reverse`() {
      val result = arrayData.query<List<*>>("$.numbers[::-1]")
        assertEquals(listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1), result)
    }
  }

  @Nested
  inner class UnionOperatorTest {
    @Test
    fun `union should return multiple indices`() {
      val result = sampleData.query<List<*>>("$.store.book[0,2].title")
        assertEquals(
            listOf("Sayings of the Century", "Moby Dick"),
            result,
        )
    }

    @Test
    fun `union should work with property names`() {
      val result = sampleData.query<List<*>>("$.store.bicycle['color','price']")
        assertEquals(listOf("red", 19.95), result)
    }

    @Test
    fun `union should work with mixed indices and ranges`() {
      val result = arrayData.query<List<*>>("$.numbers[0,2,4,6,8]")
        assertEquals(listOf(1, 3, 5, 7, 9), result)
    }
  }

  @Nested
  inner class FilterExpressionTest {
    @Test
    fun `filter should return elements matching condition`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.price < 10)]")
        assertEquals(2, result.size)
    }

    @Test
    fun `filter should work with string equality`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.category == 'fiction')].title")
        assertEquals(
            listOf("Sword of Honour", "Moby Dick", "The Lord of the Rings"),
            result,
        )
    }

    @Test
    fun `filter should support multiple conditions`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.price > 10 && @.category == 'fiction')].title")
        assertEquals(
            listOf("Sword of Honour", "The Lord of the Rings"),
            result,
        )
    }
  }

  @Nested
  inner class ComparisonOperatorsTest {
    @Test
    fun `equality operator should filter correctly`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.price == 8.95)].title")
        assertEquals(listOf("Sayings of the Century"), result)
    }

    @Test
    fun `inequality operator should filter correctly`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.category != 'fiction')].title")
        assertEquals(listOf("Sayings of the Century"), result)
    }

    @Test
    fun `less than operator should filter correctly`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.price < 10)].title")
        assertEquals(
            listOf("Sayings of the Century", "Moby Dick"),
            result,
        )
    }

    @Test
    fun `less than or equal operator should filter correctly`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.price <= 8.99)].title")
        assertEquals(
            listOf("Sayings of the Century", "Moby Dick"),
            result,
        )
    }

    @Test
    fun `greater than operator should filter correctly`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.price > 20)].title")
        assertEquals(listOf("The Lord of the Rings"), result)
    }

    @Test
    fun `greater than or equal operator should filter correctly`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.price >= 12.99)].title")
        assertEquals(
            listOf("Sword of Honour", "The Lord of the Rings"),
            result,
        )
    }
  }

  @Nested
  inner class RegexMatchTest {
    @Test
    fun `regex match should filter by pattern`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.title =~ /.*Lord.*/i)].title")
        assertEquals(listOf("The Lord of the Rings"), result)
    }

    @Test
    fun `regex match should be case insensitive with flag`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.author =~ /.*TOLKIEN.*/i)].title")
        assertEquals(listOf("The Lord of the Rings"), result)
    }
  }

  @Nested
  inner class SetOperatorsTest {
    @Test
    fun `in operator should check if value is in array`() {
      val testData = mapOf(
        "items" to listOf(
          mapOf("category" to "A", "tags" to listOf("red", "blue")),
          mapOf("category" to "B", "tags" to listOf("green", "yellow")),
        ),
      )
      val result = testData.query<List<*>>("$.items[?('red' in @.tags)].category")
        assertEquals(listOf("A"), result)
    }

    @Test
    fun `nin operator should check if value is not in array`() {
      val testData = mapOf(
        "items" to listOf(
          mapOf("category" to "A", "tags" to listOf("red", "blue")),
          mapOf("category" to "B", "tags" to listOf("green", "yellow")),
        ),
      )
      val result = testData.query<List<*>>("$.items[?('red' nin @.tags)].category")
        assertEquals(listOf("B"), result)
    }
  }

  @Nested
  inner class SizeAndEmptyTest {
    @Test
    fun `size should return array length in filter`() {
      val testData = mapOf(
        "items" to listOf(
          mapOf("tags" to listOf("a", "b", "c")),
          mapOf("tags" to listOf("x")),
        ),
      )
      val result = testData.query<List<*>>("$.items[?(@.tags.size == 3)]")
        assertEquals(1, result.size)
    }

    @Test
    fun `empty should check if array is empty`() {
      val testData = mapOf(
        "items" to listOf(
          mapOf("tags" to emptyList<String>()),
          mapOf("tags" to listOf("x")),
        ),
      )
      val result = testData.query<List<*>>("$.items[?(@.tags.empty)]")
        assertEquals(1, result.size)
    }
  }

  @Nested
  inner class LogicalAndTest {
    @Test
    fun `logical AND should combine conditions`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.price > 10 && @.price < 20)].title")
        assertEquals(
            listOf("Sword of Honour"),
            result,
        )
    }

    @Test
    fun `logical AND with three conditions`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.price > 8 && @.price < 15 && @.category == 'fiction')].title")
        assertEquals(
            listOf("Sword of Honour", "Moby Dick"),
            result,
        )
    }
  }

  @Nested
  inner class LogicalOrTest {
    @Test
    fun `logical OR should match either condition`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.price < 9 || @.price > 20)].title")
        assertEquals(
            listOf("Sayings of the Century", "Moby Dick", "The Lord of the Rings"),
            result,
        )
    }

    @Test
    fun `logical OR with category conditions`() {
      val result = sampleData.query<List<*>>("$.store.book[?(@.category == 'reference' || @.author == 'J. R. R. Tolkien')].title")
        assertEquals(
            listOf("Sayings of the Century", "The Lord of the Rings"),
            result,
        )
    }
  }

  @Nested
  inner class LogicalNotTest {
    @Test
    fun `logical NOT should negate condition`() {
      val result = sampleData.query<List<*>>("$.store.book[?(!(@.price > 10))].title")
        assertEquals(
            listOf("Sayings of the Century", "Moby Dick"),
            result,
        )
    }

    @Test
    fun `logical NOT with equality`() {
      val result = sampleData.query<List<*>>("$.store.book[?(!(@.category == 'fiction'))].title")
        assertEquals(listOf("Sayings of the Century"), result)
    }
  }

  @Nested
  inner class ComplexLogicalTest {
    @Test
    fun `complex logical expression with AND and OR`() {
      val result = sampleData.query<List<*>>("$.store.book[?((@.price < 10 || @.price > 20) && @.category == 'fiction')].title")
        assertEquals(
            listOf("Moby Dick", "The Lord of the Rings"),
            result,
        )
    }

    @Test
    fun `complex logical expression with NOT and AND`() {
      val result = sampleData.query<List<*>>("$.store.book[?(!(@.price > 15) && @.category == 'fiction')].title")
        assertEquals(
            listOf("Sword of Honour", "Moby Dick"),
            result,
        )
    }
  }

  @Nested
  inner class MinFunctionTest {
    @Test
    fun `min should return minimum value from array`() {
      val result = arrayData.query<Int>("$.numbers.min()")
        assertEquals(1, result)
    }

    @Test
    fun `min should work with prices`() {
      val result = sampleData.query<Double>("$.store.book[*].price.min()")
        assertEquals(8.95, result)
    }
  }

  @Nested
  inner class MaxFunctionTest {
    @Test
    fun `max should return maximum value from array`() {
      val result = arrayData.query<Int>("$.numbers.max()")
        assertEquals(10, result)
    }

    @Test
    fun `max should work with prices`() {
      val result = sampleData.query<Double>("$.store.book[*].price.max()")
        assertEquals(22.99, result)
    }
  }

  @Nested
  inner class AvgFunctionTest {
    @Test
    fun `avg should return average value from array`() {
      val result = arrayData.query<Double>("$.numbers.avg()")
        assertEquals(5.5, result)
    }

    @Test
    fun `avg should work with prices`() {
      val result = sampleData.query<Double>("$.store.book[*].price.avg()")
      val expected = (8.95 + 12.99 + 8.99 + 22.99) / 4
        assertEquals(expected, result)
    }
  }

  @Nested
  inner class SumFunctionTest {
    @Test
    fun `sum should return sum of all values`() {
      val result = arrayData.query<Int>("$.numbers.sum()")
        assertEquals(55, result)
    }

    @Test
    fun `sum should work with prices`() {
      val result = sampleData.query<Double>("$.store.book[*].price.sum()")
        assertEquals(8.95 + 12.99 + 8.99 + 22.99, result)
    }
  }

  @Nested
  inner class LengthFunctionTest {
    @Test
    fun `length should return array size`() {
      val result = sampleData.query<Int>("$.store.book.length()")
        assertEquals(4, result)
    }

    @Test
    fun `length should work with string`() {
      val result = sampleData.query<Int>("$.store.bicycle.color.length()")
        assertEquals(3, result)
    }

    @Test
    fun `length should work with simple arrays`() {
      val result = arrayData.query<Int>("$.fruits.length()")
        assertEquals(5, result)
    }
  }

  @Nested
  inner class KeysFunctionTest {
    @Test
    fun `keys should return object keys`() {
      val result = sampleData.query<List<*>>("$.store.keys()")
        assertEquals(listOf("book", "bicycle"), result)
    }

    @Test
    fun `keys should work on nested objects`() {
      val result = sampleData.query<List<*>>("$.warehouse.inventory.keys()")
        assertEquals(listOf("books", "bicycles"), result)
    }
  }

  @Nested
  inner class ConcatFunctionTest {
    @Test
    fun `concat should concatenate arrays`() {
      val testData = mapOf(
        "arrays" to mapOf(
          "first" to listOf(1, 2, 3),
          "second" to listOf(4, 5, 6),
        ),
      )
      val result = testData.query<List<*>>("$.arrays.concat()")
        assertEquals(listOf(1, 2, 3, 4, 5, 6), result)
    }

    @Test
    fun `concat should work with strings`() {
      val testData = mapOf(
        "words" to listOf("Hello", " ", "World"),
      )
      val result = testData.query<String>("$.words.concat()")
        assertEquals("Hello World", result)
    }
  }

  @Nested
  inner class EdgeCasesTest {
    @Test
    fun `should handle empty arrays gracefully`() {
      val emptyData = mapOf("items" to emptyList<Any>())
      val result = emptyData.query<List<*>>("$.items[*]")
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `should handle null values in data`() {
      @Suppress("UNCHECKED_CAST")
      val dataWithNull = mapOf(
        "value" to null,
        "nested" to mapOf("value" to null),
      ) as Map<String, Any>
      val result = dataWithNull.queryOrNull<Any>("$.value")
        assertNull(result)
    }

    @Test
    fun `should handle deeply nested paths`() {
      val deepData = mapOf(
        "a" to mapOf(
          "b" to mapOf(
            "c" to mapOf(
              "d" to mapOf(
                "e" to "deep value",
              ),
            ),
          ),
        ),
      )
      val result = deepData.query<String>("$.a.b.c.d.e")
        assertEquals("deep value", result)
    }

    @Test
    fun `should return null for invalid path`() {
      val result = sampleData.queryOrNull<Any>("$.invalid.path.that.does.not.exist")
        assertNull(result)
    }
  }

  @Nested
  inner class SetFunctionTest {
    @Test
    fun `set replaces existing simple property and returns the previous value`() {
      val data = mutableMapOf<String, Any>("name" to "John", "age" to 30)
      val previous = data.set("$.name", "Jane")
        assertEquals("John", previous)
        assertEquals("Jane", data["name"])
        assertEquals(30, data["age"])
    }

    @Test
    fun `set returns null and adds the key when the leaf is absent`() {
      val data = mutableMapOf<String, Any>("name" to "John")
      val previous = data.set("$.age", 30)
        assertNull(previous)
        assertEquals(30, data["age"])
    }

    @Test
    fun `set updates a nested property in place`() {
      val data = mutableMapOf<String, Any>(
        "user" to mutableMapOf<String, Any>(
          "name" to "John",
          "address" to mutableMapOf<String, Any>(
            "city" to "Seattle",
            "zip" to "98101",
          ),
        ),
      )
      val previous = data.set("$.user.address.city", "Portland")
      val address = (data["user"] as Map<*, *>)["address"] as Map<*, *>
        assertEquals("Seattle", previous)
        assertEquals("Portland", address["city"])
        assertEquals("98101", address["zip"])
    }

    @Test
    fun `set replaces an array element and returns the previous element`() {
      val data = mutableMapOf<String, Any>(
        "fruits" to mutableListOf<Any>("apple", "banana", "cherry"),
      )
      val previous = data.set("$.fruits.1", "blueberry")
      val fruits = data["fruits"] as List<*>
        assertEquals("banana", previous)
        assertEquals(listOf("apple", "blueberry", "cherry"), fruits)
    }

    @Test
    fun `set throws when an intermediate path is absent`() {
      val data = mutableMapOf<String, Any>("name" to "John")
      try {
        data.set("$.user.email", "john@example.com")
        throw AssertionError("Should have thrown IllegalArgumentException")
      } catch (e: IllegalArgumentException) {
          assertTrue(e.message?.contains("Path does not exist") == true)
      }
    }

    @Test
    fun `set throws when a list index is out of bounds`() {
      val data = mutableMapOf<String, Any>(
        "fruits" to mutableListOf<Any>("apple", "banana"),
      )
      try {
        data.set("$.fruits.5", "cherry")
        throw AssertionError("Should have thrown IllegalArgumentException")
      } catch (e: IllegalArgumentException) {
          assertTrue(e.message?.contains("Index out of bounds") == true)
      }
    }
  }

  @Nested
  inner class AddFunctionTest {
    @Test
    fun `add returns null when creating a brand new property`() {
      val data = mutableMapOf<String, Any>("name" to "John")
      val previous = data.add("$.age", 30)
        assertNull(previous)
        assertEquals(30, data["age"])
    }

    @Test
    fun `add returns previous value when replacing an existing property`() {
      val data = mutableMapOf<String, Any>("name" to "John", "age" to 25)
      val previous = data.add("$.age", 30)
        assertEquals(25, previous)
        assertEquals(30, data["age"])
    }

    @Test
    fun `add inserts into an existing nested mutable map`() {
      val data = mutableMapOf<String, Any>(
        "user" to mutableMapOf<String, Any>("name" to "John"),
      )
      val previous = data.add("$.user.email", "john@example.com")
      val user = data["user"] as Map<*, *>
        assertNull(previous)
        assertEquals("John", user["name"])
        assertEquals("john@example.com", user["email"])
    }

    @Test
    fun `add creates missing intermediate maps when navigating from empty root`() {
      val data = mutableMapOf<String, Any>()
      val previous = data.add("$.level1.level2.level3", "deep value")
      val level1 = data["level1"] as Map<*, *>
      val level2 = level1["level2"] as Map<*, *>
        assertNull(previous)
        assertEquals("deep value", level2["level3"])
    }

    @Test
    fun `add replaces an array element by index and returns the previous element`() {
      val data = mutableMapOf<String, Any>(
        "fruits" to mutableListOf<Any>("apple", "banana"),
      )
      val previous = data.add("$.fruits.0", "apricot")
      val fruits = data["fruits"] as List<*>
        assertEquals("apple", previous)
        assertEquals(listOf("apricot", "banana"), fruits)
    }
  }

  @Nested
  inner class DeleteFunctionTest {
    @Test
    fun `delete removes a simple property and returns its previous value`() {
      val data = mutableMapOf<String, Any>("name" to "John", "age" to 30)
      val previous = data.delete("$.age")
        assertEquals(30, previous)
        assertEquals("John", data["name"])
        assertNull(data["age"])
        assertEquals(1, data.size)
    }

    @Test
    fun `delete removes a nested property in place`() {
      val data = mutableMapOf<String, Any>(
        "user" to mutableMapOf<String, Any>(
          "name" to "John",
          "email" to "john@example.com",
          "age" to 30,
        ),
      )
      val previous = data.delete("$.user.email")
      val user = data["user"] as Map<*, *>
        assertEquals("john@example.com", previous)
        assertEquals("John", user["name"])
        assertEquals(30, user["age"])
        assertNull(user["email"])
        assertEquals(2, user.size)
    }

    @Test
    fun `delete removes an array element and returns the removed element`() {
      val data = mutableMapOf<String, Any>(
        "fruits" to mutableListOf<Any>("apple", "banana", "cherry"),
      )
      val previous = data.delete("$.fruits.1")
      val fruits = data["fruits"] as List<*>
        assertEquals("banana", previous)
        assertEquals(listOf("apple", "cherry"), fruits)
    }

    @Test
    fun `delete returns null when the leaf key is absent on the map parent`() {
      val data = mutableMapOf<String, Any>("name" to "John")
      val previous = data.delete("$.age")
        assertNull(previous)
        assertEquals("John", data["name"])
    }

    @Test
    fun `delete throws when an intermediate path is absent`() {
      val data = mutableMapOf<String, Any>("name" to "John")
      try {
        data.delete("$.user.email")
        throw AssertionError("Should have thrown IllegalArgumentException")
      } catch (e: IllegalArgumentException) {
          assertTrue(e.message?.contains("Path does not exist") == true)
      }
    }

    @Test
    fun `delete throws when a list index is out of bounds`() {
      val data = mutableMapOf<String, Any>(
        "fruits" to mutableListOf<Any>("apple", "banana"),
      )
      try {
        data.delete("$.fruits.5")
        throw AssertionError("Should have thrown IllegalArgumentException")
      } catch (e: IllegalArgumentException) {
          assertTrue(e.message?.contains("Index out of bounds") == true)
      }
    }
  }

  @Nested
  inner class MutationInPlaceTest {
    @Test
    fun `set mutates the receiver in place`() {
      val data = mutableMapOf<String, Any>("name" to "John", "age" to 30)
      data.set("$.name", "Jane")
        assertEquals("Jane", data["name"])
    }

    @Test
    fun `add mutates the receiver in place`() {
      val data = mutableMapOf<String, Any>("name" to "John")
      data.add("$.age", 30)
        assertEquals(30, data["age"])
    }

    @Test
    fun `delete mutates the receiver in place`() {
      val data = mutableMapOf<String, Any>("name" to "John", "age" to 30)
      data.delete("$.age")
        assertNull(data["age"])
        assertFalse(data.containsKey("age"))
    }
  }

  @Nested
  inner class ContainsPathTest {
    @Test
    fun `containsPath returns true for existing simple property`() {
      assertTrue(sampleData.containsPath("$.store"))
    }

    @Test
    fun `containsPath returns true for nested property`() {
      assertTrue(sampleData.containsPath("$.store.bicycle.color"))
    }

    @Test
    fun `containsPath returns true for deeply nested property`() {
      assertTrue(nestedData.containsPath("$.level1.level2.level3.name"))
    }

    @Test
    fun `containsPath returns true for bracket notation`() {
      assertTrue(sampleData.containsPath("$['store']['bicycle']['color']"))
    }

    @Test
    fun `containsPath returns false for non-existent property`() {
      assertFalse(sampleData.containsPath("$.store.nonexistent"))
    }

    @Test
    fun `containsPath returns false for non-existent nested property`() {
      assertFalse(sampleData.containsPath("$.invalid.path.that.does.not.exist"))
    }

    @Test
    fun `containsPath returns true for valid array index`() {
      assertTrue(sampleData.containsPath("$.store.book[0]"))
    }

    @Test
    fun `containsPath returns true for negative array index in range`() {
      assertTrue(sampleData.containsPath("$.store.book[-1]"))
    }

    @Test
    fun `containsPath returns false for out-of-bounds array index`() {
      assertFalse(sampleData.containsPath("$.store.book[10]"))
    }

    @Test
    fun `containsPath returns true for path resolving to explicit null value`() {
      @Suppress("UNCHECKED_CAST")
      val dataWithNull = mapOf("value" to null) as Map<String, Any>
      assertTrue(dataWithNull.containsPath("$.value"))
    }

    @Test
    fun `containsPath returns false for absent key on map that also has null-valued keys`() {
      @Suppress("UNCHECKED_CAST")
      val dataWithNull = mapOf("value" to null) as Map<String, Any>
      assertFalse(dataWithNull.containsPath("$.missing"))
    }

    @Test
    fun `containsPath returns true for nested path with null leaf`() {
      @Suppress("UNCHECKED_CAST")
      val data = mapOf("user" to mapOf("name" to null)) as Map<String, Any>
      assertTrue(data.containsPath("$.user.name"))
    }

    @Test
    fun `containsPath returns false when navigating through a null intermediate`() {
      @Suppress("UNCHECKED_CAST")
      val data = mapOf("user" to null) as Map<String, Any>
      assertFalse(data.containsPath("$.user.name"))
    }

    @Test
    fun `containsPath returns true for wildcard over existing collection`() {
      assertTrue(sampleData.containsPath("$.store.book[*]"))
    }

    @Test
    fun `containsPath returns true for filter that matches`() {
      assertTrue(sampleData.containsPath("$.store.book[?(@.price < 10)]"))
    }

    @Test
    fun `containsPath returns true for filter that matches nothing`() {
      // The filter produces an empty list, which is non-null.
      assertTrue(sampleData.containsPath("$.store.book[?(@.price > 1000)]"))
    }

    @Test
    fun `containsPath returns true for root`() {
      assertTrue(sampleData.containsPath("$"))
    }
  }

  @Nested
  inner class QueryOrDefaultTest {
    @Test
    fun `queryOrDefault returns the matched value when path resolves`() {
      assertEquals("red", sampleData.queryOrDefault("$.store.bicycle.color", "unknown"))
    }

    @Test
    fun `queryOrDefault returns the default when path is absent`() {
      assertEquals("unknown", sampleData.queryOrDefault("$.store.missing", "unknown"))
    }

    @Test
    fun `queryOrDefault returns the default when navigation fails on missing nested key`() {
      assertEquals(0, sampleData.queryOrDefault("$.store.book[10].price", 0))
    }

    @Test
    fun `queryOrDefault returns the default when value type does not match`() {
      assertEquals(-1, sampleData.queryOrDefault<Int>("$.store.bicycle.color", -1))
    }

    @Test
    fun `queryOrDefault returns the default when path resolves to explicit null`() {
      @Suppress("UNCHECKED_CAST")
      val data = mapOf("value" to null) as Map<String, Any>
      assertEquals("fallback", data.queryOrDefault<String>("$.value", "fallback"))
    }

    @Test
    fun `queryOrDefault works with typed numeric default`() {
      assertEquals(22.99, sampleData.queryOrDefault("$.store.book[3].price", 0.0))
      assertEquals(0.0, sampleData.queryOrDefault("$.store.book[3].discount", 0.0))
    }

    @Test
    fun `queryOrDefault works with list default`() {
      val emptyList = emptyList<String>()
      val matched = sampleData.queryOrDefault<List<*>>("$.store.book[*].title", emptyList)
      assertEquals(4, matched.size)
      val fallback = sampleData.queryOrDefault<List<*>>("$.store.missing", emptyList)
      assertEquals(emptyList, fallback)
    }
  }
}

package io.github.yesitskev.mappath

internal sealed class FilterValue {
  abstract fun getValue(item: Any?): Any?

  data class Literal(val value: Any?) : FilterValue() {
    override fun getValue(item: Any?): Any? = value
  }

  data class PropertyAccess(val property: String) : FilterValue() {
    override fun getValue(item: Any?): Any? = getPropertyValue(item, property)
  }

  data class PropertySize(val property: String) : FilterValue() {
    override fun getValue(item: Any?): Any? {
      return when (val value = getPropertyValue(item, property)) {
        is List<*> -> value.size
        is Map<*, *> -> value.size
        is String -> value.length
        else -> null
      }
    }
  }

  object CurrentItem : FilterValue() {
    override fun getValue(item: Any?): Any? = item
  }
}


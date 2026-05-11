package io.github.yesitskev.mappath

internal fun parsePath(path: String): List<String> {
  // Simple path parser for dot notation (e.g., "$.store.book.0.title")
  val normalized = path.removePrefix("$").removePrefix(".")
  return if (normalized.isEmpty()) emptyList() else normalized.split(".")
}

internal fun setMutating(root: MutableMap<String, Any>, path: String, value: Any): Any? {
  val steps = parsePath(path)
  require(steps.isNotEmpty()) { "Cannot set the root" }
  val parent = navigateToParent(root, steps, createIfMissing = false)
  return putOnContainer(parent, steps.last(), value)
}

internal fun addMutating(root: MutableMap<String, Any>, path: String, value: Any): Any? {
  val steps = parsePath(path)
  require(steps.isNotEmpty()) { "Cannot add at the root" }
  val parent = navigateToParent(root, steps, createIfMissing = true)
  return putOnContainer(parent, steps.last(), value)
}

internal fun deleteMutating(root: MutableMap<String, Any>, path: String): Any? {
  val steps = parsePath(path)
  require(steps.isNotEmpty()) { "Cannot delete the root" }
  val parent = navigateToParent(root, steps, createIfMissing = false)
  return removeFromContainer(parent, steps.last())
}

private fun navigateToParent(root: Any?, steps: List<String>, createIfMissing: Boolean): Any {
  var current: Any? = root
  for (i in 0 until steps.size - 1) {
    val step = steps[i]
    current = when (val c = current) {
      is Map<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        val m = c as MutableMap<String, Any>
        if (step in m) {
          m[step]
        } else {
          require(createIfMissing) { "Path does not exist: $step" }
          val fresh: MutableMap<String, Any> = mutableMapOf()
          m[step] = fresh
          fresh
        }
      }

      is List<*> -> {
        @Suppress("UNCHECKED_CAST")
        val l = c as MutableList<Any>
        val idx = requireNotNull(step.toIntOrNull()) { "Invalid array index: $step" }
        require(idx in l.indices) { "Path does not exist: $step" }
        l[idx]
      }

      else -> throw IllegalArgumentException("Cannot navigate through non-map/non-list at: $step")
    }
  }
  return requireNotNull(current) { "Path does not exist (parent is null)" }
}

private fun putOnContainer(parent: Any, leaf: String, value: Any): Any? {
  return when (parent) {
    is Map<*, *> -> {
      @Suppress("UNCHECKED_CAST")
      (parent as MutableMap<String, Any>).put(leaf, value)
    }

    is List<*> -> {
      @Suppress("UNCHECKED_CAST")
      val l = parent as MutableList<Any>
      val idx = requireNotNull(leaf.toIntOrNull()) { "Invalid array index: $leaf" }
      require(idx in l.indices) { "Index out of bounds: $leaf" }
      val previous = l[idx]
      l[idx] = value
      previous
    }

    else -> throw IllegalArgumentException("Cannot put on non-map/non-list container")
  }
}

private fun removeFromContainer(parent: Any, leaf: String): Any? {
  return when (parent) {
    is Map<*, *> -> {
      @Suppress("UNCHECKED_CAST")
      (parent as MutableMap<String, Any>).remove(leaf)
    }

    is List<*> -> {
      @Suppress("UNCHECKED_CAST")
      val l = parent as MutableList<Any>
      val idx = requireNotNull(leaf.toIntOrNull()) { "Invalid array index: $leaf" }
      require(idx in l.indices) { "Index out of bounds: $leaf" }
      l.removeAt(idx)
    }

    else -> throw IllegalArgumentException("Cannot remove from non-map/non-list container")
  }
}

internal fun getPropertyValue(item: Any?, property: String): Any? {
  var current: Any? = item

  for (part in property.split(".")) {
    current = when (current) {
      is Map<*, *> -> current[part]
      else -> null
    }

    if (current == null) break
  }

  return current
}

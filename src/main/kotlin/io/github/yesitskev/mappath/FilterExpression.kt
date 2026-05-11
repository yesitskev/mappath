package io.github.yesitskev.mappath

internal sealed class FilterExpression {
  abstract fun evaluate(item: Any?): Boolean

  data class Comparison(
    val left: FilterValue,
    val operator: ComparisonOperator,
    val right: FilterValue
  ) : FilterExpression() {
    override fun evaluate(item: Any?): Boolean {
      val leftVal = left.getValue(item)
      val rightVal = right.getValue(item)

      return when (operator) {
        ComparisonOperator.EQ -> leftVal == rightVal
        ComparisonOperator.NE -> leftVal != rightVal
        ComparisonOperator.LT -> compareValues(leftVal, rightVal) < 0
        ComparisonOperator.LE -> compareValues(leftVal, rightVal) <= 0
        ComparisonOperator.GT -> compareValues(leftVal, rightVal) > 0
        ComparisonOperator.GE -> compareValues(leftVal, rightVal) >= 0
        ComparisonOperator.REGEX -> {
          val str = leftVal?.toString() ?: ""
          val pattern = rightVal?.toString() ?: ""
          matchesRegex(str, pattern)
        }

        ComparisonOperator.IN -> {
          when (rightVal) {
            is List<*> -> rightVal.contains(leftVal)
            else -> false
          }
        }

        ComparisonOperator.NIN -> {
          when (rightVal) {
            is List<*> -> !rightVal.contains(leftVal)
            else -> true
          }
        }
      }
    }

    private fun compareValues(a: Any?, b: Any?): Int {
      return when {
        a is Number && b is Number -> a.toDouble().compareTo(b.toDouble())
        a is String && b is String -> a.compareTo(b)
        a is Comparable<*> && b is Comparable<*> -> {
          @Suppress("UNCHECKED_CAST")
          (a as Comparable<Any>).compareTo(b as Any)
        }

        else -> 0
      }
    }

    private fun matchesRegex(str: String, pattern: String): Boolean {
      val cleanPattern = pattern.removePrefix("/").removeSuffix("/i").removeSuffix("/")
      val caseInsensitive = pattern.endsWith("/i")
      val regex = if (caseInsensitive) {
        Regex(cleanPattern, RegexOption.IGNORE_CASE)
      } else {
        Regex(cleanPattern)
      }
      return regex.matches(str)
    }
  }

  data class LogicalAnd(val left: FilterExpression, val right: FilterExpression) : FilterExpression() {
    override fun evaluate(item: Any?): Boolean = left.evaluate(item) && right.evaluate(item)
  }

  data class LogicalOr(val left: FilterExpression, val right: FilterExpression) : FilterExpression() {
    override fun evaluate(item: Any?): Boolean = left.evaluate(item) || right.evaluate(item)
  }

  data class LogicalNot(val expression: FilterExpression) : FilterExpression() {
    override fun evaluate(item: Any?): Boolean = !expression.evaluate(item)
  }

  data class PropertyCheck(val property: String, val checkType: PropertyCheckType) : FilterExpression() {
    override fun evaluate(item: Any?): Boolean {
      val value = getPropertyValue(item, property)
      return when (checkType) {
        PropertyCheckType.SIZE -> {
          when (value) {
            is List<*> -> true
            is Map<*, *> -> true
            is String -> true
            else -> false
          }
        }

        PropertyCheckType.EMPTY -> {
          when (value) {
            is List<*> -> value.isEmpty()
            is Map<*, *> -> value.isEmpty()
            is String -> value.isEmpty()
            else -> value == null
          }
        }
      }
    }
  }

  companion object {
    fun parse(expression: String): FilterExpression {
      val normalized = expression.trim()

      // Parse logical OR (the lowest precedence)
      val orParts = splitByLogicalOperator(normalized, "||")
      if (orParts.size > 1) {
        return orParts.map { parse(it) }.reduce { acc, expr -> LogicalOr(acc, expr) }
      }

      // Parse logical AND
      val andParts = splitByLogicalOperator(normalized, "&&")
      if (andParts.size > 1) {
        return andParts.map { parse(it) }.reduce { acc, expr -> LogicalAnd(acc, expr) }
      }

      // Parse logical NOT (handle spaces after !)
      val notPattern = Regex("""^!\s*\((.*)\)$""")
      val notMatch = notPattern.find(normalized)
      if (notMatch != null) {
        val inner = notMatch.groupValues[1]
        return LogicalNot(parse(inner))
      }

      // Remove outer parentheses if present and recursively parse
      if (normalized.startsWith("(") && normalized.endsWith(")")) {
        val cleaned = normalized.substring(1, normalized.length - 1)
        // Check if the parentheses are balanced (not part of a comparison)
        var depth = 0
        var isBalanced = true
        for (i in cleaned.indices) {
          if (cleaned[i] == '(') depth++
          else if (cleaned[i] == ')') {
            depth--
            if (depth < 0) {
              isBalanced = false
              break
            }
          }
        }
        if (depth == 0 && isBalanced) {
          // Outer parens are balanced, recursively parse the inner expression
          return parse(cleaned)
        }
      }

      // Check for 'in' operator
      if (" in " in normalized) {
        val parts = normalized.split(" in ")
        if (parts.size == 2) {
          val left = parseFilterValue(parts[0].trim())
          val right = parseFilterValue(parts[1].trim())
          return Comparison(left, ComparisonOperator.IN, right)
        }
      }

      // Check for 'nin' operator
      if (" nin " in normalized) {
        val parts = normalized.split(" nin ")
        if (parts.size == 2) {
          val left = parseFilterValue(parts[0].trim())
          val right = parseFilterValue(parts[1].trim())
          return Comparison(left, ComparisonOperator.NIN, right)
        }
      }

      // Check for regex match
      if (" =~ " in normalized) {
        val parts = normalized.split(" =~ ")
        if (parts.size == 2) {
          val left = parseFilterValue(parts[0].trim())
          val right = parseFilterValue(parts[1].trim())
          return Comparison(left, ComparisonOperator.REGEX, right)
        }
      }

      // Check for .size or .empty
      if (normalized.contains(".size")) {
        val propertyMatch = Regex("""@\.(\w+(?:\.\w+)*)\.size\s*==\s*(\d+)""").find(normalized)
        if (propertyMatch != null) {
          val property = propertyMatch.groupValues[1]
          val expectedSize = propertyMatch.groupValues[2].toInt()
          return Comparison(
            FilterValue.PropertySize(property),
            ComparisonOperator.EQ,
            FilterValue.Literal(expectedSize),
          )
        }
      }

      if (normalized.contains(".empty")) {
        val propertyMatch = Regex("""@\.(\w+(?:\.\w+)*)\.empty""").find(normalized)
        if (propertyMatch != null) {
          val property = propertyMatch.groupValues[1]
          return PropertyCheck(property, PropertyCheckType.EMPTY)
        }
      }

      // Parse comparison operators (check longer operators first)
      for (op in listOf("==", "!=", "<=", ">=", "<", ">")) {
        if (op in normalized) {
          val parts = normalized.split(op, limit = 2)
          if (parts.size == 2) {
            val left = parseFilterValue(parts[0].trim())
            val right = parseFilterValue(parts[1].trim())
            val operator = when (op) {
              "==" -> ComparisonOperator.EQ
              "!=" -> ComparisonOperator.NE
              "<" -> ComparisonOperator.LT
              "<=" -> ComparisonOperator.LE
              ">" -> ComparisonOperator.GT
              ">=" -> ComparisonOperator.GE
              else -> ComparisonOperator.EQ
            }
            return Comparison(left, operator, right)
          }
        }
      }

      // Default: treat as property existence check
      return Comparison(
        FilterValue.CurrentItem,
        ComparisonOperator.NE,
        FilterValue.Literal(null),
      )
    }

    private fun splitByLogicalOperator(expr: String, operator: String): List<String> {
      val parts = mutableListOf<String>()
      var depth = 0
      var currentPart = StringBuilder()
      var i = 0

      while (i < expr.length) {
        when {
          expr[i] == '(' -> {
            depth++
            currentPart.append(expr[i])
            i++
          }

          expr[i] == ')' -> {
            depth--
            currentPart.append(expr[i])
            i++
          }

          depth == 0 && expr.substring(i).startsWith(operator) -> {
            parts.add(currentPart.toString().trim())
            currentPart = StringBuilder()
            i += operator.length
          }

          else -> {
            currentPart.append(expr[i])
            i++
          }
        }
      }

      if (currentPart.isNotEmpty()) {
        parts.add(currentPart.toString().trim())
      }

      return parts
    }

    private fun parseFilterValue(value: String): FilterValue {
      val trimmed = value.trim()

      return when {
        trimmed.startsWith("@.") -> {
          val property = trimmed.substring(2)
          FilterValue.PropertyAccess(property)
        }

        trimmed.startsWith("'") && trimmed.endsWith("'") -> {
          FilterValue.Literal(trimmed.substring(1, trimmed.length - 1))
        }

        trimmed.startsWith("\"") && trimmed.endsWith("\"") -> {
          FilterValue.Literal(trimmed.substring(1, trimmed.length - 1))
        }

        trimmed.startsWith("/") -> {
          FilterValue.Literal(trimmed)
        }

        trimmed.toIntOrNull() != null -> {
          FilterValue.Literal(trimmed.toInt())
        }

        trimmed.toDoubleOrNull() != null -> {
          FilterValue.Literal(trimmed.toDouble())
        }

        trimmed == "null" -> {
          FilterValue.Literal(null)
        }

        trimmed == "@" -> {
          FilterValue.CurrentItem
        }

        else -> {
          FilterValue.PropertyAccess(trimmed)
        }
      }
    }
  }
}


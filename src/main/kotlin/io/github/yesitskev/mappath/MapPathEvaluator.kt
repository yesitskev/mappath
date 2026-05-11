package io.github.yesitskev.mappath

internal object MapPathEvaluator {
  fun evaluate(path: String, data: Any?): Any? {
    val tokens = tokenize(path)
    val steps = parse(tokens)
    return executeSteps(steps, data)
  }

  fun contains(path: String, data: Any?): Boolean {
    val tokens = tokenize(path)
    val steps = parse(tokens)
    return checkSteps(steps, data)
  }

  private fun checkSteps(steps: List<PathStep>, initialData: Any?): Boolean {
    var current: Any? = initialData
    for (step in steps) {
      when (step) {
        is PathStep.Root -> Unit

        is PathStep.Property -> {
          val name = step.name
          if (name.endsWith("()")) {
            current = executeStep(step, current) ?: return false
            continue
          }
          current = when (val c = current) {
            is Map<*, *> -> {
              if (name !in c) return false
              c[name]
            }
            is List<*> -> {
              val results = c.mapNotNull { item ->
                if (item is Map<*, *> && name in item) item[name] else null
              }
              if (results.isEmpty()) return false
              results
            }
            else -> return false
          }
        }

        is PathStep.Index -> {
          val c = current
          if (c !is List<*>) return false
          val actual = if (step.index < 0) c.size + step.index else step.index
          if (actual < 0 || actual >= c.size) return false
          current = c[actual]
        }

        else -> {
          current = executeStep(step, current) ?: return false
        }
      }
    }
    return true
  }

  private fun tokenize(path: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0

    while (i < path.length) {
      when {
        path[i] == '$' -> {
          tokens.add(Token.Root)
          i++
        }

        path[i] == '@' -> {
          tokens.add(Token.At)
          i++
        }

        path[i] == '.' && i + 1 < path.length && path[i + 1] == '.' -> {
          tokens.add(Token.RecursiveDescent)
          i += 2
        }

        path[i] == '.' -> {
          tokens.add(Token.Dot)
          i++
        }

        path[i] == '[' -> {
          tokens.add(Token.LeftBracket)
          i++
        }

        path[i] == ']' -> {
          tokens.add(Token.RightBracket)
          i++
        }

        path[i] == '*' -> {
          tokens.add(Token.Wildcard)
          i++
        }

        path[i] == '(' -> {
          tokens.add(Token.LeftParen)
          i++
        }

        path[i] == ')' -> {
          tokens.add(Token.RightParen)
          i++
        }

        path[i] == ',' -> {
          tokens.add(Token.Comma)
          i++
        }

        path[i] == ':' -> {
          tokens.add(Token.Colon)
          i++
        }
        // Comparison operators
        path[i] == '=' && i + 1 < path.length && path[i + 1] == '=' -> {
          tokens.add(Token.Operator("=="))
          i += 2
        }

        path[i] == '!' && i + 1 < path.length && path[i + 1] == '=' -> {
          tokens.add(Token.Operator("!="))
          i += 2
        }

        path[i] == '<' && i + 1 < path.length && path[i + 1] == '=' -> {
          tokens.add(Token.Operator("<="))
          i += 2
        }

        path[i] == '>' && i + 1 < path.length && path[i + 1] == '=' -> {
          tokens.add(Token.Operator(">="))
          i += 2
        }

        path[i] == '<' -> {
          tokens.add(Token.Operator("<"))
          i++
        }

        path[i] == '>' -> {
          tokens.add(Token.Operator(">"))
          i++
        }

        path[i] == '&' && i + 1 < path.length && path[i + 1] == '&' -> {
          tokens.add(Token.Operator("&&"))
          i += 2
        }

        path[i] == '|' && i + 1 < path.length && path[i + 1] == '|' -> {
          tokens.add(Token.Operator("||"))
          i += 2
        }

        path[i] == '!' -> {
          tokens.add(Token.Operator("!"))
          i++
        }

        path[i] == '=' && i + 1 < path.length && path[i + 1] == '~' -> {
          tokens.add(Token.Operator("=~"))
          i += 2
        }

        path[i] == '\'' || path[i] == '"' -> {
          val quote = path[i]
          val start = i + 1
          i++
          while (i < path.length && path[i] != quote) {
            if (path[i] == '\\') i++
            i++
          }
          tokens.add(Token.String(path.substring(start, i)))
          i++
        }

        path[i] == '/' -> {
          // Regex literal - capture everything between / and the closing /
          val start = i
          i++
          while (i < path.length && path[i] != '/') {
            if (path[i] == '\\') i++
            i++
          }
          if (i < path.length) i++ // skip closing /
          // Check for flags (like 'i' for case-insensitive)
          while (i < path.length && path[i].isLetter()) {
            i++
          }
          tokens.add(Token.String(path.substring(start, i)))
        }

        path[i] == '?' -> {
          tokens.add(Token.Question)
          i++
        }

        path[i].isWhitespace() -> {
          i++
        }

        path[i].isDigit() || (path[i] == '-' && i + 1 < path.length && path[i + 1].isDigit()) -> {
          val start = i
          if (path[i] == '-') i++
          while (i < path.length && (path[i].isDigit() || path[i] == '.')) {
            i++
          }
          val numStr = path.substring(start, i)
          if ('.' in numStr) {
            tokens.add(Token.Number(numStr.toDouble()))
          } else {
            tokens.add(Token.Number(numStr.toInt()))
          }
        }

        path[i].isLetter() || path[i] == '_' -> {
          val start = i
          while (i < path.length && (path[i].isLetterOrDigit() || path[i] == '_')) {
            i++
          }
          val word = path.substring(start, i)
          // Check for special keywords that should be operators
          if (word == "in" || word == "nin") {
            tokens.add(Token.Operator(word))
          } else {
            tokens.add(Token.Identifier(word))
          }
        }

        else -> {
          i++
        }
      }
    }

    return tokens
  }

  private fun parse(tokens: List<Token>): List<PathStep> {
    val steps = mutableListOf<PathStep>()
    var i = 0

    if (tokens.isEmpty()) return steps

    if (tokens[i] is Token.Root) {
      steps.add(PathStep.Root)
      i++
    }

    while (i < tokens.size) {
      when (val token = tokens[i]) {
        is Token.Dot -> {
          i++
          if (i < tokens.size) {
            when (val next = tokens[i]) {
              is Token.Identifier -> {
                i++
                // Check if it's a function call (identifier followed by parentheses)
                if (i < tokens.size && tokens[i] is Token.LeftParen) {
                  i++ // skip (
                  if (i < tokens.size && tokens[i] is Token.RightParen) {
                    i++ // skip )
                    steps.add(PathStep.Function(next.name))
                  }
                } else {
                  steps.add(PathStep.Property(next.name))
                }
              }

              is Token.Wildcard -> {
                steps.add(PathStep.Wildcard)
                i++
              }

              else -> {}
            }
          }
        }

        is Token.RecursiveDescent -> {
          i++
          if (i < tokens.size && tokens[i] is Token.Identifier) {
            steps.add(PathStep.RecursiveDescent((tokens[i] as Token.Identifier).name))
            i++
          }
        }

        is Token.LeftBracket -> {
          i++
          val bracketContent = parseBracketContent(tokens, i)
          steps.add(bracketContent.step)
          i = bracketContent.nextIndex
        }

        else -> i++
      }
    }

    return steps
  }

  private data class BracketParseResult(val step: PathStep, val nextIndex: Int)

  private fun parseBracketContent(tokens: List<Token>, startIndex: Int): BracketParseResult {
    var i = startIndex

    // Check for filter expression
    if (i < tokens.size && tokens[i] is Token.Question) {
      i++ // skip ?
      if (i < tokens.size && tokens[i] is Token.LeftParen) {
        i++ // skip (
        val filterEnd = findMatchingParen(tokens, i)
        val filterTokens = tokens.subList(i, filterEnd)
        val filter = parseFilterExpression(filterTokens)
        i = filterEnd + 1 // skip )
        if (i < tokens.size && tokens[i] is Token.RightBracket) {
          i++ // skip ]
        }
        return BracketParseResult(PathStep.Filter(filter), i)
      }
    }

    // Check for wildcard
    if (i < tokens.size && tokens[i] is Token.Wildcard) {
      i++
      if (i < tokens.size && tokens[i] is Token.RightBracket) {
        i++
      }
      return BracketParseResult(PathStep.Wildcard, i)
    }

    // Check for string (property name) - single property only
    if (i < tokens.size && tokens[i] is Token.String) {
      val savedI = i
      val propName = (tokens[i] as Token.String).value
      i++
      if (i < tokens.size && tokens[i] is Token.RightBracket) {
        i++
        return BracketParseResult(PathStep.Property(propName), i)
      }
      // Restore i if this wasn't a single property access
      i = savedI
    }

    // Check for union or slice
    val elements = mutableListOf<Any>()
    var hasColon = false
    var colonCount = 0

    while (i < tokens.size && tokens[i] !is Token.RightBracket) {
      when (val token = tokens[i]) {
        is Token.Number -> {
          elements.add(token.value)
          i++
        }

        is Token.String -> {
          elements.add(token.value)
          i++
        }

        is Token.Comma -> {
          i++
        }

        is Token.Colon -> {
          hasColon = true
          colonCount++
          elements.add(":")
          i++
        }

        else -> i++
      }
    }

    if (i < tokens.size && tokens[i] is Token.RightBracket) {
      i++
    }

    // Determine the type of bracket content
    return if (hasColon) {
      // Slice - parse start:end:step
      var sliceStart: Int? = null
      var sliceEnd: Int? = null
      var sliceStep: Int? = null

      var currentPosition = 0 // 0=start, 1=end, 2=step
      var j = 0

      while (j < elements.size) {
        val element = elements[j]
        if (element == ":") {
          currentPosition++
        } else if (element is Number) {
          when (currentPosition) {
            0 -> sliceStart = element.toInt()
            1 -> sliceEnd = element.toInt()
            2 -> sliceStep = element.toInt()
          }
        }
        j++
      }

      BracketParseResult(
        PathStep.Slice(
          sliceStart,
          sliceEnd,
          sliceStep ?: 1,
        ),
        i,
      )
    } else if (elements.size == 1 && elements[0] is Number) {
      // Single index
      BracketParseResult(PathStep.Index((elements[0] as Number).toInt()), i)
    } else if (elements.all { it is String }) {
      // Union of properties
      BracketParseResult(PathStep.Union(elements.map { it.toString() }), i)
    } else {
      // Union of indices
      BracketParseResult(PathStep.Union(elements.map { it.toString() }), i)
    }
  }

  private fun findMatchingParen(tokens: List<Token>, startIndex: Int): Int {
    var depth = 1
    var i = startIndex
    while (i < tokens.size && depth > 0) {
      when (tokens[i]) {
        is Token.LeftParen -> depth++
        is Token.RightParen -> depth--
        else -> {}
      }
      if (depth > 0) i++
    }
    return i
  }

  private fun parseFilterExpression(tokens: List<Token>): FilterExpression {
    // This is a simplified parser for filter expressions
    // It handles basic comparisons and logical operators
    val parts = mutableListOf<String>()
    for (token in tokens) {
      val str = when (token) {
        is Token.Identifier -> token.name
        is Token.Number -> token.value.toString()
        is Token.String -> "'${token.value}'"
        is Token.Operator -> " ${token.op} "
        is Token.Dot -> "."
        is Token.At -> "@"
        Token.LeftParen -> "("
        Token.RightParen -> ")"
        Token.Comma -> ","
        Token.Colon -> ":"
        else -> ""
      }
      parts.add(str)
    }

    val tokenString = parts.joinToString("").trim()

    return FilterExpression.parse(tokenString)
  }

  private fun executeSteps(steps: List<PathStep>, initialData: Any?): Any? {
    var current: Any? = initialData

    for (step in steps) {
      current = executeStep(step, current)
    }

    return current
  }

  private fun executeStep(step: PathStep, data: Any?): Any? {
    return when (step) {
      is PathStep.Root -> data
      is PathStep.Property -> getProperty(data, step.name)
      is PathStep.Index -> getIndex(data, step.index)
      is PathStep.Wildcard -> getWildcard(data)
      is PathStep.Slice -> getSlice(data, step.start, step.end, step.step)
      is PathStep.RecursiveDescent -> getRecursiveDescent(data, step.property)
      is PathStep.Filter -> applyFilter(data, step.expression)
      is PathStep.Union -> getUnion(data, step.items)
      is PathStep.Function -> executeFunction(data, step.name)
    }
  }

  private fun getProperty(data: Any?, name: String): Any? {
    // Check if it's a function call
    if (name.endsWith("()")) {
      val funcName = name.removeSuffix("()")
      return executeFunction(data, funcName)
    }

    return when (data) {
      is Map<*, *> -> data[name]
      is List<*> -> {
        // Apply property access to each element and always return a list
        val results = data.mapNotNull { getProperty(it, name) }
        if (results.isEmpty()) null else results
      }

      else -> null
    }
  }

  private fun getIndex(data: Any?, index: Int): Any? {
    return when (data) {
      is List<*> -> {
        val actualIndex = if (index < 0) data.size + index else index
        data.getOrNull(actualIndex)
      }

      else -> null
    }
  }

  private fun getWildcard(data: Any?): Any? {
    return when (data) {
      is Map<*, *> -> data.values.toList()
      is List<*> -> data
      else -> null
    }
  }

  private fun getSlice(data: Any?, start: Int?, end: Int?, step: Int): Any? {
    if (data !is List<*>) return null

    val size = data.size
    val actualStart = when {
      start == null -> if (step > 0) 0 else size - 1
      start < 0 -> maxOf(0, size + start)
      else -> minOf(start, size)
    }

    val actualEnd = when {
      end == null -> if (step > 0) size else -1
      end < 0 -> maxOf(0, size + end)
      else -> minOf(end, size)
    }

    if (step > 0) {
      return (actualStart until actualEnd step step).mapNotNull { data.getOrNull(it) }
    } else {
      return (actualStart downTo actualEnd + 1 step -step).mapNotNull { data.getOrNull(it) }
    }
  }

  private fun getRecursiveDescent(data: Any?, property: String): Any? {
    val results = mutableListOf<Any?>()

    fun recurse(current: Any?) {
      when (current) {
        is Map<*, *> -> {
          if (property in current.keys) {
            results.add(current[property])
          }
          current.values.forEach { recurse(it) }
        }

        is List<*> -> {
          current.forEach { recurse(it) }
        }
      }
    }

    recurse(data)
    return if (results.isEmpty()) null else if (results.size == 1) results else results
  }

  private fun applyFilter(data: Any?, expression: FilterExpression): Any? {
    if (data !is List<*>) return null

    return data.filter { item ->
      expression.evaluate(item)
    }
  }

  private fun getUnion(data: Any?, items: List<String>): Any? {
    val results = mutableListOf<Any?>()

    for (item in items) {
      val result = when {
        item.toIntOrNull() != null -> getIndex(data, item.toInt())
        else -> getProperty(data, item)
      }
      if (result != null) {
        results.add(result)
      }
    }

    return if (results.isEmpty()) null else results
  }

  private fun executeFunction(data: Any?, name: String): Any? {
    return when (name) {
      "min" -> {
        when (data) {
          is List<*> -> data.filterIsInstance<Number>().minOfOrNull { it.toDouble() }?.let {
            if (data.all { it is Int }) it.toInt() else it
          }

          else -> null
        }
      }

      "max" -> {
        when (data) {
          is List<*> -> data.filterIsInstance<Number>().maxOfOrNull { it.toDouble() }?.let {
            if (data.all { it is Int }) it.toInt() else it
          }

          else -> null
        }
      }

      "avg" -> {
        when (data) {
          is List<*> -> {
            val numbers = data.filterIsInstance<Number>()
            if (numbers.isEmpty()) null else numbers.map { it.toDouble() }.average()
          }

          else -> null
        }
      }

      "sum" -> {
        when (data) {
          is List<*> -> {
            val numbers = data.filterIsInstance<Number>()
            if (numbers.all { it is Int }) {
              numbers.sumOf { it.toInt() }
            } else {
              numbers.sumOf { it.toDouble() }
            }
          }

          else -> null
        }
      }

      "length" -> {
        when (data) {
          is List<*> -> data.size
          is String -> data.length
          is Map<*, *> -> data.size
          else -> null
        }
      }

      "keys" -> {
        when (data) {
          is Map<*, *> -> data.keys.toList()
          else -> null
        }
      }

      "concat" -> {
        when (data) {
          is Map<*, *> -> {
            val lists = data.values.filterIsInstance<List<*>>()
            if (lists.isNotEmpty()) {
              lists.flatten()
            } else {
              null
            }
          }

          is List<*> -> {
            if (data.all { it is String }) {
              data.joinToString("")
            } else {
              data
            }
          }

          else -> null
        }
      }

      else -> null
    }
  }
}


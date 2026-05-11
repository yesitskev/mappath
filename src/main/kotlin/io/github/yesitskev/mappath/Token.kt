package io.github.yesitskev.mappath

internal sealed class Token {
  object Root : Token()
  object At : Token()
  object Dot : Token()
  object RecursiveDescent : Token()
  object LeftBracket : Token()
  object RightBracket : Token()
  object Wildcard : Token()
  object LeftParen : Token()
  object RightParen : Token()
  object Comma : Token()
  object Colon : Token()
  object Question : Token()
  data class String(val value: kotlin.String) : Token()
  data class Number(val value: Any) : Token()
  data class Identifier(val name: kotlin.String) : Token()
  data class Operator(val op: kotlin.String) : Token()
}

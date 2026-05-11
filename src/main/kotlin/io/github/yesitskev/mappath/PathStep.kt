package io.github.yesitskev.mappath

internal sealed class PathStep {
  object Root : PathStep()
  data class Property(val name: String) : PathStep()
  data class Index(val index: Int) : PathStep()
  object Wildcard : PathStep()
  data class Slice(val start: Int?, val end: Int?, val step: Int) : PathStep()
  data class RecursiveDescent(val property: String) : PathStep()
  data class Filter(val expression: FilterExpression) : PathStep()
  data class Union(val items: List<String>) : PathStep()
  data class Function(val name: String) : PathStep()
}

package com.dev.usdi_wallet.domain.credential

enum class PredicateOperator(val symbol: String) {
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
}

data class Predicate(
    val name: String,
    val operator: PredicateOperator,
    val value: Int,
)
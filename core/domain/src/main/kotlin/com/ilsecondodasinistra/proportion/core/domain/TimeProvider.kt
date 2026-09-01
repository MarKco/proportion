package com.ilsecondodasinistra.proportion.core.domain

/** Injected so that timestamps are deterministic in tests. */
fun interface TimeProvider {
    fun now(): Long
}

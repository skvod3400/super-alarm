package com.skvod3400.superalarm

import kotlin.random.Random

/** Easy two-digit addition or subtraction, e.g. 22 + 34 or 78 − 25. */
data class MathProblem(val a: Int, val b: Int, val isAddition: Boolean) {
    val answer get() = if (isAddition) a + b else a - b

    val text get() = "$a ${if (isAddition) "+" else "−"} $b = ?"

    companion object {
        fun random(): MathProblem {
            val a = Random.nextInt(10, 100)
            val b = Random.nextInt(10, 100)
            return if (Random.nextBoolean()) {
                MathProblem(a, b, isAddition = true)
            } else {
                MathProblem(maxOf(a, b), minOf(a, b), isAddition = false)
            }
        }
    }
}

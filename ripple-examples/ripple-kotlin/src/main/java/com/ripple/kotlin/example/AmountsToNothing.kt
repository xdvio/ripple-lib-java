package com.ripple.kotlin.example

import com.ripple.core.coretypes.Amount

operator fun Amount.unaryMinus() = this.negate()!!
operator fun Amount.plus(b: Amount) = this.add(b)!!

private fun amounts(vararg amounts: String) =
        amounts.map(Amount::fromIOUString)

private fun claim(c: Boolean, that:String = "all", willBe: Boolean=true) =
        if (c != willBe) throw AssertionError()
        else println("$that == $willBe OK")

object AmountsToNothing {
    @JvmStatic
    fun main(args: Array<String>) {
        val (a1, a2) = amounts("1/USD", "3/USD")
        val a3 = a1 + a2

        // Translated to `a.compareTo(b) < 0`
        claim(a1 < a3, "a1 < a2")
        claim(a3 < a1, "a2 < a1", false)
        // Really, it AmountsToNothing!
        claim((-a3 + a3).isZero, "-a3 + a3 = 0")
    }
}
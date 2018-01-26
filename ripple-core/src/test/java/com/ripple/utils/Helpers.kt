package com.ripple.utils

import com.ripple.core.coretypes.STObject
import org.json.JSONArray
import org.json.JSONObject

fun String.normalizeJSON() = JSONObject(this).toString(2)!!
fun STObject.normalizedJSON() = toJSONObject().toString(2)!!
fun String.noSpace() = this.replace(Regex("\\s+"), "")

fun JSONArray.isEqual(other: JSONArray): Boolean {
    var failed = false
    other.forEachIndexed {ix, obj->
        if (!isEqual(other[ix], obj)) {
            failed = true
            return@forEachIndexed
        }
    }

    return !failed && other.length() == length()
}

fun isEqual(thisV: Any, otherV: Any): Boolean {
    return when (thisV) {
        is JSONObject -> if (otherV is JSONObject) {
            thisV.isEqual(otherV)
        } else {
            false
        }
        is JSONArray -> if (otherV is JSONArray) {
            thisV.isEqual(otherV)
        } else {
            false
        }
        is Int -> when (otherV) {
            is Int -> thisV == (otherV)
            is Long -> thisV.toLong() == (otherV)
            else -> false
        }
        else -> {
            thisV == otherV
        }
    }
}

fun JSONObject.isEqual(other: JSONObject): Boolean {
    for (key in other.keys()) {
        if (!isEqual(this[key], other[key])) {
            println("${this[key]}, ${other[key]} ${this[key] == other[key]}")
            return false
        }
    }
    return true
}
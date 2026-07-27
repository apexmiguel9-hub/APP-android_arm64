package com.epai.oblender.utils.string

fun Throwable.getMessageOrToString(): String = message ?: toString()
fun String.isNotEmptyOrBlank(): Boolean = this.isNotEmpty() && this.isNotBlank()

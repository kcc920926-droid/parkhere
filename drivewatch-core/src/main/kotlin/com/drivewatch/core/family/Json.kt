package com.drivewatch.core.family

/**
 * 가족 서버와 주고받는 JSON만 다루는 작은 구현이다. 이 모듈에 의존성을 두지 않으려고 직접 뒀다.
 * 읽은 값은 Map<String, Any?>, List<Any?>, String, Double, Boolean, null 가운데 하나다.
 */
internal object Json {
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value)
            is Int, is Long -> out.append(value)
            is Number -> {
                val number = value.toDouble()
                require(number.isFinite()) { "JSON에 쓸 수 없는 수입니다: $number" }
                out.append(if (number == Math.rint(number) && Math.abs(number) < 1e15) number.toLong().toString() else number.toString())
            }
            is String -> quote(out, value)
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) out.append(',')
                    quote(out, key as String)
                    out.append(':')
                    append(out, item)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    append(out, item)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("JSON에 쓸 수 없는 값입니다: ${value::class}")
        }
    }

    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        for (char in text) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' -> out.append(String.format("\\u%04x", char.code))
                else -> out.append(char)
            }
        }
        out.append('"')
    }

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipSpace()
        reader.check(reader.at == text.length)
        return value
    }

    private class Reader(private val text: String) {
        var at = 0

        fun check(ok: Boolean) {
            if (!ok) throw IllegalArgumentException("JSON이 올바르지 않습니다 (${at}번째 글자)")
        }

        fun skipSpace() {
            while (at < text.length && text[at] in " \t\r\n") at++
        }

        private fun take(char: Char): Boolean {
            skipSpace()
            if (at < text.length && text[at] == char) { at++; return true }
            return false
        }

        fun value(): Any? {
            skipSpace()
            check(at < text.length)
            return when (val char = text[at]) {
                '{' -> obj()
                '[' -> list()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> { check(char == '-' || char.isDigit()); number() }
            }
        }

        private fun word(word: String, value: Any?): Any? {
            check(text.startsWith(word, at))
            at += word.length
            return value
        }

        private fun number(): Double {
            val start = at
            while (at < text.length && (text[at].isDigit() || text[at] in "+-.eE")) at++
            return text.substring(start, at).toDoubleOrNull() ?: run { check(false); 0.0 }
        }

        private fun string(): String {
            check(take('"'))
            val out = StringBuilder()
            while (true) {
                check(at < text.length)
                val char = text[at++]
                when (char) {
                    '"' -> return out.toString()
                    '\\' -> {
                        check(at < text.length)
                        when (val escaped = text[at++]) {
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'u' -> {
                                check(at + 4 <= text.length)
                                val code = text.substring(at, at + 4).toIntOrNull(16)
                                check(code != null)
                                out.append(code!!.toChar())
                                at += 4
                            }
                            '"', '\\', '/' -> out.append(escaped)
                            else -> check(false)
                        }
                    }
                    else -> out.append(char)
                }
            }
        }

        private fun list(): List<Any?> {
            check(take('['))
            val items = mutableListOf<Any?>()
            if (take(']')) return items
            do items += value() while (take(','))
            check(take(']'))
            return items
        }

        private fun obj(): Map<String, Any?> {
            check(take('{'))
            val fields = linkedMapOf<String, Any?>()
            if (take('}')) return fields
            do {
                skipSpace()
                val key = string()
                check(take(':'))
                fields[key] = value()
            } while (take(','))
            check(take('}'))
            return fields
        }
    }
}

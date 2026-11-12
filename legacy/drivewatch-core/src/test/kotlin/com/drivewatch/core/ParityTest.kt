package com.drivewatch.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** 웹 MVP(JS)가 낸 결과(expected.txt)와 Kotlin 이식본의 결과가 한 줄도 다르지 않은지 본다. */
class ParityTest {
    @Test
    fun `kotlin port matches the web MVP line by line`() {
        val expected = checkNotNull(javaClass.getResource("/parity/expected.txt")) {
            "expected.txt 가 없습니다. node tools/dump-parity.js 로 다시 만드세요."
        }.readText().trimEnd().lines()
        val actual = ParityDump.lines()
        expected.zip(actual).forEachIndexed { index, (want, got) ->
            assertEquals(want, got, "line ${index + 1}")
        }
        assertEquals(expected.size, actual.size, "line count")
    }
}

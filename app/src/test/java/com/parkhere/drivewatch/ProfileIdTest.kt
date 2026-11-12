package com.parkhere.drivewatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A server answer that arrives after the driver list changed must land on the driver it was for.
 * With list positions it landed on whoever moved into that slot (a token or family on the wrong driver).
 */
class ProfileIdTest {
    @Test fun lateAnswerFindsItsDriverAfterTheListShifts() {
        val s = AppState
        s.profiles.clear()
        s.profiles.addAll(listOf(Profile("김정숙", emptyList()), Profile("", emptyList(), onboarded = false), Profile("박영호", emptyList())))
        val park = s.profiles[2].id
        s.cur = 2
        s.pick(0) // drops the unfinished blank driver: 박영호 moves from index 2 to 1
        s.updateId(park) { it.copy(carToken = "token-for-park") }
        assertEquals("token-for-park", s.profiles.first { it.name == "박영호" }.carToken)
        assertNull(s.profiles.first { it.name == "김정숙" }.carToken)
    }

    @Test fun answerForARemovedDriverIsDropped() {
        val s = AppState
        s.profiles.clear()
        s.profiles.addAll(listOf(Profile("김정숙", emptyList()), Profile("박영호", emptyList())))
        val gone = Profile("임시", emptyList()).id
        s.updateId(gone) { it.copy(carToken = "x") }
        assertEquals(listOf(null, null), s.profiles.map { it.carToken })
    }
}

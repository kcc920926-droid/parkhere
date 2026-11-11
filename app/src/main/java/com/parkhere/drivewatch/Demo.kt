package com.parkhere.drivewatch

/**
 * Demo inputs (시연용). Past weeks are synthetic, and navigation events (turn guidance, re-routes)
 * are scripted because NaviHelper has not been verified yet. Gear, turn signal, brake and speed
 * come from the real vehicle when the Pleos Vehicle SDK is available.
 */
object Demo {
    const val PARENT = "김정숙"

    /** Places on the home screen and the junction each one tends to trouble the driver at. */
    val PLACES = listOf("병원" to "중앙대로 합류", "성당" to "성당 앞 사거리", "마트" to "시장 앞 좌회전")
    fun junction(place: String) = PLACES.first { it.first == place }.second

    /** 11 past weeks and a calm start to this week. changing = true makes last week worse already. */
    fun seed(changing: Boolean = false): List<Trip> {
        val baseReroutes = listOf(0, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1) // index = weekAgo
        val trips = mutableListOf<Trip>()
        for (w in 1..11) {
            val reroutes = if (w == 1 && changing) 3 else baseReroutes[w]
            trips += Trip(w, 10.0, familiar = true, night = false, reroutes = reroutes, turnsGuided = 7, signalsUsed = 7, hardBrakes = 0, place = "성당")
            trips += Trip(w, 10.0, familiar = true, night = false, reroutes = 0, turnsGuided = 6, signalsUsed = 6, hardBrakes = if (w % 2 == 0) 1 else 0, place = "병원")
            trips += Trip(w, 10.0, familiar = true, night = false, reroutes = 0, turnsGuided = 7, signalsUsed = 6, hardBrakes = 0, place = "마트")
            trips += Trip(w, 15.0, familiar = false, night = false, reroutes = 1, turnsGuided = 5, signalsUsed = 5, hardBrakes = 0)
        }
        trips += Trip(0, 10.0, familiar = true, night = false, reroutes = 0, turnsGuided = 7, signalsUsed = 7, hardBrakes = 0, place = "마트")
        trips += Trip(0, 10.0, familiar = true, night = false, reroutes = 0, turnsGuided = 6, signalsUsed = 6, hardBrakes = 0, place = "성당")
        return trips
    }

    /** One synthetic week where the 성당 route keeps re-routing (used by "4주 뒤로"). */
    fun strugglingWeek(): List<Trip> = listOf(
        Trip(0, 10.0, familiar = true, night = false, reroutes = 1, turnsGuided = 7, signalsUsed = 7, hardBrakes = 0, place = "성당"),
        Trip(0, 10.0, familiar = true, night = false, reroutes = 1, turnsGuided = 6, signalsUsed = 6, hardBrakes = 0, place = "성당"),
        Trip(0, 10.0, familiar = true, night = false, reroutes = 1, turnsGuided = 7, signalsUsed = 7, hardBrakes = 0, place = "마트"),
    )

    /** Scripted navigation for one drive. Seconds are from the moment the gear goes to D. */
    fun script(place: String, comfortable: Boolean): List<Pair<Int, NavEvent>> {
        val junction = junction(place)
        return if (comfortable) listOf(
            2 to NavEvent.Turn("우회전", "큰길 진입", signal = true),
            6 to NavEvent.Turn("우회전", "$place 방면 큰길", signal = true),
            10 to NavEvent.Turn("우회전", "$place 주차장 진입", signal = true),
            13 to NavEvent.Arrive(11.0),
        ) else listOf(
            2 to NavEvent.Turn("우회전", "첫 사거리", signal = true),
            5 to NavEvent.Turn("좌회전", "시장 앞", signal = false),
            8 to NavEvent.Reroute(junction),
            11 to NavEvent.Reroute(junction),
            13 to NavEvent.HardBrake,
            15 to NavEvent.Turn("우회전", "$place 주차장 진입", signal = true),
            18 to NavEvent.Arrive(10.0),
        )
    }
}

/** What the navigation reports during a drive (시연용 until NaviHelper is wired in). */
sealed class NavEvent {
    data class Turn(val dir: String, val point: String, val signal: Boolean) : NavEvent()
    data class Reroute(val point: String) : NavEvent()
    /** Used only when no real brake/speed signal is available. */
    data object HardBrake : NavEvent()
    data class Arrive(val km: Double) : NavEvent()
}

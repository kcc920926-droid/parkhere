package com.parkhere.drivewatch

import androidx.annotation.DrawableRes

/**
 * The app's icon set. Sources are SVGs in app/design/icons/, turned into res/drawable/ic_*.xml by
 * tools/svg2vector.py. One stroke style (24 grid, 2 px, round ends) and tinted with theme colours,
 * so icons look the same on every car display instead of depending on the system emoji font.
 */
enum class Ic(@DrawableRes val res: Int) {
    ARROW_LEFT(R.drawable.ic_arrow_left),
    CALENDAR(R.drawable.ic_calendar),
    CAR(R.drawable.ic_car),
    CHECK(R.drawable.ic_check),
    CHEVRON_DOWN(R.drawable.ic_chevron_down),
    CHEVRON_LEFT(R.drawable.ic_chevron_left),
    CHEVRON_RIGHT(R.drawable.ic_chevron_right),
    CHEVRON_UP(R.drawable.ic_chevron_up),
    CLOSE(R.drawable.ic_close),
    CLOUD(R.drawable.ic_cloud),
    FAMILY(R.drawable.ic_family),
    HIGHWAY(R.drawable.ic_highway),
    LOCK(R.drawable.ic_lock),
    MAP(R.drawable.ic_map),
    MOON(R.drawable.ic_moon),
    PERSON(R.drawable.ic_person),
    PIN(R.drawable.ic_pin),
    PLACE_CHURCH(R.drawable.ic_place_church),
    PLACE_HOSPITAL(R.drawable.ic_place_hospital),
    PLACE_MART(R.drawable.ic_place_mart),
    PLAY(R.drawable.ic_play),
    RAIN(R.drawable.ic_rain),
    RESET(R.drawable.ic_reset),
    SPEAKER(R.drawable.ic_speaker),
    SUN(R.drawable.ic_sun),
    TAXI(R.drawable.ic_taxi),
    THUMBS_UP(R.drawable.ic_thumbs_up),
    TIMER(R.drawable.ic_timer),
}

/** Icon for a home-screen destination (Demo.PLACES). */
fun placeIcon(place: String) = when (place) {
    "병원" -> Ic.PLACE_HOSPITAL
    "성당" -> Ic.PLACE_CHURCH
    "마트" -> Ic.PLACE_MART
    else -> Ic.PIN
}

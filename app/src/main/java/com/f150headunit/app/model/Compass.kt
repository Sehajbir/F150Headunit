package com.f150headunit.app.model

/** Converts a heading in degrees to a 16-wind compass point ("NW"). */
object Compass {

    private val DIRECTIONS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )

    fun fromDegrees(degrees: Float): String {
        val normalized = ((degrees % 360f) + 360f) % 360f
        val index = ((normalized + 11.25f) / 22.5f).toInt() % DIRECTIONS.size
        return DIRECTIONS[index]
    }
}

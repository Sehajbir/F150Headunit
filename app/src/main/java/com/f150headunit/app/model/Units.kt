package com.f150headunit.app.model

import java.util.Locale

/** Unit conversions + user-facing formatting helpers. */
object Units {

    const val MPH_PER_KMH = 0.621371f

    fun kmhToMph(kmh: Float): Float = kmh * MPH_PER_KMH
    fun mphToKmh(mph: Float): Float = mph / MPH_PER_KMH
    fun celsiusToFahrenheit(celsius: Float): Float = celsius * 9f / 5f + 32f
    fun fahrenheitToCelsius(fahrenheit: Float): Float = (fahrenheit - 32f) * 5f / 9f

    /** "72°F" or "22°C". */
    fun formatTemperature(tempF: Float, celsius: Boolean): String {
        val value = if (celsius) fahrenheitToCelsius(tempF) else tempF
        return String.format(Locale.US, "%.0f°%s", value, if (celsius) "C" else "F")
    }

    /** "325 mi" or "523 km". */
    fun formatRange(miles: Int, metric: Boolean): String =
        if (metric) {
            String.format(Locale.US, "%d km", (miles / MPH_PER_KMH).toInt())
        } else {
            String.format(Locale.US, "%d mi", miles)
        }

    /** Whole-number speed value in the requested unit, e.g. "45". */
    fun formatSpeedValue(speedMph: Float, metric: Boolean): String {
        val value = if (metric) mphToKmh(speedMph) else speedMph
        return String.format(Locale.US, "%.0f", value)
    }
}

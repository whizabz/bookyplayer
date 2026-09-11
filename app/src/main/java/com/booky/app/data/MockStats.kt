package com.booky.app.data

data class TimeSavedRow(
    val label: String,
    val value: String,
    val icon: TimeSavedKind,
)

enum class TimeSavedKind { Skipping, Speed, TrimSilence, AutoSkipping }

object MockStats {
    const val sinceLabel = "Since 27 March 2016 you've listened for"
    const val listeningHeadline = "102 days 16 hours"
    const val flavorText = "During which time 36,973,750 babies were born.\nWahhh!"
    val months = listOf("Apr", "May", "Jun", "Jul", "Aug", "Sept")
    val weekdayLabels = listOf("Mon", "", "Wed", "", "Fri", "", "")
    val totalSaved = "20 days 1 hour"

    val timeSaved = listOf(
        TimeSavedRow("Skipping", "1 day 14 hours", TimeSavedKind.Skipping),
        TimeSavedRow("Variable Speed", "12 days 10 hours", TimeSavedKind.Speed),
        TimeSavedRow("Trim Silence", "5 days 22 hours", TimeSavedKind.TrimSilence),
        TimeSavedRow("Auto Skipping", "1 hour 16 mins", TimeSavedKind.AutoSkipping),
    )

    /** 7 weekdays × 26 weeks, intensity 0–4 for the activity heatmap. */
    val activity: List<List<Int>> = listOf(
        listOf(0, 0, 1, 0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 3, 0, 0, 0, 0, 2, 0, 0, 0, 1, 0, 2, 0),
        listOf(0, 0, 0, 0, 1, 1, 0, 0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        listOf(0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 1, 0, 0, 0, 0, 1, 2, 0, 0, 0, 0, 0, 0, 3, 2, 0),
        listOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0),
        listOf(0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 0),
        listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0),
        listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    )
}

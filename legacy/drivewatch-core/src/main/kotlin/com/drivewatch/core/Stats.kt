package com.drivewatch.core

/** 값이 있고, 유한하고, 0 이상일 때만 비교에 쓴다. */
internal fun Double?.isUsable(): Boolean = this != null && this.isFinite() && this >= 0.0

internal fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

/** 소수 첫째 자리 반올림. 웹 MVP의 Math.round(value * 10) / 10 과 같은 규칙(0.5는 올림). */
internal fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

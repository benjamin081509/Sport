package de.vitalcoach.core

import java.time.LocalDate

/**
 * Kompakte Tageswerte, die die App lokal speichert. So stehen auch dann Langzeit-Trends
 * (bis zu einem Jahr) zur Verfügung, wenn die Pulsrohdaten nur für einige Monate gelesen werden.
 */
data class DailySnapshot(
    val date: LocalDate,
    val restingHr: Double? = null,
    val hrv: Double? = null,
    val sleepMin: Double? = null,
    val sleepScore: Int? = null,
    val load: Double? = null,
    val steps: Long? = null,
    val recovery: Int? = null,
    val avgHr: Double? = null,
    val stress: Double? = null,
    val weight: Double? = null,
)

object SnapshotCodec {
    private const val HEADER = "date\trhr\thrv\tsleepMin\tsleepScore\tload\tsteps\trecovery\tavgHr\tstress\tweight"

    fun encode(list: List<DailySnapshot>): String = buildString {
        appendLine(HEADER)
        list.sortedBy { it.date }.forEach { s ->
            appendLine(
                listOf(
                    s.date.toString(), s.restingHr.f(), s.hrv.f(), s.sleepMin.f(), s.sleepScore?.toString() ?: "",
                    s.load.f(), s.steps?.toString() ?: "", s.recovery?.toString() ?: "", s.avgHr.f(), s.stress.f(), s.weight.f(),
                ).joinToString("\t"),
            )
        }
    }

    fun decode(text: String): List<DailySnapshot> = text.lineSequence()
        .drop(1)
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val c = line.split('\t')
            runCatching {
                DailySnapshot(
                    date = LocalDate.parse(c[0]),
                    restingHr = c.getOrNull(1).d(),
                    hrv = c.getOrNull(2).d(),
                    sleepMin = c.getOrNull(3).d(),
                    sleepScore = c.getOrNull(4)?.toIntOrNull(),
                    load = c.getOrNull(5).d(),
                    steps = c.getOrNull(6)?.toLongOrNull(),
                    recovery = c.getOrNull(7)?.toIntOrNull(),
                    avgHr = c.getOrNull(8).d(),
                    stress = c.getOrNull(9).d(),
                    weight = c.getOrNull(10).d(),
                )
            }.getOrNull()
        }.toList()

    /** Neue Werte haben Vorrang, fehlende Werte werden aus dem Speicher übernommen. Behält max. [keepDays] Tage. */
    fun merge(old: List<DailySnapshot>, new: List<DailySnapshot>, keepDays: Long = 400): List<DailySnapshot> {
        val map = LinkedHashMap<LocalDate, DailySnapshot>()
        old.forEach { map[it.date] = it }
        new.forEach { n ->
            val o = map[n.date]
            map[n.date] = if (o == null) {
                n
            } else {
                DailySnapshot(
                    date = n.date,
                    restingHr = n.restingHr ?: o.restingHr,
                    hrv = n.hrv ?: o.hrv,
                    sleepMin = n.sleepMin ?: o.sleepMin,
                    sleepScore = n.sleepScore ?: o.sleepScore,
                    load = n.load ?: o.load,
                    steps = n.steps ?: o.steps,
                    recovery = n.recovery ?: o.recovery,
                    avgHr = n.avgHr ?: o.avgHr,
                    stress = n.stress ?: o.stress,
                    weight = n.weight ?: o.weight,
                )
            }
        }
        val newest = map.keys.maxOrNull() ?: return emptyList()
        return map.values.filter { !it.date.isBefore(newest.minusDays(keepDays)) }.sortedBy { it.date }
    }

    fun fromResult(r: AnalysisResult): List<DailySnapshot> = r.days.map { d ->
        DailySnapshot(
            date = d.date,
            restingHr = d.restingHr,
            hrv = d.hrv,
            sleepMin = d.sleep?.asleepMin,
            sleepScore = d.sleep?.score,
            load = if (d.loadFromHr) d.load else null,
            steps = d.steps,
            recovery = d.recovery?.value,
            avgHr = d.avgHr,
            stress = d.stressIndex,
            weight = d.weight,
        )
    }.filter { it.restingHr != null || it.hrv != null || it.sleepMin != null || it.load != null || it.steps != null }

    private fun Double?.f(): String = this?.let { String.format(java.util.Locale.ROOT, "%.2f", it) } ?: ""
    private fun String?.d(): Double? = this?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
}

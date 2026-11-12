package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.detection.ServiceMetrics
import com.reelometro.app.stats.Format

/**
 * Rendimiento del servicio ([ServiceMetrics]) en la pantalla de Calibración (una línea) y en la cabecera del registro
 * que se comparte (varias), en `res/values/copy_calibration.xml`. Formato de consola: «clave valor», sin plurales.
 * Las plataformas van con su nombre corto (marcas: no se traducen).
 */
object CalibrationMetricsCopy {

    /** Clave de la línea en la consola: «RENDIMIENTO». */
    val KEY: String get() = str(R.string.calibration_metrics_key)

    /** «Sin datos del servicio desde que arrancó.» */
    val NONE: String get() = str(R.string.calibration_metrics_none)

    /** Una línea para la consola: «84 ev/min · 0,3 ms de media (máx. 12) · 141 nodos por lectura». */
    fun summary(s: ServiceMetrics.Snapshot): String {
        if (s.handled == 0L) return NONE
        return str(
            R.string.calibration_metrics_summary,
            rate(s, s.handled),
            ms(s.avgEventMs),
            ms(s.maxEventMs),
            s.avgNodesPerRead?.let { Format.decimal(it, maxFractionDigits = 0) } ?: unknown(),
        )
    }

    /** Las líneas de la cabecera del registro exportado (sin datos personales: solo cifras y nombres de apps). */
    fun exportLines(s: ServiceMetrics.Snapshot): List<String> = buildList {
        add(str(R.string.calibration_metrics_title, Format.duration(s.elapsedMs), Format.duration(s.activeMs), Format.integer(s.connects)))
        if (s.handled == 0L) {
            add(NONE)
            return@buildList
        }
        for ((platform, c) in s.events) {
            add(
                str(
                    R.string.calibration_metrics_events,
                    platform.shortName,
                    Format.integer(c.total),
                    rate(s, c.total),
                    Format.integer(c.scroll),
                    Format.integer(c.window),
                    Format.integer(c.click),
                    Format.integer(c.content),
                ),
            )
        }
        add(str(R.string.calibration_metrics_content, Format.integer(s.contentOutsideQuick)))
        add(str(R.string.calibration_metrics_handler, Format.integer(s.handled), ms(s.avgEventMs), ms(s.maxEventMs), Format.integer(s.slow)))
        add(str(R.string.calibration_metrics_lag, Format.integer(s.lagMaxMs), Format.integer(s.lagSlow)))
        for ((platform, r) in s.reads) {
            add(
                str(
                    R.string.calibration_metrics_reads,
                    platform.shortName,
                    Format.integer(r.count),
                    Format.integer(r.empty),
                    Format.decimal(r.avgNodes, maxFractionDigits = 0),
                    Format.integer(r.nodesMax),
                    ms(r.avgMs),
                    ms(r.maxMs),
                ),
            )
        }
        if (s.unreadable > 0) add(str(R.string.calibration_metrics_unreadable, Format.integer(s.unreadable)))
        add(str(R.string.calibration_metrics_ipc, Format.integer(s.childFetches), Format.integer(s.parentFetches), Format.integer(s.finderQueries)))
        add(str(R.string.calibration_metrics_presence, Format.integer(s.presence.count), ms(s.presence.avgMs), ms(s.presence.maxMs)))
        if (s.quick.count > 0) {
            add(
                str(
                    R.string.calibration_metrics_quick,
                    Format.integer(s.quick.count),
                    ms(s.quick.avgMs),
                    ms(s.quick.maxMs),
                    Format.integer(s.quickChildFetches),
                ),
            )
        }
        if (s.captured > 0) add(str(R.string.calibration_metrics_capturing, Format.percent(s.capturedFraction)))
    }

    /** Lo que sustituye a un nombre de contacto o grupo en el registro exportado: «[contacto 1]». */
    fun redacted(n: Int): String = str(R.string.calibration_redacted, Format.integer(n))

    private fun rate(s: ServiceMetrics.Snapshot, count: Long): String =
        if (s.rateKnown) Format.decimal(s.perMinute(count)) else unknown()

    /** Milisegundos: dos decimales por debajo de 1 ms (casi todos los eventos), uno hasta 10 y ninguno a partir de ahí. */
    private fun ms(value: Double): String = Format.decimal(
        value,
        maxFractionDigits = when {
            value < 1 -> 2
            value < 10 -> 1
            else -> 0
        },
    )

    private fun unknown(): String = str(R.string.calibration_metrics_rate_unknown)
}

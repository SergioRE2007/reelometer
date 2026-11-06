package com.reelometro.app.data

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.AssumptionViolatedException
import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.internal.bytecode.Sandbox
import java.lang.reflect.Method
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/*
 * Utilidades de los tests de la capa de datos (Robolectric).
 *
 * Robolectric 4.17 no conoce el SDK 37 y con el SDK 36 falla en JDK 25 (accede a internos de
 * java.base), así que los tests de Android usan el 35. `application = Application::class` evita
 * arrancar ReelometroApp (programa WorkManager, que Robolectric no inicializa).
 */

const val ROBOLECTRIC_SDK = 35

val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

/** Instante de una hora local. */
fun at(date: LocalDate, hour: Int, minute: Int, zone: ZoneId = MADRID, second: Int = 0): Long =
    date.atTime(hour, minute, second).atZone(zone).toInstant().toEpochMilli()

fun inMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .build()

/** Reloj que el test mueve a mano. */
class MutableClock(@Volatile var now: Instant, private val zoneId: ZoneId = MADRID) : Clock() {
    constructor(epochMs: Long, zoneId: ZoneId = MADRID) : this(Instant.ofEpochMilli(epochMs), zoneId)

    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now

    fun set(epochMs: Long) {
        now = Instant.ofEpochMilli(epochMs)
    }
}

/** Ticker que solo avisa cuando el test llama a [tick] (además de al suscribirse). */
class ManualTicker : Ticker {
    private val flow = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16).apply { tryEmit(Unit) }
    override fun ticks(everyMinutes: Int?): Flow<Unit> = flow
    fun tick() {
        flow.tryEmit(Unit)
    }
}

/** Espera (tiempo real) al primer valor que cumpla [predicate]. */
suspend fun <T> Flow<T>.awaitFirst(timeoutMs: Long = 10_000, predicate: (T) -> Boolean): T =
    withTimeout(timeoutMs) { first(predicate) }

/**
 * Instrumentation para `MigrationTestHelper` en Robolectric:
 * - sirve los esquemas exportados (`app/schemas`) como assets, que es donde los busca el helper
 *   (se empaquetan en un zip temporal con un `AssetManager` propio; así no hay que tocar Gradle);
 * - en un anfitrión Windows, las rutas de base de datos se devuelven con `/`: androidx.sqlite
 *   compara nombres con `substringAfterLast('/')` y con `\` fallaría (en Android no pasa).
 */
class SchemaAssetsInstrumentation private constructor(
    private val base: Instrumentation,
    private val schemaAssets: AssetManager,
) : Instrumentation() {

    private val target: Context by lazy {
        object : ContextWrapper(base.targetContext) {
            override fun getDatabasePath(name: String): File = PortableFile(super.getDatabasePath(name).path)
        }
    }

    override fun getContext(): Context = object : ContextWrapper(base.targetContext) {
        override fun getAssets(): AssetManager = schemaAssets
    }

    override fun getTargetContext(): Context = target

    /** Ruta absoluta siempre con `/` (Windows también la acepta). */
    private class PortableFile(path: String) : File(path) {
        override fun getAbsolutePath(): String = super.getAbsolutePath().replace('\\', '/')
    }

    companion object {
        fun create(): Instrumentation {
            val dir = listOf(File("schemas"), File("app/schemas")).firstOrNull { it.isDirectory }
                ?: error("No encuentro app/schemas (directorio de trabajo: ${File(".").absolutePath})")
            val zip = File.createTempFile("room-schemas", ".zip").apply { deleteOnExit() }
            ZipOutputStream(zip.outputStream()).use { out ->
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    out.putNextEntry(ZipEntry("assets/" + file.relativeTo(dir).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val cookie = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                .invoke(assets, zip.absolutePath) as Int
            check(cookie != 0) { "No se pudieron cargar los esquemas como assets" }
            return SchemaAssetsInstrumentation(InstrumentationRegistry.getInstrumentation(), assets)
        }
    }
}

/**
 * Da por omitido (no fallido) un test cuando el SQLite nativo de Robolectric no se puede cargar.
 *
 * Pasa en Windows con Smart App Control o WDAC activos: Robolectric extrae a %TEMP% una DLL sin
 * firmar (`libandroid_runtime.dll`) y el sistema la bloquea («Una directiva de Control de
 * aplicaciones bloqueó este archivo»). En el resto de equipos estos tests se ejecutan con normalidad.
 * Úsala la primera: `@get:Rule(order = 0) val nativeSqlite = NativeSqliteRule()`, junto con
 * `@RunWith(ReelRobolectricRunner::class)` (el primer intento de carga ocurre fuera de las reglas).
 */
class NativeSqliteRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } catch (t: Throwable) {
                val blocked = generateSequence(t) { it.cause }.take(10).any {
                    it is UnsatisfiedLinkError && it.message.orEmpty().let { m ->
                        "nativeBinding" in m || "android_runtime" in m || "nativeruntime" in m
                    }
                }
                if (blocked) {
                    throw AssumptionViolatedException("SQLite nativo de Robolectric bloqueado en este equipo (${t.message?.take(160)})")
                }
                throw t
            }
        }
    }
}

/**
 * `RobolectricTestRunner` que da por omitido el test cuando Robolectric no puede cargar su runtime
 * nativo (ver [NativeSqliteRule]): el primer test de la JVM lo intenta en `beforeTest`, antes de las reglas.
 */
class ReelRobolectricRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    override fun beforeTest(sandbox: Sandbox, method: FrameworkMethod, bootstrappedMethod: Method) {
        try {
            super.beforeTest(sandbox, method, bootstrappedMethod)
        } catch (e: UnsatisfiedLinkError) {
            throw AssumptionViolatedException("Runtime nativo de Robolectric bloqueado en este equipo (${e.message?.take(160)})")
        }
    }
}

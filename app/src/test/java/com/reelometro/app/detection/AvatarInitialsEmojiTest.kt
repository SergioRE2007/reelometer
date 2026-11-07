package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarInitialsEmojiTest {
    private fun cps(s: String) = s.codePoints().toArray().joinToString(" ") { "U+%X".format(it) }

    @Test fun disco() = assertEquals(cps("💿"), cps(AvatarRules.initials("💿")))
    @Test fun corazon() = assertEquals(cps("❤️"), cps(AvatarRules.initials("❤️")))
    @Test fun bandera() = assertEquals(cps("🇪🇸"), cps(AvatarRules.initials("🇪🇸")))
    @Test fun tono() = assertEquals(cps("👍🏽"), cps(AvatarRules.initials("👍🏽")))
    @Test fun zwj() = assertEquals(cps("🧑‍💻"), cps(AvatarRules.initials("🧑‍💻")))
}

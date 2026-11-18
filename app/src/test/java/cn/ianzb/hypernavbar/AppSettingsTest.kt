package cn.ianzb.hypernavbar

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the JSON (de)serialization of [AppSettings].
 *
 * Covers:
 *  - legacy floating-navbar booleans (isFloatingNavbar / isLiquidGlass)
 *  - legacy string rail styles
 *  - new numeric enum values
 *  - defaults when a field is absent (or the input is not valid JSON)
 *  - toJson/fromJson round trip
 */
class AppSettingsTest {

    // ---------------------------------------------------------------- legacy booleans

    @Test
    fun `legacy floating navbar true with liquid glass maps to IOS`() {
        val settings = AppSettings.fromJson(
            """{"isFloatingNavbar":true,"isLiquidGlass":true}"""
        )

        assertEquals(BottomBarStyle.IOS, settings.bottomBarStyle)
        // floating navbar + legacy rail: rail defaults to Off
        assertEquals(RailStyle.Off, settings.railStyle)
    }

    @Test
    fun `legacy floating navbar true without liquid glass maps to HyperOS`() {
        val liquid = AppSettings.fromJson(
            """{"isFloatingNavbar":true,"isLiquidGlass":false}"""
        )
        assertEquals(BottomBarStyle.HyperOS, liquid.bottomBarStyle)
        assertEquals(RailStyle.Off, liquid.railStyle)

        val missing = AppSettings.fromJson("""{"isFloatingNavbar":true}""")
        assertEquals(BottomBarStyle.HyperOS, missing.bottomBarStyle)
        assertEquals(RailStyle.Off, missing.railStyle)
    }

    @Test
    fun `legacy floating navbar false maps to HyperOS and Capsule`() {
        val settings = AppSettings.fromJson(
            """{"isFloatingNavbar":false,"isLiquidGlass":true}"""
        )

        assertEquals(BottomBarStyle.HyperOS, settings.bottomBarStyle)
        assertEquals(RailStyle.Capsule, settings.railStyle)
    }

    @Test
    fun `legacy floating navbar absent defaults to HyperOS and Capsule`() {
        val settings = AppSettings.fromJson("{}")

        assertEquals(BottomBarStyle.HyperOS, settings.bottomBarStyle)
        assertEquals(RailStyle.Capsule, settings.railStyle)
    }

    // ---------------------------------------------------------------- legacy string rail

    @Test
    fun `legacy string rail style is parsed by name`() {
        assertEquals(
            RailStyle.Left,
            AppSettings.fromJson("""{"railStyle":"Left"}""").railStyle,
        )
        assertEquals(
            RailStyle.Off,
            AppSettings.fromJson("""{"railStyle":"Off"}""").railStyle,
        )
        assertEquals(
            RailStyle.Capsule,
            AppSettings.fromJson("""{"railStyle":"Capsule"}""").railStyle,
        )
    }

    @Test
    fun `legacy string rail style wins over floating default`() {
        val settings = AppSettings.fromJson(
            """{"railStyle":"Left","isFloatingNavbar":true}"""
        )

        assertEquals(RailStyle.Left, settings.railStyle)
        // bottom bar style is still derived from the legacy booleans
        assertEquals(BottomBarStyle.HyperOS, settings.bottomBarStyle)
    }

    // ---------------------------------------------------------------- new numeric values

    @Test
    fun `new numeric enum values are parsed and the removed 0 migrates to HyperOS`() {
        assertEquals(
            BottomBarStyle.HyperOS,
            AppSettings.fromJson("""{"bottomBarStyle":0}""").bottomBarStyle,
        )
        assertEquals(
            BottomBarStyle.HyperOS,
            AppSettings.fromJson("""{"bottomBarStyle":1}""").bottomBarStyle,
        )
        assertEquals(
            BottomBarStyle.IOS,
            AppSettings.fromJson("""{"bottomBarStyle":2}""").bottomBarStyle,
        )

        assertEquals(
            RailStyle.Off,
            AppSettings.fromJson("""{"railStyle":0}""").railStyle,
        )
        assertEquals(
            RailStyle.Left,
            AppSettings.fromJson("""{"railStyle":2}""").railStyle,
        )
    }

    @Test
    fun `numeric bottom 0 overrides legacy booleans and migrates to HyperOS`() {
        val settings = AppSettings.fromJson(
            """{"bottomBarStyle":0,"railStyle":1,"isFloatingNavbar":true,"isLiquidGlass":true}"""
        )

        assertEquals(BottomBarStyle.HyperOS, settings.bottomBarStyle)
        assertEquals(RailStyle.Capsule, settings.railStyle)
    }

    @Test
    fun `unknown numeric values fall back to legacy or default`() {
        val withFloating = AppSettings.fromJson(
            """{"bottomBarStyle":99,"isFloatingNavbar":true,"isLiquidGlass":true}"""
        )
        assertEquals(BottomBarStyle.IOS, withFloating.bottomBarStyle)

        val withoutFloating = AppSettings.fromJson("""{"bottomBarStyle":99,"railStyle":99}""")
        assertEquals(BottomBarStyle.HyperOS, withoutFloating.bottomBarStyle)
        assertEquals(RailStyle.Capsule, withoutFloating.railStyle)
    }

    // ---------------------------------------------------------------- defaults

    @Test
    fun `missing fields use documented defaults`() {
        val settings = AppSettings.fromJson("{}")

        assertEquals("System", settings.themeMode)
        assertEquals(BottomBarStyle.HyperOS, settings.bottomBarStyle)
        assertEquals(RailStyle.Capsule, settings.railStyle)
        assertTrue(settings.isBlurEnabled)
        assertEquals(1, settings.applyIntervalMinutes)
        assertTrue(settings.autoApplyAfterEdit)
        assertTrue(settings.checkUpdateOnLaunch)
        assertEquals("", settings.language)
        assertEquals("auto", settings.forcedMode)
        assertEquals("", settings.rulesConfigsJson)
        assertFalse(settings.hideLauncherIcon)
    }

    @Test
    fun `fully populated json is parsed`() {
        val settings = AppSettings.fromJson(
            """
            {
              "themeMode": "Dark",
              "bottomBarStyle": 2,
              "railStyle": 2,
              "isBlurEnabled": false,
              "applyIntervalMinutes": 15,
              "autoApplyAfterEdit": false,
              "checkUpdateOnLaunch": false,
              "language": "en",
              "forcedMode": "hyperos",
              "rulesConfigs": [{"id": 1}],
              "hideLauncherIcon": true
            }
            """.trimIndent()
        )

        assertEquals("Dark", settings.themeMode)
        assertEquals(BottomBarStyle.IOS, settings.bottomBarStyle)
        assertEquals(RailStyle.Left, settings.railStyle)
        assertFalse(settings.isBlurEnabled)
        assertEquals(15, settings.applyIntervalMinutes)
        assertFalse(settings.autoApplyAfterEdit)
        assertFalse(settings.checkUpdateOnLaunch)
        assertEquals("en", settings.language)
        assertEquals("hyperos", settings.forcedMode)
        assertEquals("""[{"id":1}]""", settings.rulesConfigsJson)
        assertTrue(settings.hideLauncherIcon)
    }

    @Test
    fun `invalid json falls back to full defaults`() {
        assertEquals(AppSettings(), AppSettings.fromJson("not json at all"))
        assertEquals(AppSettings(), AppSettings.fromJson(""))
    }

    // ---------------------------------------------------------------- round trip

    @Test
    fun `toJson then fromJson round trips every field`() {
        val original = AppSettings(
            themeMode = "Light",
            bottomBarStyle = BottomBarStyle.IOS,
            railStyle = RailStyle.Left,
            isBlurEnabled = false,
            applyIntervalMinutes = 7,
            autoApplyAfterEdit = false,
            checkUpdateOnLaunch = false,
            language = "zh",
            forcedMode = "hyperos",
            rulesConfigsJson = """[{"id":1},{"id":2}]""",
            hideLauncherIcon = true,
        )

        val restored = AppSettings.fromJson(original.toJson())

        assertEquals(original.themeMode, restored.themeMode)
        assertEquals(original.bottomBarStyle, restored.bottomBarStyle)
        assertEquals(original.railStyle, restored.railStyle)
        assertEquals(original.isBlurEnabled, restored.isBlurEnabled)
        assertEquals(original.applyIntervalMinutes, restored.applyIntervalMinutes)
        assertEquals(original.autoApplyAfterEdit, restored.autoApplyAfterEdit)
        assertEquals(original.checkUpdateOnLaunch, restored.checkUpdateOnLaunch)
        assertEquals(original.language, restored.language)
        assertEquals(original.forcedMode, restored.forcedMode)
        assertEquals(original.rulesConfigsJson, restored.rulesConfigsJson)
        assertEquals(original.hideLauncherIcon, restored.hideLauncherIcon)
        assertEquals(original, restored)
    }

    @Test
    fun `toJson then fromJson round trips the default instance`() {
        val restored = AppSettings.fromJson(AppSettings().toJson())

        assertEquals(AppSettings().themeMode, restored.themeMode)
        assertEquals(AppSettings().bottomBarStyle, restored.bottomBarStyle)
        assertEquals(AppSettings().railStyle, restored.railStyle)
        assertEquals(AppSettings().isBlurEnabled, restored.isBlurEnabled)
        assertEquals(AppSettings().applyIntervalMinutes, restored.applyIntervalMinutes)
        assertEquals(AppSettings().autoApplyAfterEdit, restored.autoApplyAfterEdit)
        assertEquals(AppSettings().checkUpdateOnLaunch, restored.checkUpdateOnLaunch)
        assertEquals(AppSettings().language, restored.language)
        assertEquals(AppSettings().forcedMode, restored.forcedMode)
        assertEquals(AppSettings().hideLauncherIcon, restored.hideLauncherIcon)
        // empty rules are serialized as an empty JSON array, not as an empty string
        assertEquals("[]", restored.rulesConfigsJson)
    }

    @Test
    fun `toJson writes numeric enum values and plain booleans`() {
        val json = AppSettings(
            bottomBarStyle = BottomBarStyle.IOS,
            railStyle = RailStyle.Left,
            isBlurEnabled = false,
            applyIntervalMinutes = 3,
            hideLauncherIcon = true,
        ).toJson()

        val obj = JSONObject(json)
        assertEquals(2, obj.getInt("bottomBarStyle"))
        assertEquals(2, obj.getInt("railStyle"))
        assertFalse(obj.getBoolean("isBlurEnabled"))
        assertEquals(3, obj.getInt("applyIntervalMinutes"))
        assertTrue(obj.getBoolean("hideLauncherIcon"))
        // the legacy keys are no longer emitted
        assertFalse(obj.has("isFloatingNavbar"))
        assertFalse(obj.has("isLiquidGlass"))
    }
}

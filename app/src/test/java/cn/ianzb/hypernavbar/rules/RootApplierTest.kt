package cn.ianzb.hypernavbar.rules

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校验文件挂载的内容生成：官方基底 + 模块规则覆盖合并（3.0 / 3.3 的 JSON 与 2.2 的 XML）。 */
class RootApplierTest {

    private val payloads = mapOf(
        "com.example.app" to
            """{"MainActivity":{"mode":1,"color":null,"sf_sampling_mode":0,"dialogMode":1,"popupMode":1,"appNavColorDisabled":0}}""",
    )

    @Test
    fun buildFileContent_mergesPayloadOverOfficialBase() {
        val root = JSONObject(RootApplier.buildFileContent(OFFICIAL_BASE, payloads))
        val nbi = root.getJSONObject("NBIRules")

        // 基底根字段保留，版本号更新为当天（基底为旧日期）
        assertEquals("官方规则", root.getString("name"))
        assertEquals("navigation_bar_immersive_application_config_new", root.getString("modules"))
        assertEquals("modifyApps", root.getString("modifyApps"))
        assertEquals(RuleConverter.todayDataVersion(), root.getString("dataVersion"))

        // 模块规则应用：强制启用、清空版本阈值、活动覆盖、null 颜色不写入
        val app = nbi.getJSONObject("com.example.app")
        assertTrue(app.getBoolean("enable"))
        assertFalse(app.has("disableVersionCode"))
        val activities = app.getJSONObject("activityRules")
        assertEquals(1, (activities.getJSONObject("MainActivity").get("mode") as Number).toInt())
        assertFalse(activities.getJSONObject("MainActivity").has("color"))

        // 未涉及的活动 / 应用原样保留
        assertEquals(2, (activities.getJSONObject("OtherActivity").get("mode") as Number).toInt())
        assertEquals("示例", app.getString("name"))
        val other = nbi.getJSONObject("com.other.app")
        assertEquals(1, (other.getJSONObject("activityRules").getJSONObject("A").get("mode") as Number).toInt())
    }

    @Test
    fun buildFileContent_addsPayloadAppsMissingFromBase() {
        val root = JSONObject(RootApplier.buildFileContent("""{"NBIRules":{}}""", payloads))
        val app = root.getJSONObject("NBIRules").getJSONObject("com.example.app")

        assertTrue(app.getBoolean("enable"))
        assertTrue(app.getJSONObject("activityRules").has("MainActivity"))
    }

    @Test
    fun buildFileContent_usesEmptyRootWhenBaseMissingOrBroken() {
        for (base in listOf(null, "not-json")) {
            val root = JSONObject(RootApplier.buildFileContent(base, payloads))
            assertEquals("navigation_bar_immersive_application_config_new", root.getString("modules"))
            assertEquals(RuleConverter.todayDataVersion(), root.getString("dataVersion"))
            assertTrue(root.getJSONObject("NBIRules").has("com.example.app"))
        }
    }

    @Test
    fun buildFileContent_keepsHigherBaseDataVersion() {
        val base = """{"dataVersion":"991231","NBIRules":{}}"""

        val root = JSONObject(RootApplier.buildFileContent(base, payloads))

        assertEquals("991231", root.getString("dataVersion"))
    }

    @Test
    fun buildFileContent_os22_rewritesConfiguredPackageAndKeepsOthers() {
        val base = """
            <?xml version='1.0' encoding='utf-8' standalone='yes'?>
            <NBIRules>
               <package name="com.example.app" enable="false" activityRule="MainActivity:0,OtherActivity:2" />
               <package name="com.other.app" enable="true" activityRule="A:1" />
            </NBIRules>
        """.trimIndent()

        val xml = RootApplier.buildFileContent(base, payloads, RuleConverter.OsMode.OS22)

        // 命中载荷的应用：强制 enable、活动按同名覆盖，其余活动与其它应用原样保留
        assertTrue(
            xml.contains("""<package name="com.example.app" enable="true" activityRule="MainActivity:1,OtherActivity:2" />"""),
        )
        assertTrue(xml.contains("""<package name="com.other.app" enable="true" activityRule="A:1" />"""))
    }

    @Test
    fun buildFileContent_os22_appendsPackagesMissingFromBase() {
        val base = "<?xml version='1.0' encoding='utf-8' standalone='yes'?>\n<NBIRules>\n</NBIRules>"

        val xml = RootApplier.buildFileContent(base, payloads, RuleConverter.OsMode.OS22)

        assertTrue(xml.contains("""<package name="com.example.app" enable="true" activityRule="MainActivity:1" />"""))
        assertTrue(xml.indexOf("</NBIRules>") > xml.indexOf("com.example.app"))
    }

    private companion object {
        val OFFICIAL_BASE = """
            {
              "dataVersion": "260630",
              "name": "官方规则",
              "modules": "navigation_bar_immersive_application_config_new",
              "modifyApps": "modifyApps",
              "NBIRules": {
                "com.example.app": {
                  "name": "示例",
                  "enable": false,
                  "disableVersionCode": 123,
                  "activityRules": {
                    "MainActivity": { "mode": 0, "sf_sampling_mode": 0 },
                    "OtherActivity": { "mode": 2, "sf_sampling_mode": 0 }
                  }
                },
                "com.other.app": {
                  "enable": true,
                  "activityRules": { "A": { "mode": 1, "sf_sampling_mode": 1 } }
                }
              }
            }
        """.trimIndent()
    }
}

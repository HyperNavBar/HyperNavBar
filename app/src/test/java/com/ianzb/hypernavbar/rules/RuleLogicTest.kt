package com.ianzb.hypernavbar.rules

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleLogicTest {

    @Test
    fun parseJson_extractsAppCountAndNBIRules() {
        val json = """{
            "dataVersion": "1",
            "name": "Test",
            "NBIRules": {
                "com.example.a": { "enable": true },
                "com.example.b": { "enable": false }
            }
        }"""
        val result = RuleFetcher.parseJson(json).getOrThrow()
        assertEquals(2, result.appCount)
        assertEquals("Test", result.configName)
        assertTrue(result.nbiRules.has("com.example.a"))
    }

    @Test
    fun parseJson_failsWhenNBIRulesMissing() {
        val result = RuleFetcher.parseJson("""{"name":"Test"}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun combiner_mergesDifferentActivities() {
        val low = ruleConfig("low", 0, """{
            "modules": "HyperNavBar_config",
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "style": "disabled" } }
                }
            }
        }""")
        val high = ruleConfig("high", 1, """{
            "modules": "HyperNavBar_config",
            "NBIRules": {
                "com.app": {
                    "activityRules": { "B": { "style": "view" } }
                }
            }
        }""")
        val results = resultsFor(low, high)
        val merged = RuleCombiner.combine(listOf(low, high), results)
        assertEquals("HyperNavBar_config", merged.getString("modules"))
        val activities = merged.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules")
        assertEquals("disabled", activities.getJSONObject("A").getString("style"))
        assertEquals("view", activities.getJSONObject("B").getString("style"))
        assertEquals(1, RuleCombiner.getTotalAppCount(results))
    }

    @Test
    fun combiner_lowerPriorityNumberWinsForSameActivity() {
        val low = ruleConfig("low", 0, """{
            "modules": "HyperNavBar_config",
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "style": "disabled" } }
                }
            }
        }""")
        val high = ruleConfig("high", 1, """{
            "modules": "HyperNavBar_config",
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "style": "view" } }
                }
            }
        }""")
        val results = resultsFor(low, high)
        val merged = RuleCombiner.combine(listOf(low, high), results)
        val activities = merged.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules")
        assertEquals("disabled", activities.getJSONObject("A").getString("style"))
    }

    @Test
    fun combiner_normalizesOfficialFormatSource() {
        val official = ruleConfig("official", 0, """{
            "dataVersion": "1",
            "name": "Official",
            "modules": "navigation_bar_immersive_application_config_new",
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "mode": 0 } }
                }
            }
        }""")
        val results = resultsFor(official)
        val merged = RuleCombiner.combine(listOf(official), results)
        // 旧格式源归一化后，合并结果根级 modules 固定为内部新格式
        assertEquals("HyperNavBar_config", merged.getString("modules"))
        assertEquals("1", merged.getString("dataVersion"))
        assertEquals("Official", merged.getString("name"))
        // activity 已归一化为 style 形态
        val act = merged.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals("disabled", act.getString("style"))
    }

    @Test
    fun combiner_mixedFormats_unifyToStyle() {
        val official = ruleConfig("official", 0, """{
            "modules": "navigation_bar_immersive_application_config_new",
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "mode": 0 } }
                }
            }
        }""")
        val newFormat = ruleConfig("newFormat", 1, """{
            "modules": "HyperNavBar_config",
            "NBIRules": {
                "com.app": {
                    "activityRules": { "B": { "style": "view" } }
                }
            }
        }""")
        val results = resultsFor(official, newFormat)
        val merged = RuleCombiner.combine(listOf(official, newFormat), results)
        assertEquals("HyperNavBar_config", merged.getString("modules"))
        val activities = merged.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules")
        // 新旧格式混用后 activity 统一为 style 形态
        assertEquals("disabled", activities.getJSONObject("A").getString("style"))
        assertEquals("view", activities.getJSONObject("B").getString("style"))
    }

    @Test
    fun converter_os33PreservesActivityRules() {
        val json = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "name": "App",
                    "activityRules": { "A": { "style": "floating" } }
                }
            }
        }""")
        val out = JSONObject(RuleConverter.convert(json, RuleConverter.OsMode.OS33))
        assertTrue(
            out.getJSONObject("NBIRules")
                .getJSONObject("com.app")
                .getJSONObject("activityRules")
                .has("A")
        )
    }

    @Test
    fun converter_os22EscapesXmlSpecialChars() {
        val json = JSONObject("""{
            "dataVersion": "1",
            "name": "Test",
            "modules": "mod",
            "modifyApps": "apps",
            "NBIRules": {
                "com.app&": {
                    "name": "A < B > C",
                    "activityRules": {
                        "Act\"D": { "style": "color", "color": "#000000FF" }
                    }
                }
            }
        }""")
        val xml = RuleConverter.convert(json, RuleConverter.OsMode.OS22)
        // 检查 XML 属性值中的转义结果（使用属性边界避免子串误判）
        val nameAttr = Regex("name=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList()
        // 包名和 name 字段都转义
        assertTrue(nameAttr[0] == "com.app&amp;")
        assertTrue(nameAttr[1] == "A &lt; B &gt; C")
        assertTrue(xml.contains("Act&quot;D"))
        // activityRule 不包含未转义的双引号
        assertFalse(Regex("Act\"D").containsMatchIn(xml))
    }

    @Test
    fun converter_styleColor_convertsToArgb() {
        val json = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "style": "color", "color": "#FF0000FF" } }
                }
            }
        }""")
        val out = JSONObject(RuleConverter.convert(json, RuleConverter.OsMode.OS33))
        val act = out.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals(1, act.getInt("mode"))
        assertEquals(-65536, act.getInt("color"))
    }

    @Test
    fun converter_styleSf_setsSamplingMode() {
        val json = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "style": "sf" } }
                }
            }
        }""")
        val out = JSONObject(RuleConverter.convert(json, RuleConverter.OsMode.OS33))
        val act = out.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals(1, act.getInt("sf_sampling_mode"))
        assertEquals(1, act.getInt("mode"))
    }

    @Test
    fun converter_styleFloating_setsMode2() {
        val json = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "style": "floating" } }
                }
            }
        }""")
        val out = JSONObject(RuleConverter.convert(json, RuleConverter.OsMode.OS33))
        val act = out.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals(2, act.getInt("mode"))
    }

    @Test
    fun converter_unknownStyle_fallsBackToDisabled() {
        val json = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "style": "bogus" } }
                }
            }
        }""")
        val out = JSONObject(RuleConverter.convert(json, RuleConverter.OsMode.OS33))
        val act = out.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals(0, act.getInt("mode"))
    }

    @Test
    fun normalizeFromOfficial_sfSamplingMode1_isSf() {
        val official = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "mode": 0, "sf_sampling_mode": 1 } }
                }
            }
        }""")
        val newFormat = RuleConverter.normalizeFromOfficial(official)
        val act = newFormat.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals("sf", act.getString("style"))
    }

    @Test
    fun normalizeFromOfficial_colorMinus1_isColor() {
        val official = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "mode": 1, "color": -1 } }
                }
            }
        }""")
        val newFormat = RuleConverter.normalizeFromOfficial(official)
        val act = newFormat.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals("color", act.getString("style"))
        assertEquals("#FFFFFFFF", act.getString("color"))
    }

    @Test
    fun roundTrip_officialBlackColor() {
        val official = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "mode": 1, "color": -16777216 } }
                }
            }
        }""")
        val newFormat = RuleConverter.normalizeFromOfficial(official)
        val act1 = newFormat.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals("color", act1.getString("style"))
        assertEquals("#000000FF", act1.getString("color"))
        val out = JSONObject(RuleConverter.convert(newFormat, RuleConverter.OsMode.OS33))
        val act2 = out.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals(-16777216, act2.getInt("color"))
    }

    @Test
    fun roundTrip_officialRedColor() {
        val official = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "mode": 1, "color": -65536 } }
                }
            }
        }""")
        val newFormat = RuleConverter.normalizeFromOfficial(official)
        val act1 = newFormat.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals("color", act1.getString("style"))
        assertEquals("#FF0000FF", act1.getString("color"))
        val out = JSONObject(RuleConverter.convert(newFormat, RuleConverter.OsMode.OS33))
        val act2 = out.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals(-65536, act2.getInt("color"))
    }

    @Test
    fun isNewFormat_detectsFormat() {
        assertTrue(RuleConverter.isNewFormat(JSONObject("""{"modules":"HyperNavBar_config"}""")))
        assertFalse(RuleConverter.isNewFormat(JSONObject("""{"modules":"navigation_bar_immersive_application_config_new"}""")))
        assertFalse(RuleConverter.isNewFormat(JSONObject("""{}""")))
    }

    @Test
    fun convert_os33_forcesOfficialModules() {
        val json = JSONObject("""{
            "modules": "HyperNavBar_config",
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "style": "view" } }
                }
            }
        }""")
        val out = JSONObject(RuleConverter.convert(json, RuleConverter.OsMode.OS33))
        assertEquals("navigation_bar_immersive_application_config_new", out.getString("modules"))
    }

    @Test
    fun roundTrip_officialToNewToOfficial() {
        val official = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "mode": 1, "color": 1, "sf_sampling_mode": 0 } }
                }
            }
        }""")
        val newFormat = RuleConverter.normalizeFromOfficial(official)
        val out = JSONObject(RuleConverter.convert(newFormat, RuleConverter.OsMode.OS33))
        val act = out.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules").getJSONObject("A")
        assertEquals(1, act.getInt("mode"))
        assertEquals(1, act.getInt("color"))
        assertEquals(0, act.getInt("sf_sampling_mode"))
    }

    private fun ruleConfig(id: String, priority: Int, json: String) = RuleConfigSource(
        id = id,
        type = RuleType.LOCAL,
        jsonContent = json,
        priority = priority,
    )

    private fun resultsFor(vararg configs: RuleConfigSource) = configs.associate {
        it.id to RuleFetcher.parseJson(it.jsonContent).getOrThrow()
    }
}

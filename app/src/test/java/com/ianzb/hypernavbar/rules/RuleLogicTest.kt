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
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "mode": 0 } }
                }
            }
        }""")
        val high = ruleConfig("high", 1, """{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "B": { "mode": 1 } }
                }
            }
        }""")
        val results = resultsFor(low, high)
        val merged = RuleCombiner.combine(listOf(low, high), results)
        val activities = merged.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules")
        assertEquals(0, activities.getJSONObject("A").getInt("mode"))
        assertEquals(1, activities.getJSONObject("B").getInt("mode"))
        assertEquals(1, RuleCombiner.getTotalAppCount(results))
    }

    @Test
    fun combiner_lowerPriorityNumberWinsForSameActivity() {
        val low = ruleConfig("low", 0, """{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "mode": 0 } }
                }
            }
        }""")
        val high = ruleConfig("high", 1, """{
            "NBIRules": {
                "com.app": {
                    "activityRules": { "A": { "mode": 1 } }
                }
            }
        }""")
        val results = resultsFor(low, high)
        val merged = RuleCombiner.combine(listOf(low, high), results)
        val activities = merged.getJSONObject("NBIRules").getJSONObject("com.app").getJSONObject("activityRules")
        assertEquals(0, activities.getJSONObject("A").getInt("mode"))
    }

    @Test
    fun converter_os33PreservesActivityRules() {
        val json = JSONObject("""{
            "NBIRules": {
                "com.app": {
                    "name": "App",
                    "activityRules": { "A": { "mode": 2, "color": 0 } }
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
                        "Act\"D": { "mode": 1, "color": 0 }
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

package com.maaend.android.catalog

import android.content.res.AssetManager
import com.maaend.android.model.CatalogSnapshot
import com.maaend.android.model.PresetDescriptor
import com.maaend.android.model.ResourceDescriptor
import com.maaend.android.model.TaskDescriptor
import com.maaend.android.model.TaskTier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class InterfaceCatalogLoader(
    private val assets: AssetManager,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(): CatalogSnapshot {
        val interfaceText = readText("interface.json")
        val localeText = readText("locales/interface/zh_cn.json")
        return parseCatalog(
            interfaceText = interfaceText,
            localeText = localeText,
            importResolver = ::readText,
        )
    }

    internal fun parseCatalog(
        interfaceText: String,
        localeText: String,
        importResolver: (String) -> String,
    ): CatalogSnapshot {
        val localeMap = parseLocaleMap(localeText)
        val root = parseJsonObject(interfaceText)
        val importPaths = stringArray(root["import"])

        val taskObjects = mutableListOf<JsonObject>()
        val presetObjects = mutableListOf<JsonObject>()

        for (importPath in importPaths) {
            val importedRoot = parseJsonObject(importResolver(importPath))
            taskObjects += importedRoot["task"].asObjects()
            presetObjects += importedRoot["preset"].asObjects()
        }

        val resources = root["resource"].asObjects().mapNotNull { parseResource(it, localeMap) }

        val tasks = taskObjects
            .mapNotNull { parseTask(it, localeMap) }
            .sortedWith(
                compareBy<TaskDescriptor>(
                    { MVP_TASK_ORDER.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
                    { it.label },
                ),
            )

        val presets = presetObjects
            .mapNotNull { parsePreset(it, localeMap) }
            .sortedBy { it.label }

        return CatalogSnapshot(
            tasks = tasks,
            presets = presets,
            resources = resources,
        )
    }

    private fun parseLocaleMap(text: String): Map<String, String> {
        val root = parseJsonObject(text)
        return root.mapValuesNotNull { (_, value) ->
            value.primitiveContent()
        }
    }

    private fun parseTask(
        obj: JsonObject,
        localeMap: Map<String, String>,
    ): TaskDescriptor? {
        val id = obj["name"].primitiveContent() ?: return null
        val tier = tierForTask(id) ?: return null
        val controllers = stringArray(obj["controller"])
        if (!controllers.isEmpty() && controllers.none { it.equals("ADB", ignoreCase = true) }) {
            return null
        }

        return TaskDescriptor(
            id = id,
            label = resolveText(obj["label"].primitiveContent(), localeMap, fallback = id),
            description = resolveText(obj["description"].primitiveContent(), localeMap, fallback = ""),
            entry = obj["entry"].primitiveContent() ?: "",
            groups = stringArray(obj["group"]),
            controllers = controllers,
            tier = tier,
        )
    }

    private fun parsePreset(
        obj: JsonObject,
        localeMap: Map<String, String>,
    ): PresetDescriptor? {
        val id = obj["name"].primitiveContent() ?: return null
        if (id != "QuickDaily") {
            return null
        }

        val taskIds = obj["task"].asObjects()
            .mapNotNull { task -> task["name"].primitiveContent() }

        return PresetDescriptor(
            id = id,
            label = resolveText(obj["label"].primitiveContent(), localeMap, fallback = id),
            description = resolveText(obj["description"].primitiveContent(), localeMap, fallback = ""),
            taskIds = taskIds,
            tier = TaskTier.MVP,
        )
    }

    private fun parseResource(
        obj: JsonObject,
        localeMap: Map<String, String>,
    ): ResourceDescriptor? {
        val id = obj["name"].primitiveContent() ?: return null
        return ResourceDescriptor(
            id = id,
            label = resolveText(obj["label"].primitiveContent(), localeMap, fallback = id),
        )
    }

    private fun tierForTask(id: String): TaskTier? {
        return when {
            MVP_TASK_ORDER.contains(id) -> TaskTier.MVP
            STRETCH_TASKS.contains(id) -> TaskTier.STRETCH
            else -> null
        }
    }

    private fun resolveText(
        raw: String?,
        localeMap: Map<String, String>,
        fallback: String,
    ): String {
        if (raw.isNullOrBlank()) {
            return fallback
        }
        if (!raw.startsWith("$")) {
            return raw
        }
        return localeMap[raw.removePrefix("$")] ?: fallback
    }

    private fun readText(path: String): String {
        return assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun parseJsonObject(text: String): JsonObject {
        return json.parseToJsonElement(JsonWithComments.stripLineComments(text)).jsonObject
    }

    private fun stringArray(element: JsonElement?): List<String> {
        return element.asArray().mapNotNull { it.primitiveContent() }
    }

    private fun JsonElement?.asArray(): JsonArray {
        return when (this) {
            is JsonArray -> this
            else -> JsonArray(emptyList())
        }
    }

    private fun JsonElement?.asObjects(): List<JsonObject> {
        return asArray().mapNotNull { it as? JsonObject }
    }

    private fun JsonElement?.primitiveContent(): String? {
        return (this as? JsonPrimitive)?.contentOrNull
    }

    private fun <K, V> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> String?): Map<K, String> {
        val result = linkedMapOf<K, String>()
        for (entry in entries) {
            val mapped = transform(entry) ?: continue
            result[entry.key] = mapped
        }
        return result
    }

    companion object {
        val MVP_TASK_ORDER = listOf(
            "AndroidOpenGame",
            "DailyRewards",
            "DijiangRewards",
            "CreditShoppingN2",
            "VisitFriends",
            "SellProduct",
            "AutoEssence",
        )

        val STRETCH_TASKS = setOf(
            "SeizeEntrustTask",
            "DeliveryJobs",
            "GearAssembly",
            "BakerEntry",
            "EnvironmentMonitoring",
        )
    }
}

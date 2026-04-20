package com.maaend.android.catalog

import android.content.res.AssetManager
import com.maaend.android.model.CatalogSnapshot
import com.maaend.android.model.PresetDescriptor
import com.maaend.android.model.ResourceDescriptor
import com.maaend.android.model.TaskOptionCase
import com.maaend.android.model.TaskOptionDescriptor
import com.maaend.android.model.TaskOptionInput
import com.maaend.android.model.TaskOptionType
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

        val taskObjects = mutableListOf<Pair<JsonObject, JsonObject>>()
        val presetObjects = mutableListOf<JsonObject>()

        for (importPath in importPaths) {
            val importedRoot = parseJsonObject(importResolver(importPath))
            val optionRoot = importedRoot["option"] as? JsonObject ?: JsonObject(emptyMap())
            taskObjects += importedRoot["task"].asObjects().map { it to optionRoot }
            presetObjects += importedRoot["preset"].asObjects()
        }

        val resources = root["resource"].asObjects().mapNotNull { parseResource(it, localeMap) }

        val tasks = taskObjects
            .mapNotNull { (taskObj, optionRoot) -> parseTask(taskObj, localeMap, optionRoot) }
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
        optionRoot: JsonObject,
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
            options = parseTaskOptions(
                optionIds = stringArray(obj["option"]),
                localeMap = localeMap,
                optionRoot = optionRoot,
                seen = linkedSetOf(),
            ),
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
            .let(::ensureOpenGameFirst)

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

    private fun parseTaskOptions(
        optionIds: List<String>,
        localeMap: Map<String, String>,
        optionRoot: JsonObject,
        seen: Set<String>,
    ): List<TaskOptionDescriptor> {
        if (optionIds.isEmpty()) {
            return emptyList()
        }
        return optionIds.mapNotNull { optionId ->
            if (optionId in seen) {
                return@mapNotNull null
            }
            val optionObj = optionRoot[optionId] as? JsonObject ?: return@mapNotNull null
            parseTaskOption(
                optionId = optionId,
                obj = optionObj,
                localeMap = localeMap,
                optionRoot = optionRoot,
                seen = seen + optionId,
            )
        }
    }

    private fun parseTaskOption(
        optionId: String,
        obj: JsonObject,
        localeMap: Map<String, String>,
        optionRoot: JsonObject,
        seen: Set<String>,
    ): TaskOptionDescriptor? {
        val type = when (obj["type"].primitiveContent()) {
            "switch" -> TaskOptionType.Switch
            "checkbox" -> TaskOptionType.Checkbox
            "select" -> TaskOptionType.Select
            "input" -> TaskOptionType.Input
            else -> return null
        }

        val controllers = stringArray(obj["controller"])
        if (controllers.isNotEmpty() && controllers.none { it.equals("ADB", ignoreCase = true) }) {
            return null
        }

        val defaultCaseNames = when (val defaultCase = obj["default_case"]) {
            is JsonPrimitive -> defaultCase.contentOrNull?.let(::listOf).orEmpty()
            is JsonArray -> defaultCase.mapNotNull { it.primitiveContent() }
            else -> emptyList()
        }

        val cases = obj["cases"].asObjects().mapNotNull { caseObj ->
            val caseName = caseObj["name"].primitiveContent() ?: return@mapNotNull null
            val pipelineOverride = caseObj["pipeline_override"] as? JsonObject ?: JsonObject(emptyMap())
            val nestedOptionIds = stringArray(caseObj["option"])
            TaskOptionCase(
                name = caseName,
                label = resolveOptionCaseLabel(optionId, caseObj, caseName, localeMap),
                pipelineOverrideJson = pipelineOverride.toString(),
                nestedOptions = parseTaskOptions(
                    optionIds = nestedOptionIds,
                    localeMap = localeMap,
                    optionRoot = optionRoot,
                    seen = seen,
                ),
            )
        }

        val inputs = obj["inputs"].asObjects().mapNotNull { inputObj ->
            val inputName = inputObj["name"].primitiveContent() ?: return@mapNotNull null
            TaskOptionInput(
                name = inputName,
                label = resolveText(inputObj["label"].primitiveContent(), localeMap, fallback = inputName),
                description = resolveText(inputObj["description"].primitiveContent(), localeMap, fallback = ""),
                defaultValue = inputObj["default"].primitiveContent().orEmpty(),
                verifyRegex = inputObj["verify"].primitiveContent().orEmpty(),
                pipelineType = inputObj["pipeline_type"].primitiveContent().orEmpty(),
            )
        }

        val pipelineOverride = (obj["pipeline_override"] as? JsonObject ?: JsonObject(emptyMap())).toString()

        return TaskOptionDescriptor(
            id = optionId,
            type = type,
            label = resolveText(obj["label"].primitiveContent(), localeMap, fallback = optionId),
            description = resolveText(obj["description"].primitiveContent(), localeMap, fallback = ""),
            defaultCaseNames = defaultCaseNames,
            cases = cases,
            inputs = inputs,
            pipelineOverrideJson = pipelineOverride,
        )
    }

    private fun resolveOptionCaseLabel(
        optionId: String,
        caseObj: JsonObject,
        caseName: String,
        localeMap: Map<String, String>,
    ): String {
        val explicit = resolveText(caseObj["label"].primitiveContent(), localeMap, fallback = "")
        if (explicit.isNotBlank()) {
            return explicit
        }

        val candidateKeys = listOf(
            "option.$optionId.cases.$caseName.label",
            "option.$optionId.$caseName.label",
        )
        candidateKeys.firstNotNullOfOrNull { localeMap[it] }?.let { return it }

        return when (caseName) {
            "Yes" -> "开启"
            "No" -> "关闭"
            else -> caseName
        }
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
        private fun ensureOpenGameFirst(taskIds: List<String>): List<String> {
            val filtered = taskIds.filterNot { it == "AndroidOpenGame" }
            return listOf("AndroidOpenGame") + filtered
        }

        val MVP_TASK_ORDER = listOf(
            "AndroidOpenGame",
            "DijiangRewards",
            "CreditShoppingN2",
            "VisitFriends",
            "SellProduct",
            "AutoEssence",
            "DailyRewards",
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

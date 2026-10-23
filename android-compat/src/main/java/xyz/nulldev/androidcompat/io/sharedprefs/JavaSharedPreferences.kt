package xyz.nulldev.androidcompat.io.sharedprefs

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.SharedPreferences
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import xyz.nulldev.androidcompat.util.SafePath
import xyz.nulldev.ts.config.ApplicationRootDir
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
class JavaSharedPreferences(
    private val key: String,
) : SharedPreferences {
    companion object {
        private val logger = KotlinLogging.logger {}

        // desktop: where getAll() finds each key's type (spec 5a §3 D)
        private const val TYPE_PREFIX = "__type."
        private const val TYPE_STRING = "string"
        private const val TYPE_INT = "int"
        private const val TYPE_LONG = "long"
        private const val TYPE_FLOAT = "float"
        private const val TYPE_DOUBLE = "double"
        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_SET = "set"

        private fun typeOf(value: Any): String = when (value) {
            is Set<*> -> TYPE_SET
            is Int -> TYPE_INT
            is Long -> TYPE_LONG
            is Float -> TYPE_FLOAT
            is Double -> TYPE_DOUBLE
            is Boolean -> TYPE_BOOLEAN
            else -> TYPE_STRING
        }
    }

    sealed class Action {
        data class Add(
            val key: String,
            val value: Any,
        ) : Action()

        data class Remove(
            val key: String,
        ) : Action()

        data object Clear : Action()
    }

    private val file =
        Path(
            ApplicationRootDir,
            "settings",
            "${SafePath.buildValidFilename(key)}.xml",
        )
    private val properties =
        Properties().also { properties ->
            try {
                if (file.exists()) {
                    file.inputStream().use { properties.loadFromXML(it) }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error loading settings from $key" }
            }
        }
    private val preferences = PropertiesSettings(properties)

    // desktop: concurrent, every Preference.changes() collector registers from its own thread
    private val listeners = ConcurrentHashMap<SharedPreferences.OnSharedPreferenceChangeListener, (String) -> Unit>()

    // desktop: Android's getAll() gives each value its type, which a backup keeps (spec 5a §3 D). Properties only
    // stores text, so the type of each key is saved beside it, under TYPE_PREFIX
    override fun getAll(): MutableMap<String, *> {
        val keys = preferences.keys
        val types = keys
            .filter { it.startsWith(TYPE_PREFIX) }
            .associate { it.removePrefix(TYPE_PREFIX) to preferences.getString(it, "") }
        val setKeys = types.filterValues { it == TYPE_SET }.keys
        val all = mutableMapOf<String, Any?>()
        types.forEach { (key, type) ->
            all[key] = when (type) {
                TYPE_INT -> preferences.getInt(key, 0)
                TYPE_LONG -> preferences.getLong(key, 0L)
                TYPE_FLOAT -> preferences.getFloat(key, 0f)
                TYPE_DOUBLE -> preferences.getDouble(key, 0.0)
                TYPE_BOOLEAN -> preferences.getBoolean(key, false)
                TYPE_SET -> getStringSet(key, null)
                else -> preferences.getStringOrNull(key)
            }
        }
        // ponytail: keys saved before the types were kept read as text, as they did; a set from then shows as its parts
        keys
            .filter { key -> !key.startsWith(TYPE_PREFIX) && key !in types && setKeys.none { key.startsWith("$it.") } }
            .forEach { all[it] = preferences.getStringOrNull(it) }
        return all
    }

    // desktop: a key saved before the types were kept takes its type from the first read that names one, so getAll()
    // and a backup see it typed from then on; the next save writes it to disk
    private fun <T> typed(key: String, type: String, read: () -> T): T {
        val stored = preferences.hasKey(key) || (type == TYPE_SET && preferences.hasKey("$key.size"))
        if (stored && !preferences.hasKey(TYPE_PREFIX + key)) {
            synchronized(properties) { preferences.putString(TYPE_PREFIX + key, type) }
        }
        return read()
    }

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = typed(key, TYPE_STRING) {
        if (defValue != null) {
            preferences.getString(key, defValue)
        } else {
            preferences.getStringOrNull(key)
        }
    }

    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? = typed(key, TYPE_SET) {
        try {
            if (defValues != null) {
                preferences.decodeValue(SetSerializer(String.serializer()), key, defValues)
            } else {
                preferences.decodeValueOrNull(SetSerializer(String.serializer()), key)
            }
        } catch (_: SerializationException) {
            throw ClassCastException("$key was not a StringSet")
        }
    }

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = typed(key, TYPE_INT) { preferences.getInt(key, defValue) }

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = typed(key, TYPE_LONG) { preferences.getLong(key, defValue) }

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = typed(key, TYPE_FLOAT) { preferences.getFloat(key, defValue) }

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = typed(key, TYPE_BOOLEAN) { preferences.getBoolean(key, defValue) }

    override fun contains(key: String): Boolean = key in preferences.keys

    private fun notify(key: String) {
        listeners.forEach { (_, listener) ->
            listener(key)
        }
    }

    private fun saveAction(
        action: Action,
        shouldNotify: Boolean = true,
    ) {
        when (action) {
            is Action.Add -> {
                when (val value = action.value) {
                    is Set<*> -> {
                        saveAction(Action.Remove(action.key), shouldNotify = false)
                        @Suppress("UNCHECKED_CAST")
                        preferences.encodeValue(SetSerializer(String.serializer()), action.key, value as Set<String>)
                    }

                    is String -> {
                        preferences.putString(action.key, value)
                    }

                    is Int -> {
                        preferences.putInt(action.key, value)
                    }

                    is Long -> {
                        preferences.putLong(action.key, value)
                    }

                    is Float -> {
                        preferences.putFloat(action.key, value)
                    }

                    is Double -> {
                        preferences.putDouble(action.key, value)
                    }

                    is Boolean -> {
                        preferences.putBoolean(action.key, value)
                    }
                }

                // desktop: the type getAll() gives back
                preferences.putString(TYPE_PREFIX + action.key, typeOf(action.value))
                if (shouldNotify) notify(action.key)
            }

            is Action.Remove -> {
                preferences.remove(action.key)
                preferences.remove(TYPE_PREFIX + action.key) // desktop: and its type
                /*
                 Set<String> are stored like
                 key.0 = value1
                 key.1 = value2
                 key.size = 2
                 */
                preferences.keys.forEach { key ->
                    if (key.startsWith(action.key + ".")) {
                        preferences.remove(key)
                    }
                }

                if (shouldNotify) notify(action.key)
            }

            Action.Clear -> {
                preferences.clear()
            }
        }
    }

    private fun saveActions(actions: List<Action>) =
        synchronized(properties) {
            actions.forEach(::saveAction)
        }

    private fun getSnapshot(): Properties =
        synchronized(properties) {
            Properties().apply { putAll(properties) }
        }

    @OptIn(ExperimentalAtomicApi::class)
    private var dirty = AtomicBoolean(false)

    @OptIn(ExperimentalAtomicApi::class)
    private var pendingAsyncSave = AtomicBoolean(false)

    private fun save(properties: Properties): Boolean =
        synchronized(file) {
            try {
                if (properties.isEmpty) {
                    file.deleteIfExists()
                } else {
                    file.createParentDirectories()

                    val tempFile = file.resolveSibling("${file.name}.tmp")

                    tempFile.outputStream().use {
                        properties.storeToXML(it, null)
                    }

                    file.deleteIfExists()
                    tempFile.copyTo(file)
                    tempFile.deleteExisting()
                }

                return true
            } catch (e: Exception) {
                logger.error(e) { "Error saving settings $key" }
                return false
            }
        }

    private fun save(actions: List<Action>): Boolean {
        val snapshot =
            synchronized(properties) {
                saveActions(actions)
                getSnapshot()
            }

        return save(snapshot)
    }

    private fun save(): Boolean = save(getSnapshot())

    @OptIn(DelicateCoroutinesApi::class, ExperimentalAtomicApi::class)
    private fun startAsyncSaver() {
        if (!pendingAsyncSave.compareAndSet(false, true)) {
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                dirty.store(false)

                save(getSnapshot())

                if (!dirty.load()) {
                    pendingAsyncSave.store(false)

                    if (dirty.load() && pendingAsyncSave.compareAndSet(false, true)) {
                        continue
                    }

                    break
                }
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun saveAsync(actions: List<Action>) {
        saveActions(actions)

        dirty.store(true)

        startAsyncSaver()
    }

    override fun edit(): SharedPreferences.Editor = Editor(::save, ::saveAsync)

    class Editor(
        private val save: (actions: List<Action>) -> Boolean,
        private val saveAsync: (actions: List<Action>) -> Unit,
    ) : SharedPreferences.Editor {
        private val actions = mutableListOf<Action>()

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor {
            actions +=
                if (value != null) {
                    Action.Add(key, value)
                } else {
                    Action.Remove(key)
                }
            return this
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            actions +=
                if (values != null) {
                    Action.Add(key, values)
                } else {
                    Action.Remove(key)
                }
            return this
        }

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            actions += Action.Remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            actions.add(Action.Clear)
            return this
        }

        override fun commit(): Boolean = save(actions)

        override fun apply() {
            saveAsync(actions)
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val javaListener: (String) -> Unit = {
            listener.onSharedPreferenceChanged(this, it)
        }
        listeners[listener] = javaListener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    fun deleteAll(): Boolean {
        save(listOf(Action.Clear))
        return true
    }
}

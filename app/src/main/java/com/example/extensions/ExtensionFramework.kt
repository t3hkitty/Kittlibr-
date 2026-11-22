package com.example.extensions

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

interface LibrExtension {
    val id: String
    val name: String
    val version: String
    val category: String // Librget, Librdiscover, LibrManage, LibrSync, LibrAudio, Utility
    fun initialize(context: Context)
    fun execute(action: String, payload: Map<String, Any>): Any?
}

data class ExtensionManifestDto(
    val id: String,
    val name: String,
    val category: String,
    val version: String,
    val description: String,
    val downloadUrl: String
)

interface ExtensionRepository {
    suspend fun fetchRemoteExtensions(repoUrl: String): List<ExtensionManifestDto>
}

class RemoteExtensionRepository : ExtensionRepository {
    private val TAG = "RemoteExtRepo"

    override suspend fun fetchRemoteExtensions(repoUrl: String): List<ExtensionManifestDto> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = URL(repoUrl).readText()
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<ExtensionManifestDto>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        ExtensionManifestDto(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            category = obj.getString("category"),
                            version = obj.getString("version"),
                            description = obj.getString("description"),
                            downloadUrl = obj.getString("downloadUrl")
                        )
                    )
                }
                list
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch remote extensions from $repoUrl", e)
                emptyList()
            }
        }
    }
}

class ExtensionManager(private val context: Context) {
    private val TAG = "ExtensionManager"
    private val repository: ExtensionRepository = RemoteExtensionRepository()

    private val _loadedExtensions = MutableStateFlow<Map<String, LibrExtension>>(emptyMap())
    val loadedExtensions: StateFlow<Map<String, LibrExtension>> = _loadedExtensions.asStateFlow()

    private val _remoteCatalog = MutableStateFlow<List<ExtensionManifestDto>>(emptyList())
    val remoteCatalog: StateFlow<List<ExtensionManifestDto>> = _remoteCatalog.asStateFlow()

    // Shared persistent store for extensions (e.g. WebView Cookier session cookies)
    private val sharedStore = mutableMapOf<String, String>()

    fun registerExtension(extension: LibrExtension) {
        try {
            extension.initialize(context)
            val current = _loadedExtensions.value.toMutableMap()
            current[extension.id] = extension
            _loadedExtensions.value = current
            Log.d(TAG, "Successfully registered extension: ${extension.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register extension ${extension.id}", e)
        }
    }

    suspend fun loadExtensionsFromRepository(repoUrl: String) {
        val dtos = repository.fetchRemoteExtensions(repoUrl)
        _remoteCatalog.value = dtos
        // Automatically register built-in default stub extensions matching DTOs if not present
        dtos.forEach { dto ->
            if (!_loadedExtensions.value.containsKey(dto.id)) {
                registerExtension(StubLibrExtension(dto))
            }
        }
    }

    fun setSharedData(key: String, value: String) {
        sharedStore[key] = value
    }

    fun getSharedData(key: String): String? {
        return sharedStore[key]
    }
}

class StubLibrExtension(private val dto: ExtensionManifestDto) : LibrExtension {
    override val id: String = dto.id
    override val name: String = dto.name
    override val version: String = dto.version
    override val category: String = dto.category

    override fun initialize(context: Context) {
        Log.d("StubExt", "Initialized remote extension: $name")
    }

    override fun execute(action: String, payload: Map<String, Any>): Any? {
        Log.d("StubExt", "Executing $action on remote extension $name")
        return null
    }
}

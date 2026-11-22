package com.example.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookEntity
import com.example.data.repository.AiRepository
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryViewMode {
    CAROUSEL, LIST, SMALL_GRID, MEDIUM_GRID, DETAILED_CARD
}

data class DynamicLibraryRule(
    val name: String,
    val authorFilter: String = "",
    val useOrForAuthor: Boolean = false,
    val tagQuery: String = "",
    val useOrLogicForTags: Boolean = false,
    val shelfFilter: String = "",
    val useOrForShelf: Boolean = false,
    val fileType: String = "",
    val missingMetadataOnly: Boolean = false,
    val acquiredOnly: Boolean? = null,
    val minSeriesCount: Int = 0,
    val sensitiveContentPass: Boolean? = null // true/false for "Does the dog die?", triggers, etc.
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LibraryRepository(application)
    private val aiRepository = AiRepository()

    val books: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _viewMode = MutableStateFlow(LibraryViewMode.MEDIUM_GRID)
    val viewMode: StateFlow<LibraryViewMode> = _viewMode.asStateFlow()

    private val _activeDynamicLibrary = MutableStateFlow<DynamicLibraryRule?>(null)
    val activeDynamicLibrary: StateFlow<DynamicLibraryRule?> = _activeDynamicLibrary.asStateFlow()

    // Custom shelves state
    private val _customShelves = MutableStateFlow(
        listOf("Masterpieces", "Comfort Reads", "Guilty Pleasures", "To-Read", "Favorites", "DNF")
    )
    val customShelves: StateFlow<List<String>> = _customShelves.asStateFlow()

    // Saved dynamic library rules state
    private val _savedDynamicRules = MutableStateFlow<List<DynamicLibraryRule>>(emptyList())
    val savedDynamicRules: StateFlow<List<DynamicLibraryRule>> = _savedDynamicRules.asStateFlow()

    // Long press action preference: "auto_open" or "librmanage_edit"
    private val _longPressAction = MutableStateFlow("auto_open")
    val longPressAction: StateFlow<String> = _longPressAction.asStateFlow()

    // Modal selection states
    private val _selectedBookForMoreInfo = MutableStateFlow<BookEntity?>(null)
    val selectedBookForMoreInfo: StateFlow<BookEntity?> = _selectedBookForMoreInfo.asStateFlow()

    private val _selectedBookForEdit = MutableStateFlow<BookEntity?>(null)
    val selectedBookForEdit: StateFlow<BookEntity?> = _selectedBookForEdit.asStateFlow()

    // Multi-select state
    private val _selectedBookIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedBookIds: StateFlow<Set<Long>> = _selectedBookIds.asStateFlow()

    private val _isMultiSelectActive = MutableStateFlow(false)
    val isMultiSelectActive: StateFlow<Boolean> = _isMultiSelectActive.asStateFlow()

    // Background metadata status
    private val _pendingMetadataCount = MutableStateFlow(0)
    val pendingMetadataCount: StateFlow<Int> = _pendingMetadataCount.asStateFlow()

    private val _failedLookupCount = MutableStateFlow(0)
    val failedLookupCount: StateFlow<Int> = _failedLookupCount.asStateFlow()

    val presetDynamicLibraries = listOf(
        DynamicLibraryRule("All Books"),
        DynamicLibraryRule("Wishlist / Missing Copies", acquiredOnly = false),
        DynamicLibraryRule("First in Long Series (5+ Books)", minSeriesCount = 5),
        DynamicLibraryRule("Missing Metadata / Failed Lookups", missingMetadataOnly = true),
        DynamicLibraryRule("Web Novels & EPUBs", fileType = "EPUB")
    )

    init {
        viewModelScope.launch {
            repository.populateInitialDataIfNeeded()
            checkPendingMetadata()
        }
    }

    private fun checkPendingMetadata() {
        val currentBooks = books.value
        val missing = currentBooks.count { it.description.isBlank() || it.author.isBlank() }
        _pendingMetadataCount.value = missing
        if (missing > 0) {
            viewModelScope.launch {
                currentBooks.filter { it.description.isBlank() || it.author.isBlank() }.forEach { book ->
                    try {
                        val cleaned = aiRepository.cleanMetadata(book.title, book.author, book.description)
                        repository.updateBook(book.copy(description = cleaned))
                        _pendingMetadataCount.value = maxOf(0, _pendingMetadataCount.value - 1)
                    } catch (e: Exception) {
                        _failedLookupCount.value += 1
                    }
                }
            }
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        _viewMode.value = mode
    }

    fun setDynamicLibrary(rule: DynamicLibraryRule?) {
        _activeDynamicLibrary.value = rule
    }

    fun createCustomShelf(shelfName: String) {
        if (shelfName.isNotBlank() && !_customShelves.value.contains(shelfName)) {
            _customShelves.value = _customShelves.value + shelfName.trim()
        }
    }

    fun saveDynamicRule(rule: DynamicLibraryRule) {
        if (!_savedDynamicRules.value.any { it.name.equals(rule.name, ignoreCase = true) }) {
            _savedDynamicRules.value = _savedDynamicRules.value + rule
        }
    }

    fun setLongPressAction(action: String) {
        _longPressAction.value = action
    }

    fun setSelectedBookForMoreInfo(book: BookEntity?) {
        _selectedBookForMoreInfo.value = book
    }

    fun setSelectedBookForEdit(book: BookEntity?) {
        _selectedBookForEdit.value = book
    }

    fun toggleBookSelection(bookId: Long) {
        val current = _selectedBookIds.value
        _selectedBookIds.value = if (current.contains(bookId)) current - bookId else current + bookId
        _isMultiSelectActive.value = _selectedBookIds.value.isNotEmpty()
    }

    fun selectAllBooks(allIds: List<Long>) {
        _selectedBookIds.value = allIds.toSet()
        _isMultiSelectActive.value = true
    }

    fun clearBookSelection() {
        _selectedBookIds.value = emptySet()
        _isMultiSelectActive.value = false
    }

    fun inverseBookSelection(allIds: List<Long>) {
        val current = _selectedBookIds.value
        _selectedBookIds.value = allIds.toSet() - current
        _isMultiSelectActive.value = _selectedBookIds.value.isNotEmpty()
    }

    fun batchUpdateTags(bookIds: Set<Long>, newTags: String, append: Boolean) {
        viewModelScope.launch {
            books.value.filter { bookIds.contains(it.id) }.forEach { book ->
                val updatedTags = if (append && book.tags.isNotBlank()) "${book.tags}, $newTags" else newTags
                repository.updateBook(book.copy(tags = updatedTags))
            }
            clearBookSelection()
        }
    }

    fun batchUpdateStatus(bookIds: Set<Long>, newStatus: String) {
        viewModelScope.launch {
            books.value.filter { bookIds.contains(it.id) }.forEach { book ->
                repository.updateBook(book.copy(status = newStatus))
            }
            clearBookSelection()
        }
    }

    fun batchUpdateReadDate(bookIds: Set<Long>, readDate: String) {
        viewModelScope.launch {
            books.value.filter { bookIds.contains(it.id) }.forEach { book ->
                repository.updateBook(book.copy(externalUri = readDate))
            }
            clearBookSelection()
        }
    }

    fun batchDelete(bookIds: Set<Long>) {
        viewModelScope.launch {
            books.value.filter { bookIds.contains(it.id) }.forEach { book ->
                repository.deleteBook(book)
            }
            clearBookSelection()
        }
    }

    fun addBook(
        title: String,
        author: String,
        description: String,
        coverUrl: String,
        format: String,
        tags: String,
        series: String,
        seriesIndex: Float = 1.0f,
        hasCopy: Boolean = true,
        externalUri: String = ""
    ) {
        viewModelScope.launch {
            repository.insertBook(
                BookEntity(
                    title = title,
                    author = author,
                    description = description,
                    coverUrl = coverUrl.ifBlank { "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600" },
                    format = format,
                    filePath = if (hasCopy) "imported_${System.currentTimeMillis()}" else "wishlist_pending",
                    tags = tags,
                    series = series,
                    seriesIndex = seriesIndex,
                    hasCopy = hasCopy,
                    externalUri = externalUri
                )
            )
            checkPendingMetadata()
        }
    }

    fun updateBook(book: BookEntity) {
        viewModelScope.launch {
            repository.updateBook(book)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            repository.updateBook(book.copy(isFavorite = !book.isFavorite))
        }
    }

    fun updateStatus(book: BookEntity, newStatus: String) {
        viewModelScope.launch {
            repository.updateBook(book.copy(status = newStatus))
        }
    }
}

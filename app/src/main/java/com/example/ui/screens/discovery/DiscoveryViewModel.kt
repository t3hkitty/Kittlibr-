package com.example.ui.screens.discovery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AiRepository
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    private val libraryRepository = LibraryRepository(application)
    private val aiRepository = AiRepository()

    private val _recommendations = MutableStateFlow<String?>("Tap below to analyze your historical book records and imported library files to generate AI recommendations via Gemini.")
    val recommendations: StateFlow<String?> = _recommendations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _customCombos = MutableStateFlow(listOf("LitRPG + Yaoi", "Progression Fantasy + Romance", "SciFi + Cyberpunk", "Isekai + Slice of Life"))
    val customCombos: StateFlow<List<String>> = _customCombos.asStateFlow()

    private val _genreSuggestions = MutableStateFlow<String?>("Tap below to generate AI combination genre suggestions based on your imported books.")
    val genreSuggestions: StateFlow<String?> = _genreSuggestions.asStateFlow()

    private val _isGenreLoading = MutableStateFlow(false)
    val isGenreLoading: StateFlow<Boolean> = _isGenreLoading.asStateFlow()

    fun generateAiTasteRecommendations() {
        viewModelScope.launch {
            _isLoading.value = true
            val books = libraryRepository.allBooks.first()
            val historySummary = if (books.isNotEmpty()) {
                books.joinToString("; ") { book ->
                    "Title: '${book.title}', Author: '${book.author}', Format: '${book.format}', Status: '${book.status}', Tags: '${book.tags}', Description: '${book.description}'"
                }
            } else {
                "No books currently in library."
            }
            val result = aiRepository.getTasteRecommendations(historySummary)
            _recommendations.value = result
            _isLoading.value = false
        }
    }

    fun addCustomCombo(combo: String) {
        if (combo.isNotBlank() && !_customCombos.value.contains(combo)) {
            _customCombos.value = _customCombos.value + combo
        }
    }

    fun generateGenreSuggestions() {
        viewModelScope.launch {
            _isGenreLoading.value = true
            val books = libraryRepository.allBooks.first()
            val tags = books.joinToString(", ") { it.tags }
            val prompt = "Based on the user's imported library tags and book descriptions ($tags), suggest 4 exciting and niche combination genres (e.g. 'LitRPG + Yaoi + Apocalypse', 'System Progression + Dark Romance') with a brief 1-sentence description for each."
            val result = aiRepository.getTasteRecommendations(prompt) // reuse AI method or custom prompt
            _genreSuggestions.value = result
            _isGenreLoading.value = false
        }
    }

    fun addBookFromDiscovery(title: String, author: String, sourceList: String) {
        viewModelScope.launch {
            libraryRepository.insertBook(
                com.example.data.local.BookEntity(
                    title = title,
                    author = author,
                    description = "Imported from Open Library Rec List: $sourceList",
                    coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600",
                    format = "EPUB",
                    filePath = "open_library_${System.currentTimeMillis()}",
                    tags = "OpenLibrary, RecList, FreeSource",
                    series = sourceList,
                    status = "PLAN_TO_READ"
                )
            )
        }
    }
}

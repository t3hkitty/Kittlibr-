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
}

package com.example.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookEntity
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LibraryRepository(application)

    val books: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.populateInitialDataIfNeeded()
        }
    }

    fun addBook(
        title: String,
        author: String,
        description: String,
        coverUrl: String,
        format: String,
        tags: String,
        series: String
    ) {
        viewModelScope.launch {
            repository.insertBook(
                BookEntity(
                    title = title,
                    author = author,
                    description = description,
                    coverUrl = coverUrl.ifBlank { "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600" },
                    format = format,
                    filePath = "imported_${System.currentTimeMillis()}",
                    tags = tags,
                    series = series
                )
            )
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

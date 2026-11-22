package com.example.ui.screens.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.NoteEntity
import com.example.data.repository.AiRepository
import com.example.data.repository.LibraryRepository
import com.example.ui.theme.ReaderThemeConfig
import com.example.ui.theme.ReaderThemes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReaderViewModel(application: Application, private val bookId: Long) : AndroidViewModel(application) {
    private val repository = LibraryRepository(application)
    private val aiRepository = AiRepository()

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book.asStateFlow()

    val bookmarks: Flow<List<BookmarkEntity>> = repository.getBookmarks(bookId)
    val notes: Flow<List<NoteEntity>> = repository.getNotes(bookId)

    private val _currentTheme = MutableStateFlow<ReaderThemeConfig>(ReaderThemes.Dark)
    val currentTheme: StateFlow<ReaderThemeConfig> = _currentTheme.asStateFlow()

    private val _fontSize = MutableStateFlow(18f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _aiSummary = MutableStateFlow<String?>(null)
    val aiSummary: StateFlow<String?> = _aiSummary.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _book.value = repository.getBook(bookId)
        }
    }

    fun updateProgress(currentPage: Int, totalPages: Int) {
        val current = _book.value ?: return
        val percent = if (totalPages > 0) currentPage.toFloat() / totalPages.toFloat() else 0f
        val updated = current.copy(
            currentPage = currentPage,
            totalPages = totalPages,
            progressPercent = percent,
            lastReadDate = System.currentTimeMillis()
        )
        _book.value = updated
        viewModelScope.launch {
            repository.updateBook(updated)
            repository.recordReading(minutes = 2, pages = 5)
        }
    }

    fun setTheme(theme: ReaderThemeConfig) {
        _currentTheme.value = theme
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
    }

    fun addBookmark(chapterTitle: String, snippet: String) {
        viewModelScope.launch {
            bookmarkDaoAction(chapterTitle, snippet)
        }
    }

    private suspend fun bookmarkDaoAction(chapterTitle: String, snippet: String) {
        repository.addBookmark(
            BookmarkEntity(
                bookId = bookId,
                chapterTitle = chapterTitle,
                positionPercent = _book.value?.progressPercent ?: 0f,
                snippet = snippet
            )
        )
    }

    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            repository.addNote(
                NoteEntity(
                    bookId = bookId,
                    title = title,
                    content = content
                )
            )
        }
    }

    fun generateAiChapterSummary(chapterText: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiSummary.value = aiRepository.summarizeChapter(chapterText)
            _isAiLoading.value = false
        }
    }
}

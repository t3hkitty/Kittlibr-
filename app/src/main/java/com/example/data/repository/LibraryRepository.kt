package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.NoteEntity
import com.example.data.local.ReadingStatEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LibraryRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val bookDao = db.bookDao()
    private val bookmarkDao = db.bookmarkDao()
    private val noteDao = db.noteDao()
    private val statDao = db.readingStatDao()

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getBook(id: Long): BookEntity? = bookDao.getBookById(id)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(book: BookEntity) = bookDao.deleteBook(book)

    fun getBookmarks(bookId: Long): Flow<List<BookmarkEntity>> = bookmarkDao.getBookmarksForBook(bookId)

    suspend fun addBookmark(bookmark: BookmarkEntity) = bookmarkDao.insertBookmark(bookmark)

    suspend fun deleteBookmark(bookmark: BookmarkEntity) = bookmarkDao.deleteBookmark(bookmark)

    fun getNotes(bookId: Long): Flow<List<NoteEntity>> = noteDao.getNotesForBook(bookId)

    suspend fun addNote(note: NoteEntity) = noteDao.insertNote(note)

    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)

    val allStats: Flow<List<ReadingStatEntity>> = statDao.getAllStats()

    suspend fun recordReading(minutes: Int, pages: Int) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        val existing = statDao.getStatForDate(today)
        if (existing != null) {
            val updated = existing.copy(
                minutesRead = existing.minutesRead + minutes,
                pagesRead = existing.pagesRead + pages
            )
            statDao.insertOrUpdateStat(updated)
        } else {
            statDao.insertOrUpdateStat(
                ReadingStatEntity(dateString = today, minutesRead = minutes, pagesRead = pages)
            )
        }
    }

    suspend fun populateInitialDataIfNeeded() {
        // Leave database clean and empty when no books exist — zero dummy/sample books.
    }
}

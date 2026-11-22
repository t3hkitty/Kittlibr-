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
        val currentBooks = bookDao.getAllBooks().first()
        if (currentBooks.isEmpty()) {
            val sampleBooks = listOf(
                BookEntity(
                    title = "The Omniscient Reader's Viewpoint",
                    author = "Sing-Shong",
                    description = "A web novel about a reader who finds himself in the world of his favorite doomed web novel.",
                    coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600",
                    format = "NOVEL",
                    filePath = "sample_omniscient.txt",
                    currentPage = 45,
                    totalPages = 500,
                    progressPercent = 0.09f,
                    rating = 4.9f,
                    status = "READING",
                    tags = "Fantasy, Action, Apocalyptic, WebNovel, demo",
                    series = "ORV Series",
                    isFavorite = true
                ),
                BookEntity(
                    title = "Shadow Slave",
                    author = "Guiltythree",
                    description = "A grimdark progression fantasy web novel following Sunny in a world corrupted by the Nightmare Spell.",
                    coverUrl = "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600",
                    format = "NOVEL",
                    filePath = "sample_shadow.txt",
                    currentPage = 120,
                    totalPages = 850,
                    progressPercent = 0.14f,
                    rating = 4.8f,
                    status = "READING",
                    tags = "ProgressionFantasy, Grimdark, Magic, Monsters, demo",
                    series = "Shadow Slave",
                    isFavorite = true
                ),
                BookEntity(
                    title = "Foundation",
                    author = "Isaac Asimov",
                    description = "The classic science fiction masterpiece about psychohistory and the fall of a galactic empire.",
                    coverUrl = "https://images.unsplash.com/photo-1506880018603-83d5b814b5a6?w=600",
                    format = "EPUB",
                    filePath = "sample_foundation.epub",
                    currentPage = 0,
                    totalPages = 255,
                    progressPercent = 0f,
                    rating = 4.7f,
                    status = "PLAN_TO_READ",
                    tags = "SciFi, Classics, SpaceOpera, demo",
                    series = "Foundation Universe",
                    isFavorite = false
                ),
                BookEntity(
                    title = "Metadata & Library Management Guide",
                    author = "Kittlibr Team",
                    description = "Comprehensive reference manual for organizing your ebook library, cleaning metadata, and WebDAV sync.",
                    coverUrl = "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600",
                    format = "EPUB",
                    filePath = "sample_guide.epub",
                    currentPage = 25,
                    totalPages = 100,
                    progressPercent = 0.25f,
                    rating = 4.5f,
                    status = "READING",
                    tags = "Tutorial, Productivity, demo",
                    series = "Manuals",
                    isFavorite = false
                ),
                BookEntity(
                    title = "Pride and Prejudice (Project Gutenberg Free Source)",
                    author = "Jane Austen",
                    description = "Classic romance novel acquired from Project Gutenberg free public domain library archive.",
                    coverUrl = "https://images.unsplash.com/photo-1474932430478-367dbb6832c1?w=600",
                    format = "EPUB",
                    filePath = "gutenberg_pride.epub",
                    currentPage = 0,
                    totalPages = 320,
                    progressPercent = 0f,
                    rating = 4.8f,
                    status = "PLAN_TO_READ",
                    tags = "Classics, Romance, Gutenberg, FreeSource",
                    series = "Gutenberg Library",
                    isFavorite = false
                ),
                BookEntity(
                    title = "Alice's Adventures in Wonderland (Internet Archive Source)",
                    author = "Lewis Carroll",
                    description = "The whimsical tale of Alice falling down the rabbit hole, sourced directly from Internet Archive Open Library.",
                    coverUrl = "https://images.unsplash.com/photo-1512820790803-83ca734da794?w=600",
                    format = "EPUB",
                    filePath = "internet_archive_alice.epub",
                    currentPage = 0,
                    totalPages = 200,
                    progressPercent = 0f,
                    rating = 4.7f,
                    status = "PLAN_TO_READ",
                    tags = "Fantasy, Classics, InternetArchive, FreeSource",
                    series = "Open Library",
                    isFavorite = false
                )
            )
            for (book in sampleBooks) {
                bookDao.insertBook(book)
            }
        }
    }
}

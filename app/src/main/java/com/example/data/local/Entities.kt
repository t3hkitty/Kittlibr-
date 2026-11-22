package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String,
    val format: String, // EPUB, TXT, NOVEL, PDF
    val filePath: String,
    val currentPage: Int = 0,
    val totalPages: Int = 100,
    val progressPercent: Float = 0f,
    val rating: Float = 0f,
    val status: String = "READING", // READING, COMPLETED, PLAN_TO_READ
    val tags: String, // comma separated tags
    val series: String = "",
    val isFavorite: Boolean = false,
    val addedDate: Long = System.currentTimeMillis(),
    val lastReadDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val chapterTitle: String,
    val positionPercent: Float,
    val snippet: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reading_stats")
data class ReadingStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateString: String, // YYYY-MM-DD
    val minutesRead: Int,
    val pagesRead: Int
)

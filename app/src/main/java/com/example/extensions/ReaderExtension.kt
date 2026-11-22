package com.example.extensions

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

abstract class ReaderExtension : LibrExtension {
    protected val TAG = "ReaderExtension"
    protected val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun initialize(context: Context) {
        Log.d(TAG, "Initializing Reader Extension: $name")
    }

    suspend fun syncReadingProgressToCloud(
        userId: String,
        bookId: Long,
        title: String,
        progressPercent: Float,
        currentPage: Int,
        lastReadTimestamp: Long,
        bookmarks: List<Map<String, Any>>
    ) {
        try {
            val syncData = mapOf(
                "bookId" to bookId,
                "title" to title,
                "progressPercent" to progressPercent,
                "currentPage" to currentPage,
                "lastReadTimestamp" to lastReadTimestamp,
                "bookmarks" to bookmarks,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(userId)
                .collection("reading_progress")
                .document(bookId.toString())
                .set(syncData)
                .await()

            Log.d(TAG, "Successfully synced reading progress for book $bookId to Firestore.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync reading progress to cloud", e)
        }
    }

    suspend fun fetchCloudReadingProgress(userId: String, bookId: Long): Map<String, Any>? {
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("reading_progress")
                .document(bookId.toString())
                .get()
                .await()

            snapshot.data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch cloud reading progress", e)
            null
        }
    }
}

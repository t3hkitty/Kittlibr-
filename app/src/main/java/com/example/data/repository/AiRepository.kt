package com.example.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class AiRepository {
    private val TAG = "AiRepository"

    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    suspend fun getTasteRecommendations(readingHistoryTitles: String): String {
        return try {
            withTimeout(45_000L) {
                val prompt = "The user has read, imported, or has the following book records in their library: '$readingHistoryTitles'. " +
                        "Based on this comprehensive taste profile and historical library records, suggest 4 new popular web novels, books, or light novels with title, author, and a compelling 2-sentence pitch for each."
                val response = model.generateContent(prompt)
                response.text ?: "No recommendations generated."
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI recommendation timed out", e)
            "AI request timed out. Please check your network connection."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI recommendation failed", e)
            "Failed to generate AI recommendations: ${e.localizedMessage}"
        }
    }

    suspend fun cleanMetadata(title: String, author: String, description: String): String {
        return try {
            withTimeout(45_000L) {
                val prompt = "Act as an expert Calibre metadata librarian. Clean up and improve the following book metadata:\n" +
                        "Title: '$title'\nAuthor: '$author'\nDescription: '$description'\n\n" +
                        "Provide polished Title, corrected Author, clean enriched Description, and comma-separated Tags (e.g., Fantasy, SciFi, WebNovel, Romance)."
                val response = model.generateContent(prompt)
                response.text ?: "Metadata cleanup failed."
            }
        } catch (e: TimeoutCancellationException) {
            "Metadata cleanup timed out."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error cleaning metadata: ${e.localizedMessage}"
        }
    }

    suspend fun summarizeChapter(chapterText: String): String {
        return try {
            withTimeout(45_000L) {
                val prompt = "Provide a concise, engaging summary of the following story chapter/excerpt:\n\n$chapterText"
                val response = model.generateContent(prompt)
                response.text ?: "Could not summarize chapter."
            }
        } catch (e: TimeoutCancellationException) {
            "Summary timed out."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error summarizing chapter: ${e.localizedMessage}"
        }
    }
}

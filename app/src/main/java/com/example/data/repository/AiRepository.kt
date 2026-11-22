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

    suspend fun getSimilarRecommendations(title: String, author: String, tags: String): String {
        return try {
            withTimeout(45_000L) {
                val prompt = "The user enjoyed the book '$title' by $author (tags: $tags). " +
                        "Suggest 3 similar books or web novels that match this style, tone, and genre, with a 2-sentence pitch for each."
                val response = model.generateContent(prompt)
                response.text ?: "No similar books found."
            }
        } catch (e: Exception) {
            "Failed to fetch similar recommendations: ${e.localizedMessage}"
        }
    }

    suspend fun inferRatingFromEmoji(emoji: String, bookTitle: String): String {
        return try {
            withTimeout(30_000L) {
                val prompt = "The user finished reading '$bookTitle' and reacted with the emoji '$emoji'. " +
                        "Infer a star rating from 1 to 5 based on this emoji reaction, and write a 1-sentence enthusiastic or thoughtful AI review reaction. Return format: 'Rating: X/5 stars - [AI reaction]'"
                val response = model.generateContent(prompt)
                response.text ?: "Rating: 5/5 stars - Amazing read!"
            }
        } catch (e: Exception) {
            when (emoji) {
                "🤩", "😍", "🤯" -> "Rating: 5/5 stars - Absolute masterpiece!"
                "😊", "👍" -> "Rating: 4/5 stars - Highly enjoyable read!"
                "😐" -> "Rating: 3/5 stars - An average, decent read."
                "🥱" -> "Rating: 2/5 stars - A bit slow or underwhelming."
                else -> "Rating: 1/5 stars - Not quite to their taste."
            }
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

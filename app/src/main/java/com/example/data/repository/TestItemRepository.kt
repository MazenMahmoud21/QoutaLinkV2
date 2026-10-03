package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.TestItem
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TestItemRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeItems(): Flow<List<TestItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/items"
        emitAll(
            db.collection("users").document(uid).collection("items")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TestItem::class.java)
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncUserProfile(email: String, displayName: String, photoUrl: String?) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val snapshot = docRef.get().await()
        val now = FieldValue.serverTimestamp()
        if (!snapshot.exists()) {
            val profileData = mutableMapOf<String, Any>(
                "userId" to uid,
                "email" to email,
                "displayName" to displayName,
                "createdAt" to now,
                "lastActive" to now
            )
            photoUrl?.let { if (it.isNotBlank()) profileData["photoUrl"] = it }
            docRef.set(profileData).await()
        } else {
            val updateData = mutableMapOf<String, Any>(
                "email" to email,
                "displayName" to displayName,
                "lastActive" to now
            )
            photoUrl?.let { if (it.isNotBlank()) updateData["photoUrl"] = it }
            docRef.set(updateData, SetOptions.merge()).await()
        }
    }

    suspend fun createItem(
        title: String,
        description: String,
        status: String,
        priority: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val id = UUID.randomUUID().toString()
            val docRef = db.collection("users").document(uid).collection("items").document(id)
            val now = FieldValue.serverTimestamp()
            val payload = mapOf(
                "id" to id,
                "userId" to uid,
                "title" to title.trim(),
                "description" to description.trim(),
                "status" to status,
                "priority" to priority,
                "createdAt" to now,
                "updatedAt" to now
            )
            docRef.set(payload).await()
            Result.success(id)
        } catch (e: Exception) {
            val jsonErr = handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/items")
            Result.failure(Exception(jsonErr, e))
        }
    }

    suspend fun updateItem(
        itemId: String,
        title: String,
        description: String,
        status: String,
        priority: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("items").document(itemId)
            val now = FieldValue.serverTimestamp()
            val payload = mapOf(
                "id" to itemId,
                "userId" to uid,
                "title" to title.trim(),
                "description" to description.trim(),
                "status" to status,
                "priority" to priority,
                "updatedAt" to now
            )
            docRef.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val jsonErr = handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/items/$itemId")
            Result.failure(Exception(jsonErr, e))
        }
    }

    suspend fun deleteItem(itemId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("items").document(itemId)
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            val jsonErr = handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/items/$itemId")
            Result.failure(Exception(jsonErr, e))
        }
    }
}

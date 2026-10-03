package com.example.data.model

import com.google.firebase.Timestamp

enum class TestStatus(val displayName: String) {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    PASSED("Passed"),
    FAILED("Failed")
}

enum class TestPriority(val displayName: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High")
}

data class TestItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = TestStatus.PENDING.name,
    val priority: String = TestPriority.MEDIUM.name,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

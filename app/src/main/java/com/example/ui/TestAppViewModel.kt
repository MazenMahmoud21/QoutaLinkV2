package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TestItem
import com.example.data.model.TestPriority
import com.example.data.model.TestStatus
import com.example.data.model.UserProfile
import com.example.data.repository.TestItemRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class TestMetrics(
    val total: Int = 0,
    val passed: Int = 0,
    val failed: Int = 0,
    val inProgress: Int = 0,
    val pending: Int = 0
)

class TestAppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TestItemRepository(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<TestStatus?>(null)
    val statusFilter: StateFlow<TestStatus?> = _statusFilter.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val rawItemsState: StateFlow<UiState<List<TestItem>>> = repository.observeItems()
        .map<List<TestItem>, UiState<List<TestItem>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(error.localizedMessage ?: "Failed to observe test items"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val userProfileState: StateFlow<UserProfile?> = repository.observeUserProfile()
        .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val filteredItems: StateFlow<List<TestItem>> = combine(
        rawItemsState,
        _searchQuery,
        _statusFilter
    ) { state, query, filter ->
        if (state !is UiState.Success) return@combine emptyList<TestItem>()
        state.data.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.description.contains(query, ignoreCase = true)
            val matchesFilter = filter == null || item.status == filter.name
            matchesQuery && matchesFilter
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val metrics: StateFlow<TestMetrics> = rawItemsState.map { state ->
        if (state !is UiState.Success) return@map TestMetrics()
        val items = state.data
        TestMetrics(
            total = items.size,
            passed = items.count { it.status == TestStatus.PASSED.name },
            failed = items.count { it.status == TestStatus.FAILED.name },
            inProgress = items.count { it.status == TestStatus.IN_PROGRESS.name },
            pending = items.count { it.status == TestStatus.PENDING.name }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TestMetrics()
    )

    init {
        val user = Firebase.auth.currentUser
        if (user != null) {
            viewModelScope.launch {
                try {
                    repository.syncUserProfile(
                        email = user.email ?: "no-email@google.com",
                        displayName = user.displayName ?: "Google User",
                        photoUrl = user.photoUrl?.toString()
                    )
                } catch (ignored: Exception) {}
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onStatusFilterSelect(status: TestStatus?) {
        _statusFilter.value = if (_statusFilter.value == status) null else status
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun createItem(title: String, description: String, status: TestStatus, priority: TestPriority) {
        if (title.isBlank()) {
            _userMessage.value = "Title cannot be empty"
            return
        }
        viewModelScope.launch {
            val result = repository.createItem(
                title = title,
                description = description,
                status = status.name,
                priority = priority.name
            )
            if (result.isSuccess) {
                _userMessage.value = "Test item created in Firestore"
            } else {
                _userMessage.value = "Failed: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun updateItem(itemId: String, title: String, description: String, status: TestStatus, priority: TestPriority) {
        viewModelScope.launch {
            val result = repository.updateItem(
                itemId = itemId,
                title = title,
                description = description,
                status = status.name,
                priority = priority.name
            )
            if (result.isSuccess) {
                _userMessage.value = "Test item updated"
            } else {
                _userMessage.value = "Update failed: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun cycleItemStatus(item: TestItem) {
        val nextStatus = when (item.status) {
            TestStatus.PENDING.name -> TestStatus.IN_PROGRESS
            TestStatus.IN_PROGRESS.name -> TestStatus.PASSED
            TestStatus.PASSED.name -> TestStatus.FAILED
            else -> TestStatus.PENDING
        }
        viewModelScope.launch {
            repository.updateItem(
                itemId = item.id,
                title = item.title,
                description = item.description,
                status = nextStatus.name,
                priority = item.priority
            )
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            val result = repository.deleteItem(itemId)
            if (result.isSuccess) {
                _userMessage.value = "Test item deleted"
            } else {
                _userMessage.value = "Delete failed: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun seedSampleData() {
        viewModelScope.launch {
            repository.createItem(
                title = "Auth Service Validation",
                description = "Verify Google Sign-In with Credential Manager integration",
                status = TestStatus.PASSED.name,
                priority = TestPriority.HIGH.name
            )
            repository.createItem(
                title = "Firestore Security Rules Sandbox",
                description = "Ensure multi-user tenant isolation in rules engine",
                status = TestStatus.IN_PROGRESS.name,
                priority = TestPriority.HIGH.name
            )
            repository.createItem(
                title = "Realtime Sync Performance Check",
                description = "Measure latency for live snapshot streams",
                status = TestStatus.PENDING.name,
                priority = TestPriority.MEDIUM.name
            )
            _userMessage.value = "Sample diagnostic tests seeded"
        }
    }
}

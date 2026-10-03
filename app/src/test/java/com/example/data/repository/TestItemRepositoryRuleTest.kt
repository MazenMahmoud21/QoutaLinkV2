package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TestPriority
import com.example.data.model.TestStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TestItemRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun testCreateAndObserveItemAgainstEmulator() = runBlocking {
    val uid = signInTestUser("tester_${System.currentTimeMillis()}@example.com")
    val repository = TestItemRepository(firestore)

    val createResult = repository.createItem(
      title = "Smoke Test Item",
      description = "Verifying repository against emulator",
      status = TestStatus.PASSED.name,
      priority = TestPriority.HIGH.name
    )

    assertTrue("Create item should succeed: ${createResult.exceptionOrNull()?.message}", createResult.isSuccess)
    val itemId = createResult.getOrThrow()
    assertNotNull(itemId)

    val items = repository.observeItems().first()
    assertTrue("Items should contain created item", items.any { it.id == itemId && it.title == "Smoke Test Item" })
    val item = items.first { it.id == itemId }
    assertEquals(uid, item.userId)
    assertEquals(TestStatus.PASSED.name, item.status)
  }

  @Test
  fun testUnauthenticatedAccessFails() = runBlocking {
    auth.signOut()
    val repository = TestItemRepository(firestore)

    val result = repository.createItem("Unauthorized", "Desc", TestStatus.PENDING.name, TestPriority.LOW.name)
    assertTrue("Unauthenticated create should return Result.failure", result.isFailure)
  }
}

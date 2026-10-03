package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.TestItem
import com.example.data.model.TestPriority
import com.example.data.model.TestStatus

@Composable
fun ItemEditorDialog(
    itemToEdit: TestItem? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, status: TestStatus, priority: TestPriority) -> Unit
) {
    var title by remember { mutableStateOf(itemToEdit?.title ?: "") }
    var description by remember { mutableStateOf(itemToEdit?.description ?: "") }
    var selectedStatus by remember {
        mutableStateOf(
            if (itemToEdit != null) {
                try { TestStatus.valueOf(itemToEdit.status) } catch (e: Exception) { TestStatus.PENDING }
            } else TestStatus.PENDING
        )
    }
    var selectedPriority by remember {
        mutableStateOf(
            if (itemToEdit != null) {
                try { TestPriority.valueOf(itemToEdit.priority) } catch (e: Exception) { TestPriority.MEDIUM }
            } else TestPriority.MEDIUM
        )
    }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (itemToEdit == null) "Create New Test" else "Edit Test Item",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) hasError = false
                    },
                    label = { Text("Test Name / Title *") },
                    isError = hasError,
                    supportingText = {
                        if (hasError) Text("Title is required")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_test_title"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Diagnostics") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_test_description"),
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TestStatus.entries.forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text(status.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Priority",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TestPriority.entries.forEach { priority ->
                        FilterChip(
                            selected = selectedPriority == priority,
                            onClick = { selectedPriority = priority },
                            label = { Text(priority.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        hasError = true
                    } else {
                        onSave(title.trim(), description.trim(), selectedStatus, selectedPriority)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("save_test_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_test_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

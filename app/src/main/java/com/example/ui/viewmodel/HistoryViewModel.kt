package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirestoreSyncRepository
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val syncRepository: FirestoreSyncRepository
) : ViewModel() {

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            syncRepository.deleteSession(sessionId)
        }
    }

    fun deleteWorkflow(workflowId: String) {
        viewModelScope.launch {
            syncRepository.deleteWorkflowRun(workflowId)
        }
    }
}

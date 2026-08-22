package com.neuroflow.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuroflow.app.data.local.entity.ContractOutcome
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.data.repository.UlyssesContractRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UlyssesEvaluatorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val contractRepository: UlyssesContractRepository,
    private val taskRepository: TaskRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val contractId = inputData.getString("contractId") ?: return Result.success()

        val contract = contractRepository.getById(contractId) ?: return Result.success()
        val task = taskRepository.getById(contract.taskId) ?: return Result.success()

        val outcome = if (task.completedAt != null && task.completedAt < contract.deadlineAt) {
            ContractOutcome.WIN
        } else {
            ContractOutcome.LOSS
        }

        contractRepository.update(contract.copy(outcome = outcome))

        return Result.success()
    }
}

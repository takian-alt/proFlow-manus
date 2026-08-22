package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class ContractOutcome { WIN, LOSS }

@Entity(tableName = "ulysses_contracts")
data class UlyssesContractEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val deadlineAt: Long,
    val consequence: String,
    val outcome: ContractOutcome? = null,   // null = still active
    val createdAt: Long = System.currentTimeMillis()
)

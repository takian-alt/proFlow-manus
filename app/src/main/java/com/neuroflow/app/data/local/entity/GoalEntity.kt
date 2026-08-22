package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val targetDate: Long? = null,
    val identityStatement: String = "",
    val completedTaskCount: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

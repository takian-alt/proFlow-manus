package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "unavailable_time_blocks",
    indices = [Index(value = ["startMillis"]), Index(value = ["endMillis"])]
)
data class UnavailableTimeBlockEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startMillis: Long,
    val endMillis: Long,
    val label: String = "Unavailable",
    val createdAtMillis: Long = System.currentTimeMillis()
)

package com.neuroflow.app.data.local

import androidx.room.TypeConverter
import com.neuroflow.app.data.local.entity.ContractOutcome
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.Recurrence
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.domain.model.RewardTier
import com.neuroflow.app.domain.model.TaskType

class Converters {
    @TypeConverter fun fromQuadrant(value: Quadrant): String = value.name
    @TypeConverter fun toQuadrant(value: String): Quadrant = Quadrant.valueOf(value)

    @TypeConverter fun fromPriority(value: Priority): String = value.name
    @TypeConverter fun toPriority(value: String): Priority = Priority.valueOf(value)

    @TypeConverter fun fromTaskStatus(value: TaskStatus): String = value.name
    @TypeConverter fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter fun fromRecurrence(value: Recurrence): String = value.name
    @TypeConverter fun toRecurrence(value: String): Recurrence = Recurrence.valueOf(value)

    @TypeConverter fun fromEnergyLevel(value: EnergyLevel): String = value.name
    @TypeConverter fun toEnergyLevel(value: String): EnergyLevel = EnergyLevel.valueOf(value)

    @TypeConverter fun fromTaskType(value: TaskType): String = value.name
    @TypeConverter fun toTaskType(value: String): TaskType =
        try { TaskType.valueOf(value) } catch (_: Exception) { TaskType.ANALYTICAL }

    @TypeConverter fun fromContractOutcome(value: ContractOutcome?): String? = value?.name
    @TypeConverter fun toContractOutcome(value: String?): ContractOutcome? =
        value?.let { ContractOutcome.valueOf(it) }

    @TypeConverter fun fromRewardTier(value: RewardTier): String = value.name
    @TypeConverter fun toRewardTier(value: String): RewardTier = RewardTier.valueOf(value)
}

package com.samco.trackandgraph.base.database.entity.queryresponse

import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.samco.trackandgraph.base.database.dto.DataType
import com.samco.trackandgraph.base.database.dto.DiscreteValue
import com.samco.trackandgraph.base.database.entity.Feature

internal data class TrackerWithFeature(
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "group_id")
    val groupId: Long,

    @ColumnInfo(name = "feature_id")
    val featureId: Long,

    @ColumnInfo(name = "display_index")
    val displayIndex: Int,

    @ColumnInfo(name = "feature_description")
    val description: String,

    @ColumnInfo(name = "type")
    val dataType: DataType,

    @ColumnInfo(name = "discrete_values")
    val discreteValues: List<DiscreteValue>,

    @ColumnInfo(name = "has_default_value")
    val hasDefaultValue: Boolean,

    @ColumnInfo(name = "default_value")
    val defaultValue: Double,
) {
    fun toFeatureEntity() = Feature(
        id = featureId,
        name = name,
        groupId = groupId,
        displayIndex = displayIndex,
        description = description
    )
}
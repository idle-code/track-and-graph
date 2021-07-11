/*
 *  This file is part of Track & Graph
 *
 *  Track & Graph is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Track & Graph is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.samco.trackandgraph.viewgraphstat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.samco.trackandgraph.database.entity.DataPoint
import com.samco.trackandgraph.database.entity.FeatureType
import com.samco.trackandgraph.database.dto.NoteType
import com.samco.trackandgraph.databinding.ListItemNoteBinding
import com.samco.trackandgraph.ui.formatDayMonthYearHourMinute
import com.samco.trackandgraph.ui.formatDayWeekDayMonthYearHourMinuteOneLine

class NotesAdapter(
    private val featureDisplayNames: Map<Long, String>,
    private val featureTypes: Map<Long, FeatureType>,
    private val weekDayNames: List<String>,
    private val clickListener: NoteClickListener
) : ListAdapter<GraphNote, NotesAdapter.ViewHolder>(
    NoteDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder.from(
            parent,
            featureDisplayNames,
            weekDayNames,
            featureTypes,
            clickListener
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder private constructor(
        private val binding: ListItemNoteBinding,
        private val featureDisplayNames: Map<Long, String>,
        private val weekDayNames: List<String>,
        private val featureTypes: Map<Long, FeatureType>,
        private val clickListener: NoteClickListener
    ) : RecyclerView.ViewHolder(binding.root) {

        var note: GraphNote? = null

        fun bind(note: GraphNote) {
            this.note = note
            binding.timestampText.text =
                formatDayWeekDayMonthYearHourMinuteOneLine(
                    binding.timestampText.context,
                    weekDayNames,
                    note.timestamp
                )
            when (note.noteType) {
                NoteType.DATA_POINT -> initFromDataPointNote()
                NoteType.GLOBAL_NOTE -> initFromGlobalNote()
            }
        }

        private fun initFromGlobalNote() {
            val globalNote = note!!.globalNote!!
            binding.valueText.visibility = View.GONE
            binding.featureNameText.visibility = View.GONE
            binding.cardView.setOnClickListener { clickListener.viewClicked(note!!) }
            binding.noteText.visibility = View.VISIBLE
            binding.noteText.text = globalNote.note
        }

        private fun initFromDataPointNote() {
            val dataPoint = note!!.dataPoint!!
            binding.valueText.visibility = View.VISIBLE
            val featureType = featureTypes.getOrElse(dataPoint.featureId) { FeatureType.CONTINUOUS }
            binding.valueText.text = DataPoint.getDisplayValue(note!!.dataPoint!!, featureType)
            binding.featureNameText.visibility = View.VISIBLE
            //TODO 2
            binding.featureNameText.text = featureDisplayNames.getOrElse(dataPoint.featureId) { "" }
            binding.cardView.setOnClickListener { clickListener.viewClicked(note!!) }
            binding.noteText.visibility = View.VISIBLE
            binding.noteText.text = dataPoint.note
        }

        companion object {
            fun from(
                parent: ViewGroup,
                featureDisplayNames: Map<Long, String>,
                weekDayNames: List<String>,
                featureTypes: Map<Long, FeatureType>,
                clickListener: NoteClickListener
            ): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = ListItemNoteBinding.inflate(layoutInflater, parent, false)
                return ViewHolder(
                    binding,
                    featureDisplayNames,
                    weekDayNames,
                    featureTypes,
                    clickListener
                )
            }
        }
    }
}

class NoteDiffCallback : DiffUtil.ItemCallback<GraphNote>() {
    override fun areItemsTheSame(oldItem: GraphNote, newItem: GraphNote) =
        oldItem.timestamp == newItem.timestamp
                && oldItem.noteType == newItem.noteType
                && oldItem.dataPoint?.featureId == newItem.dataPoint?.featureId

    override fun areContentsTheSame(oldItem: GraphNote, newItem: GraphNote) =
        oldItem.dataPoint == newItem.dataPoint
                && oldItem.globalNote == newItem.globalNote
}

class NoteClickListener(private val onViewDataPoint: (GraphNote) -> Unit) {
    fun viewClicked(note: GraphNote) = onViewDataPoint(note)
}

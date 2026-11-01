package org.fossify.phone.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.phone.databinding.ItemRecordingBinding
import org.fossify.phone.recording.Recording
import org.fossify.phone.recording.RecordingRowFormat

/** Shows recordings in a list. Tap a row to play, long-press to delete. */
class RecordingsAdapter(
    private val recordings: List<Recording>,
    private val onPlay: (Recording) -> Unit,
    private val onDelete: (Recording) -> Unit,
) : RecyclerView.Adapter<RecordingsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRecordingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = recordings.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recording = recordings[position]
        holder.binding.recordingTitle.text = recording.name.ifBlank { recording.number.ifBlank { "Unknown" } }
        holder.binding.recordingSubtitle.text = RecordingRowFormat.subtitle(recording)
        holder.binding.root.setOnClickListener { onPlay(recording) }
        holder.binding.root.setOnLongClickListener {
            onDelete(recording)
            true
        }
    }
}

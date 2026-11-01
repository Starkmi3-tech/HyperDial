package org.fossify.phone.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.phone.R
import org.fossify.phone.adapters.RecordingsAdapter
import org.fossify.phone.databinding.ActivityRecordingsBinding
import org.fossify.phone.recording.CallRecordingService
import org.fossify.phone.recording.DriveClient
import org.fossify.phone.recording.Recording
import org.fossify.phone.recording.RecordingStore
import java.io.File

class RecordingsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityRecordingsBinding::inflate)
    private val store by lazy { RecordingStore(File(filesDir, CallRecordingService.RECORDINGS_DIR)) }
    private val driveClient by lazy { DriveClient(this) }

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.recordingsList.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.recordingsAppbar, NavigationIcon.Arrow)
        refresh()
    }

    private fun refresh() {
        val recordings = store.all()
        binding.recordingsEmpty.beVisibleIf(recordings.isEmpty())
        binding.recordingsSignInBanner.beVisibleIf(!driveClient.hasAccount())
        binding.recordingsSignInBanner.setOnClickListener {
            signInLauncher.launch(driveClient.signInIntent())
        }
        binding.recordingsList.adapter = RecordingsAdapter(
            recordings = recordings,
            onPlay = ::play,
            onDelete = ::confirmDelete,
        )
        updateTextColors(binding.recordingsHolder)
    }

    private fun play(recording: Recording) {
        val file = File(recording.filePath)
        if (!file.exists()) {
            toast(R.string.recording_file_missing)
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.recordings", file)
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(this)
        }
    }

    private fun confirmDelete(recording: Recording) {
        ConfirmationDialog(this, getString(R.string.delete_recording_confirmation)) {
            store.delete(recording.id)
            File(recording.filePath).delete()
            refresh()
        }
    }
}

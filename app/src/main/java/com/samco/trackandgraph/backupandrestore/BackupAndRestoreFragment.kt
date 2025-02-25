/*
 * This file is part of Track & Graph
 *
 * Track & Graph is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Track & Graph is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.samco.trackandgraph.backupandrestore

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.samco.trackandgraph.MainActivity
import com.samco.trackandgraph.NavButtonStyle
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.model.AlarmInteractor
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.databinding.BackupAndRestoreFragmentBinding
import com.samco.trackandgraph.util.bindingForViewLifecycle
import com.samco.trackandgraph.util.getColorFromAttr
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import org.threeten.bp.OffsetDateTime
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlin.system.exitProcess

const val EXPORT_DATABASE_REQUEST_CODE = 235
const val RESTORE_DATABASE_REQUEST_CODE = 578
const val SQLITE_MIME_TYPE = "application/vnd.sqlite3"

@AndroidEntryPoint
class BackupAndRestoreFragment : Fragment() {

    private var binding: BackupAndRestoreFragmentBinding by bindingForViewLifecycle()
    private val viewModel by viewModels<BackupAndRestoreViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BackupAndRestoreFragmentBinding.inflate(inflater)

        listenToViewModel()

        binding.backupButton.setOnClickListener { onBackupClicked() }
        binding.restoreButton.setOnClickListener { onRestoreClicked() }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).setActionBarConfig(
            NavButtonStyle.MENU,
            getString(R.string.backup_and_restore)
        )
    }

    private fun listenToViewModel() {
        listenToInProgress()
        listenToRestoreResult()
        listenToBackupResult()
    }

    private fun listenToInProgress() {
        viewModel.inProgress.observe(viewLifecycleOwner, Observer {
            binding.progressOverlay.visibility = if (it) View.VISIBLE else View.GONE
        })
    }

    private fun listenToRestoreResult() {
        viewModel.restoreResult.observe(viewLifecycleOwner, Observer {
            when (it) {
                true -> restartApp()
                false -> {
                    val color =
                        binding.restoreFeedbackText.context.getColorFromAttr(R.attr.errorTextColor)
                    binding.restoreFeedbackText.setTextColor(color)
                    val errorText = viewModel.error?.stringResource?.let { r -> getString(r) } ?: ""
                    binding.restoreFeedbackText.text = getString(R.string.restore_failed, errorText)
                }
            }
        })
    }

    private fun listenToBackupResult() {
        viewModel.backupResult.observe(viewLifecycleOwner, Observer {
            when (it) {
                true -> {
                    val color =
                        binding.backupFeedbackText.context.getColorFromAttr(R.attr.colorOnError)
                    binding.backupFeedbackText.setTextColor(color)
                    binding.backupFeedbackText.text = getString(R.string.backup_successful)
                }
                false -> {
                    val color =
                        binding.backupFeedbackText.context.getColorFromAttr(R.attr.errorTextColor)
                    binding.backupFeedbackText.setTextColor(color)
                    val errorText = viewModel.error?.stringResource?.let { r -> getString(r) } ?: ""
                    binding.backupFeedbackText.text = getString(R.string.backup_failed, errorText)
                }
            }
        })
    }

    private fun restartApp() {
        val mStartActivity = Intent(context, MainActivity::class.java)
        val mPendingIntent = PendingIntent.getActivity(
            context,
            8375,
            mStartActivity,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mgr = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = mPendingIntent
        exitProcess(0)
    }

    private fun onBackupClicked() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            val now = OffsetDateTime.now()
            val generatedName = getString(
                R.string.backup_file_name_suffix,
                "TrackAndGraphBackup", now.year, now.monthValue,
                now.dayOfMonth, now.hour, now.minute, now.second
            )
            putExtra(Intent.EXTRA_TITLE, generatedName)
            type = SQLITE_MIME_TYPE
        }
        startActivityForResult(intent, EXPORT_DATABASE_REQUEST_CODE)
    }

    private fun onRestoreClicked() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
        startActivityForResult(intent, RESTORE_DATABASE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        when (requestCode) {
            EXPORT_DATABASE_REQUEST_CODE -> {
                viewModel.exportDatabase(
                    resultData?.data?.let { activity?.contentResolver?.openOutputStream(it) }

                )
            }
            RESTORE_DATABASE_REQUEST_CODE -> {
                viewModel.restoreDatabase(
                    resultData?.data?.let { activity?.contentResolver?.openInputStream(it) }
                )
            }
        }
    }
}

@HiltViewModel
class BackupAndRestoreViewModel @Inject constructor(
    private val dataInteractor: DataInteractor,
    private val alarmInteractor: AlarmInteractor
) : ViewModel() {
    class BackupRestoreException(val stringResource: Int?)

    private val _restoreResult: MutableLiveData<Boolean?> = MutableLiveData(null)
    val restoreResult: LiveData<Boolean?> = _restoreResult

    private val _backupResult: MutableLiveData<Boolean?> = MutableLiveData(null)
    val backupResult: LiveData<Boolean?> = _backupResult

    var error: BackupRestoreException? = null
        private set

    private val _inProgress = MutableLiveData(false)
    val inProgress: LiveData<Boolean> = _inProgress

    private var workerJob = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + workerJob)

    fun exportDatabase(outputStream: OutputStream?) {
        val databaseFilePath = dataInteractor.getDatabaseFilePath()

        if (outputStream == null) {
            error = BackupRestoreException(R.string.backup_error_could_not_write_to_file)
            _backupResult.value = false
            return
        }
        if (databaseFilePath == null) {
            error = BackupRestoreException(R.string.backup_error_could_not_find_database_file)
            _backupResult.value = false
            return
        }
        val databaseFile = File(databaseFilePath)
        if (!databaseFile.exists()) {
            error = BackupRestoreException(R.string.backup_error_failed_to_copy_database)
            _backupResult.value = false
            return
        }
        _inProgress.value = true
        ioScope.launch {
            try {
                dataInteractor.doRawQuery(
                    SimpleSQLiteQuery("PRAGMA wal_checkpoint(full)")
                )

                databaseFile.inputStream().use { inputStream ->
                    outputStream.use { inputStream.copyTo(it) }
                }

                withContext(Dispatchers.Main) {
                    _backupResult.value = true
                    _inProgress.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = BackupRestoreException(R.string.backup_error_failed_to_copy_database)
                    _backupResult.value = false
                }
            }
        }
    }

    fun restoreDatabase(inputStream: InputStream?) {

        val databaseFilePath = dataInteractor.getDatabaseFilePath()

        if (inputStream == null) {
            error = BackupRestoreException(R.string.restore_error_could_not_read_from_database_file)
            _restoreResult.value = false
            return
        }
        if (databaseFilePath == null) {
            error = BackupRestoreException(R.string.restore_error_could_not_write_to_database)
            _restoreResult.value = false
            return
        }

        _inProgress.value = true
        ioScope.launch {
            try {
                alarmInteractor.clearAlarms()
                dataInteractor.closeOpenHelper()

                inputStream.use { inStream ->
                    File(databaseFilePath).outputStream().use { inStream.copyTo(it) }
                }
                withContext(Dispatchers.Main) {
                    _restoreResult.value = true
                    _inProgress.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = BackupRestoreException(R.string.restore_error_failed_to_copy_database)
                    _restoreResult.value = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        workerJob.cancel()
    }
}

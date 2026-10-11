package com.safir.iptv.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safir.iptv.data.backup.Backup
import com.safir.iptv.data.backup.BackupManager
import com.safir.iptv.data.backup.FoundDevice
import com.safir.iptv.data.backup.LanTransfer
import com.safir.iptv.data.backup.NotABackupException
import com.safir.iptv.data.backup.WrongCodeException
import com.safir.iptv.ui.i18n.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    /** Set while this box offers its settings: the code its screen shows. */
    val sendCode: String? = null,
    val sendAddress: String? = null,
    /** True while this box looks for one that is sending. */
    val receiving: Boolean = false,
    val found: List<FoundDevice> = emptyList(),
    val address: String = "",
    val port: Int = LanTransfer.PORT,
    val code: String = "",
    /** What is happening right now; null when nothing is. */
    val busy: String? = null,
    /** A backup that has been read and waits for "apply". */
    val pending: Backup? = null,
    val message: String? = null,
    val error: String? = null,
    /** Set once a backup has been applied cleanly — time to start over at home. */
    val finished: Boolean = false
)

class BackupViewModel(
    private val context: Context,
    private val backups: BackupManager
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private var sending: Job? = null
    private var discovering: Job? = null

    private val deviceName: String get() = Build.MODEL.orEmpty().ifBlank { "Android" }

    // ------------------------------------------------------------ network

    fun startSending() {
        stopAll()
        val code = LanTransfer.newCode()
        _state.update {
            it.copy(
                sendCode = code,
                sendAddress = LanTransfer.localAddress(),
                message = null,
                error = null
            )
        }
        sending = viewModelScope.launch {
            try {
                val payload = backups.encode(backups.create(deviceName)).toByteArray()
                LanTransfer.serve(context, deviceName, payload, code)
            } catch (error: Exception) {
                if (error !is kotlinx.coroutines.CancellationException) {
                    _state.update { it.copy(sendCode = null, error = error.message) }
                }
                throw error
            }
        }
    }

    fun startReceiving() {
        stopAll()
        _state.update { it.copy(receiving = true, found = emptyList(), message = null, error = null) }
        discovering = viewModelScope.launch {
            LanTransfer.discover(context).collect { devices ->
                _state.update { state ->
                    // The first box that turns up is taken straight away: usually
                    // there is exactly one, and the remote is spared a step.
                    val first = devices.firstOrNull()
                    if (state.address.isBlank() && first != null) {
                        state.copy(found = devices, address = first.host, port = first.port)
                    } else {
                        state.copy(found = devices)
                    }
                }
            }
        }
    }

    fun pick(device: FoundDevice) =
        _state.update { it.copy(address = device.host, port = device.port) }

    fun setAddress(value: String) = _state.update {
        // Typed by hand, so the box listens on the fixed port.
        it.copy(address = value.trim(), port = LanTransfer.PORT)
    }

    fun setCode(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit)) }

    fun fetch() {
        val form = _state.value
        if (form.address.isBlank() || form.code.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = AppLocale.strings.connecting, error = null) }
            try {
                val bytes = LanTransfer.fetch(form.address, form.port, form.code)
                val backup = backups.decode(bytes.decodeToString())
                _state.update { it.copy(busy = null, pending = backup) }
            } catch (wrong: WrongCodeException) {
                _state.update { it.copy(busy = null, error = AppLocale.strings.backup.wrongCode) }
            } catch (error: NotABackupException) {
                _state.update { it.copy(busy = null, error = AppLocale.strings.backup.notABackup) }
            } catch (error: Exception) {
                _state.update { it.copy(busy = null, error = AppLocale.strings.backup.unreachable) }
            }
        }
    }

    /** Called when the page is left: nothing keeps listening behind the user's back. */
    fun stopAll() {
        sending?.cancel()
        discovering?.cancel()
        sending = null
        discovering = null
        _state.update { it.copy(sendCode = null, sendAddress = null, receiving = false, found = emptyList()) }
    }

    // --------------------------------------------------------------- file

    fun fileName(): String =
        "karacast-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())}.json"

    fun saveTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = backups.encode(backups.create(deviceName))
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(text.toByteArray())
                    }
                }
                _state.update { it.copy(message = AppLocale.strings.backup.saved, error = null) }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message) }
            }
        }
    }

    fun loadFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                _state.update { it.copy(pending = backups.decode(text), error = null, message = null) }
            } catch (error: NotABackupException) {
                _state.update { it.copy(error = AppLocale.strings.backup.notABackup) }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message) }
            }
        }
    }

    fun noFilePicker() = _state.update { it.copy(error = AppLocale.strings.backup.noFilePicker) }

    // -------------------------------------------------------------- apply

    fun cancelPending() = _state.update { it.copy(pending = null) }

    fun apply() {
        val backup = _state.value.pending ?: return
        stopAll()
        viewModelScope.launch {
            _state.update { it.copy(pending = null, busy = AppLocale.strings.connecting, error = null) }
            val failed = backups.restore(backup) { step -> _state.update { it.copy(busy = step) } }
            _state.update {
                if (failed.isEmpty()) {
                    it.copy(busy = null, finished = true)
                } else {
                    it.copy(busy = null, message = AppLocale.strings.backup.partly(failed.joinToString(", ")))
                }
            }
        }
    }
}

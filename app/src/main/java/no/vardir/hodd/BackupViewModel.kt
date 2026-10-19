package no.vardir.hodd

import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.vardir.hodd.core.StagedBackup

internal data class ImportPreview(val collections: Int, val items: Int, val photos: Int, val createdAt: Long, val titles: List<String>)

/** Kept with the screen ViewModel so staged imports survive rotation. */
internal class BackupActions(private val owner: HoddViewModel) {
    val preview = MutableStateFlow<ImportPreview?>(null)
    val message = MutableStateFlow<String?>(null)
    private var staged: StagedBackup? = null

    private fun task(success: String, block: suspend () -> Unit) {
        if (!owner.beginBackup()) return
        message.value = null
        owner.viewModelScope.launch {
            try { block(); message.value = success }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                message.value = error.message ?: "The backup operation failed. Keep your current files and try again."
            } finally { owner.endBackup() }
        }
    }
    fun export(uri: Uri) = task("Backup verified at the location you chose.") { owner.repository.exportBackup(uri) }
    fun inspect(uri: Uri) = task("Backup validated. Review it before replacing your collection.") {
        withContext(Dispatchers.IO) { staged?.discard() }; staged = null; preview.value = null
        val candidate = owner.repository.stageBackup(uri)
        staged = candidate
        preview.value = ImportPreview(candidate.catalog.collections.size, candidate.catalog.items.size, candidate.photoCount,
            candidate.manifest.createdAt, candidate.catalog.items.take(5).map { it.title })
    }
    fun restore(done: () -> Unit) = task("Backup restored. A recovery copy of the previous collection was kept.") {
        val candidate = requireNotNull(staged) { "Select the backup again." }
        owner.repository.restoreBackup(candidate)
        withContext(Dispatchers.IO) { candidate.discard() }
        staged = null; preview.value = null
        owner.backupRestored(); done()
    }
    fun recover(id: String, done: () -> Unit) = task("Recovery copy restored. The previous saved file was preserved.") {
        owner.repository.restoreRecovery(id)
        owner.backupRestored(); done()
    }
    fun discard() {
        if (owner.busy.value) return
        val candidate = staged; staged = null; preview.value = null; message.value = null
        if (candidate != null) owner.viewModelScope.launch(Dispatchers.IO) { candidate.discard() }
    }
    fun close() { staged?.discard(); staged = null }
}

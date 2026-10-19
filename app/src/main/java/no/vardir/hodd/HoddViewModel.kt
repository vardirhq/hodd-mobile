package no.vardir.hodd

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.vardir.hodd.core.Catalog
import no.vardir.hodd.core.CatalogItem
import no.vardir.hodd.core.exampleCatalog
import no.vardir.hodd.data.CatalogRepository

class HoddViewModel(application: Application) : AndroidViewModel(application) {
    val repository = CatalogRepository(application)
    val catalog = repository.catalog
    internal val recovery = repository.recovery
    internal val backups = BackupActions(this)
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    init { load() }

    fun load() = viewModelScope.launch {
        try { repository.load(); mutableReady.value = true; mutableError.value = recovery.value.warning }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableReady.value = false
            mutableError.value = "The saved collection could not be read. Nothing has been replaced. Retry, restore a recovery copy, or import a backup."
        }
    }

    private fun change(onSaved: () -> Unit = {}, block: (Catalog) -> Catalog) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { repository.update(block); onSaved() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableError.value = if (error is IllegalArgumentException) error.message else "Could not verify the save. Retry, or open Backups & recovery to review saved revisions."
            } finally { mutableBusy.value = false }
        }
    }
    fun save(item: CatalogItem, onSaved: () -> Unit) = change(onSaved) { it.save(item) }
    fun addCollection(name: String, onSaved: () -> Unit) = change(onSaved) { it.addCollection(name) }
    fun deleteItem(id: String, onSaved: () -> Unit) = change(onSaved) { it.removeItem(id) }
    fun loadExamples() = change { if (it.collections.isEmpty() && it.items.isEmpty()) exampleCatalog() else it }
    internal fun beginBackup(): Boolean {
        if (mutableBusy.value) return false
        mutableBusy.value = true
        return true
    }
    internal fun endBackup() { mutableBusy.value = false }
    internal fun backupRestored() { mutableReady.value = true; mutableError.value = null }
    override fun onCleared() { backups.close(); super.onCleared() }
    fun clearError() { mutableError.value = null }
    fun photo(uri: Uri, onSaved: (String?) -> Unit) = viewModelScope.launch {
        try { onSaved(repository.importPhoto(uri)) }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableError.value = error.message ?: "Could not add this photo."
            onSaved(null)
        }
    }
}

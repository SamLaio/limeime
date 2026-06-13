package net.toload.main.hd.ui.view

import net.toload.main.hd.data.Record

/**
 * Interface for ManageImFragment view updates.
 */
interface ManageImView : ViewUpdateListener {
    fun displayRecords(records: List<Record>?)
    fun updateRecordCount(count: Int)
    fun showAddRecordDialog()
    fun showEditRecordDialog(record: Record?)
    fun showDeleteConfirmDialog(id: Long)
    fun refreshRecordList()
}

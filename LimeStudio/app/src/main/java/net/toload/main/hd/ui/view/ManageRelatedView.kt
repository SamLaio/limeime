package net.toload.main.hd.ui.view

import net.toload.main.hd.data.Related

/**
 * Interface for ManageRelatedFragment view updates.
 */
interface ManageRelatedView : ViewUpdateListener {
    fun displayRelatedPhrases(phrases: List<Related>?)
    fun updatePhraseCount(count: Int)
    fun showAddPhraseDialog()
    fun showEditPhraseDialog(phrase: Related?)
    fun showDeleteConfirmDialog(id: Long)
    fun refreshPhraseList()
}

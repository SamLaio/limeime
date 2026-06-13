/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: https://github.com/SamLaio/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */
package net.toload.main.hd.ui.view

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import android.widget.CompoundButton
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.global.LIME
import net.toload.main.hd.R
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.controller.SetupImController
import net.toload.main.hd.ui.LIMESettings

/**
 * Fragment showing expandable per-IM download/import cards.
 * Replaces ImListFragment under Tab 1 (輸入法).
 */
class ImInstallFragment : Fragment() {
    private var setupImController: SetupImController? = null
    private var manageImController: ManageImController? = null
    private var activity: Activity? = null
    private var recyclerView: RecyclerView? = null
    private var adapter: ImFamilyAdapter? = null
    private var currentFamilies: MutableList<ImFamily>? = null

    // File picker launchers
    private var limedbLauncher: ActivityResultLauncher<Intent?>? = null
    private var txtLauncher: ActivityResultLauncher<Intent?>? = null

    // State for pending picker result
    private var pendingTableName: String? = null
    private var pendingIsRelated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        limedbLauncher = registerForActivityResult<Intent?, ActivityResult?>(
            StartActivityForResult(),
            ActivityResultCallback { result: ActivityResult? ->
                if (result!!.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    val uri = result.getData()!!.getData()
                    if (uri != null) {
                        val act: Activity? = activity
                        val ctrl: SetupImController? = setupImController
                        val tbl = pendingTableName!!
                        val rel = pendingIsRelated
                        val restore = getRestorePref(tbl)
                        Thread(Runnable {
                            val file = saveUriToFile(uri, act)
                            if (file != null && ctrl != null) {
                                if (rel) ctrl.importZippedDbRelated(file)
                                else ctrl.importZippedDb(file, tbl, restore)
                                if (act != null) act.runOnUiThread(Runnable { onInstallComplete(tbl) })
                            }
                        }).start()
                    }
                }
            })

        txtLauncher = registerForActivityResult<Intent?, ActivityResult?>(
            StartActivityForResult(),
            ActivityResultCallback { result: ActivityResult? ->
                if (result!!.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    val uri = result.getData()!!.getData()
                    if (uri != null) {
                        val act: Activity? = activity
                        val ctrl: SetupImController? = setupImController
                        val tbl = pendingTableName!!
                        val restore = getRestorePref(tbl)
                        Thread(Runnable {
                            val file = saveUriToFile(uri, act)
                            if (file != null && ctrl != null) {
                                ctrl.importTxtTable(
                                    file, tbl, restore,
                                    Runnable { onInstallComplete(tbl) })
                            }
                        }).start()
                    }
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activity = getActivity()

        if (activity is LIMESettings) {
            setupImController = (activity as LIMESettings).getSetupImController()
            manageImController = (activity as LIMESettings).getManageImController()
        } else {
            Log.w(TAG, "Activity is not LIMESettings; controllers unavailable")
        }

        val rootView: View = inflater.inflate(R.layout.fragment_im_install, container, false)

        // Toolbar with back navigation and refresh action
        val toolbar: MaterialToolbar =
            rootView.findViewById<MaterialToolbar>(R.id.im_install_toolbar)
        toolbar.setNavigationOnClickListener(View.OnClickListener { v: View? ->
            val host = getParentFragment()
            if (host != null) {
                host.getChildFragmentManager().popBackStack()
            }
        })
        toolbar.inflateMenu(R.menu.im_install_menu)
        toolbar.setOnMenuItemClickListener { item: MenuItem? ->
            if (item!!.getItemId() == R.id.action_refresh) {
                loadFamilyListAsync()
                true
            } else {
                false
            }
        }

        val rv = rootView.findViewById<RecyclerView>(R.id.im_install_list)
        recyclerView = rv
        rv.layoutManager = LinearLayoutManager(requireContext())
        ScrollableTabHelper.applyToRecyclerView(activity, rv)

        // Load installed state async, then set adapter
        loadFamilyListAsync()

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity = null
        setupImController = null
        manageImController = null
        recyclerView = null
        adapter = null
        currentFamilies = null
    }

    // -------- Async family list loader --------
    private fun loadFamilyListAsync() {
        val act: Activity? = activity
        val ctrl: ManageImController? = manageImController
        val rv: RecyclerView? = recyclerView
        Thread(Runnable {
            val families = buildFamilyList()
            if (ctrl != null) {
                for (family in families) {
                    // Check if the IM is registered in the `im` config table (matches IM List).
                    // Bundled-DB record-count alone gives false positives for tables like 倉頡
                    // that have seed records but were never registered as installed IMs.
                    var inConfig = false
                    val cfgList: MutableList<ImConfig> = ctrl.imConfigFullNameList
                    for (cfg in cfgList) {
                        if (family.tableName == cfg.code) {
                            inConfig = true
                            break
                        }
                    }
                    family.isInstalled = inConfig
                }
            }
            if (act == null || rv == null) return@Runnable
            act.runOnUiThread(Runnable {
                if (!isAdded()) return@Runnable
                currentFamilies = families
                adapter = ImFamilyAdapter(families)
                rv.setAdapter(adapter)
                ScrollableTabHelper.refreshRecyclerViewScrollbar(rv)
            })
        }).start()
    }

    // -------- Restore-learning preference --------
    private fun getRestorePref(tableName: String?): Boolean {
        val prefs: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        return prefs.getBoolean("restore_on_import_" + tableName, true)
    }

    private fun setRestorePref(tableName: String?, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putBoolean("restore_on_import_" + tableName, value)
            .apply()
    }

    // -------- File picker launchers --------
    private fun launchLimedbPicker(tableName: String, isRelated: Boolean) {
        pendingTableName = tableName
        pendingIsRelated = isRelated
        val intent: Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("*/*")
        limedbLauncher?.launch(intent)
    }

    private fun launchTxtPicker(tableName: String) {
        pendingTableName = tableName
        pendingIsRelated = false
        val intent: Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("*/*")
        txtLauncher?.launch(intent)
    }

    // -------- URI → File helper --------
    private fun saveUriToFile(uri: Uri, act: Activity?): File? {
        if (act == null) return null
        try {
            act.getContentResolver().openInputStream(uri).use { inputStream ->
                if (inputStream == null) return null
                val fileName = getFileName(uri, act)
                val file: File = File(act.getCacheDir(), fileName)
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(LIME.BUFFER_SIZE_1KB)
                    var length: Int
                    while ((inputStream.read(buffer).also { length = it }) > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
                return file
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file from URI", e)
            return null
        }
    }

    private fun getFileName(uri: Uri, act: Activity?): String {
        if (act == null) return "tmpfile"
        var result: String? = null
        if ("content" == uri.getScheme()) {
            act.getContentResolver().query(uri, null, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath()
            if (result != null) {
                val cut = result.lastIndexOf('/')
                if (cut != -1) result = result.substring(cut + 1)
            } else {
                result = "tmpfile"
            }
        }
        return result
    }

    // -------- Data model --------
    private class CloudVariant(
        val label: String?,
        val count: String?,
        val fileSize: String?,
        val url: String?
    )

    private class ImFamily(
        val tableName: String,
        val displayTitle: String?,
        val cloudVariants: MutableList<CloudVariant>,
        val hasRestoreSwitch: Boolean,
        val isRelated: Boolean,
        val isCustom: Boolean,
        val iconResId: Int
    ) {
        var isInstalled: Boolean = false // populated async before adapter is set
    }

    private fun buildFamilyList(): MutableList<ImFamily> {
        val list: MutableList<ImFamily> = ArrayList<ImFamily>()

        // 注音
        val phonetic: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        phonetic.add(
            CloudVariant(
                getString(R.string.im_install_variant_openvanilla_phonetic), "34,838", "589 KB",
                LIME.DATABASE_CLOUD_IM_PHONETIC
            )
        )
        phonetic.add(
            CloudVariant(
                getString(R.string.im_install_variant_openvanilla_phonetic_big5),
                "15,945",
                "465 KB",
                LIME.DATABASE_CLOUD_IM_PHONETIC_BIG5
            )
        )
        phonetic.add(
            CloudVariant(
                getString(R.string.im_install_variant_phonetic_complete), "95,029", "2.8 MB",
                LIME.DATABASE_CLOUD_IM_PHONETICCOMPLETE
            )
        )
        phonetic.add(
            CloudVariant(
                getString(R.string.im_install_variant_phonetic_complete_big5), "76,122", "2.3 MB",
                LIME.DATABASE_CLOUD_IM_PHONETICCOMPLETE_BIG5
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_PHONETIC,
                getString(R.string.im_install_family_phonetic),
                phonetic,
                true,
                false,
                false,
                R.drawable.ic_keyboard_outline
            )
        )

        // 倉頡
        val cj: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        cj.add(
            CloudVariant(
                getString(R.string.im_install_variant_cj), "28,596", "830 KB",
                LIME.DATABASE_CLOUD_IM_CJ
            )
        )
        cj.add(
            CloudVariant(
                getString(R.string.im_install_variant_cj_big5), "13,859", "506 KB",
                LIME.DATABASE_CLOUD_IM_CJ_BIG5
            )
        )
        cj.add(
            CloudVariant(
                getString(R.string.im_install_variant_cj_hk), "30,278", "884 KB",
                LIME.DATABASE_CLOUD_IM_CJHK
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_CJ, getString(R.string.im_install_family_cj), cj, true, false, false,
                R.drawable.ic_grid_on_24
            )
        )

        // 四碼倉頡
        val cj4: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        cj4.add(
            CloudVariant(
                getString(R.string.im_install_variant_cj4), "33,021", "598 KB",
                LIME.DATABASE_CLOUD_IM_CJ4
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_CJ4,
                getString(R.string.im_install_family_cj4),
                cj4,
                true,
                false,
                false,
                R.drawable.ic_grid_on_24
            )
        )

        // 倉頡五代
        val cj5: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        cj5.add(
            CloudVariant(
                getString(R.string.im_install_variant_cj5), "24,004", "491 KB",
                LIME.DATABASE_CLOUD_IM_CJ5
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_CJ5,
                getString(R.string.im_install_family_cj5),
                cj5,
                true,
                false,
                false,
                R.drawable.ic_grid_on_24
            )
        )

        // 快倉
        val scj: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        scj.add(
            CloudVariant(
                getString(R.string.im_install_variant_scj), "74,250", "1.4 MB",
                LIME.DATABASE_CLOUD_IM_SCJ
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_SCJ,
                getString(R.string.im_install_family_scj),
                scj,
                true,
                false,
                false,
                R.drawable.ic_grid_on_24
            )
        )

        // 速成
        val ecj: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        ecj.add(
            CloudVariant(
                getString(R.string.im_install_variant_ecj), "13,119", "136 KB",
                LIME.DATABASE_CLOUD_IM_ECJ
            )
        )
        ecj.add(
            CloudVariant(
                getString(R.string.im_install_variant_ecj_hk), "27,853", "210 KB",
                LIME.DATABASE_CLOUD_IM_ECJHK
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_ECJ,
                getString(R.string.im_install_family_ecj),
                ecj,
                true,
                false,
                false,
                R.drawable.ic_grid_on_24
            )
        )

        // 大易
        val dayi: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        dayi.add(
            CloudVariant(
                getString(R.string.im_install_variant_openvanilla_dayi), "18,638", "486 KB",
                LIME.DATABASE_CLOUD_IM_DAYI
            )
        )
        dayi.add(
            CloudVariant(
                getString(R.string.im_install_variant_dayi_unicode_char), "27,198", "584 KB",
                LIME.DATABASE_CLOUD_IM_DAYIUNI
            )
        )
        dayi.add(
            CloudVariant(
                getString(R.string.im_install_variant_dayi_unicode_phrase), "117,766", "2.6 MB",
                LIME.DATABASE_CLOUD_IM_DAYIUNIP
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_DAYI,
                getString(R.string.im_install_family_dayi),
                dayi,
                true,
                false,
                false,
                R.drawable.ic_textformat_alt
            )
        )

        // 輕鬆
        val ez: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        ez.add(
            CloudVariant(
                getString(R.string.im_install_variant_ez), "14,422", "237 KB",
                LIME.DATABASE_CLOUD_IM_EZ
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_EZ, getString(R.string.im_install_family_ez), ez, true, false, false,
                R.drawable.ic_hand_tap
            )
        )

        // 行列
        val array: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        array.add(
            CloudVariant(
                getString(R.string.im_install_variant_array), "32,386", "524 KB",
                LIME.DATABASE_CLOUD_IM_ARRAY
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_ARRAY,
                getString(R.string.im_install_family_array),
                array,
                true,
                false,
                false,
                R.drawable.ic_grid_on_24
            )
        )

        // 行列10
        val array10: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        array10.add(
            CloudVariant(
                getString(R.string.im_install_variant_array10), "32,120", "558 KB",
                LIME.DATABASE_CLOUD_IM_ARRAY10
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_ARRAY10,
                getString(R.string.im_install_family_array10),
                array10,
                true,
                false,
                false,
                R.drawable.ic_grid_on_24
            )
        )

        // 筆順
        val wb: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        wb.add(
            CloudVariant(
                getString(R.string.im_install_variant_wb), "26,378", "267 KB",
                LIME.DATABASE_CLOUD_IM_WB
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_WB, getString(R.string.im_install_family_wb), wb, true, false, false,
                R.drawable.ic_pencil_outline
            )
        )

        // 華象
        val hs: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        hs.add(
            CloudVariant(
                getString(R.string.im_install_variant_hs_full), "183,659", "3.5 MB",
                LIME.DATABASE_CLOUD_IM_HS
            )
        )
        hs.add(
            CloudVariant(
                getString(R.string.im_install_variant_hs_v1), "50,845", "830 KB",
                LIME.DATABASE_CLOUD_IM_HS_V1
            )
        )
        hs.add(
            CloudVariant(
                getString(R.string.im_install_variant_hs_v2), "50,838", "834 KB",
                LIME.DATABASE_CLOUD_IM_HS_V2
            )
        )
        hs.add(
            CloudVariant(
                getString(R.string.im_install_variant_hs_v3), "64,324", "1000 KB",
                LIME.DATABASE_CLOUD_IM_HS_V3
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_HS, getString(R.string.im_install_family_hs), hs, true, false, false,
                R.drawable.ic_wand_stars
            )
        )

        // 拼音
        val pinyin: MutableList<CloudVariant> = ArrayList<CloudVariant>()
        pinyin.add(
            CloudVariant(
                getString(R.string.im_install_variant_pinyin), "34,753", "509 KB",
                LIME.DATABASE_CLOUD_IM_PINYIN
            )
        )
        pinyin.add(
            CloudVariant(
                getString(R.string.im_install_variant_pinyin_gb), "34,753", "502 KB",
                LIME.DATABASE_CLOUD_IM_PINYINGB
            )
        )
        list.add(
            ImFamily(
                LIME.DB_TABLE_PINYIN,
                getString(R.string.im_install_family_pinyin),
                pinyin,
                true,
                false,
                false,
                R.drawable.ic_text_bubble
            )
        )

        // 自建 (CUSTOM) — no restore switch, no cloud buttons
        // TODO §2.3 — seedCustomIM verification: ensure seedCustomIM is invoked after successful custom-IM import
        list.add(
            ImFamily(
                LIME.DB_TABLE_CUSTOM,
                getString(R.string.im_install_family_custom),
                ArrayList<CloudVariant>(),
                false,
                false,
                true,
                R.drawable.ic_person_crop_rectangle
            )
        )

        // 關聯字庫 (RELATED) — no restore switch, no cloud buttons, no txt import
        list.add(
            ImFamily(
                LIME.DB_TABLE_RELATED,
                getString(R.string.im_install_family_related),
                ArrayList<CloudVariant>(),
                false,
                true,
                false,
                R.drawable.ic_text_bubble
            )
        )

        return list
    }

    // -------- Adapter --------
    private inner class ImFamilyAdapter(private val families: MutableList<ImFamily>) :
        RecyclerView.Adapter<ImFamilyViewHolder?>() {
        val expanded: BooleanArray

        init {
            this.expanded = BooleanArray(families.size)
            for (i in families.indices) {
                expanded[i] = !families.get(i).isInstalled
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImFamilyViewHolder {
            val v: View = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_im_family_card, parent, false)
            return ImFamilyViewHolder(v)
        }

        override fun onBindViewHolder(holder: ImFamilyViewHolder, position: Int) {
            val family = families.get(position)
            holder.bind(family, expanded[position], Runnable {
                val pos = holder.getLayoutPosition()
                if (pos == RecyclerView.NO_POSITION) return@Runnable
                expanded[pos] = !expanded[pos]
                notifyItemChanged(pos)
            })
        }

        override fun getItemCount(): Int = families.size
    }

    // -------- ViewHolder --------
    private inner class ImFamilyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardHeader: LinearLayout
        val ivFamilyIcon: ImageView
        val tvTitle: TextView
        val tvInstalledBadge: TextView
        val ivChevron: ImageView
        val bodyContainer: LinearLayout
        val switchRestoreLearning: SwitchMaterial
        val cloudButtonsContainer: LinearLayout
        val btnImportLimedb: MaterialButton
        val btnImportTxt: MaterialButton
        val btnImportDefaultRelated: MaterialButton

        init {
            cardHeader = itemView.findViewById<LinearLayout>(R.id.card_header)
            ivFamilyIcon = itemView.findViewById<ImageView>(R.id.iv_family_icon)
            tvTitle = itemView.findViewById<TextView>(R.id.tv_im_title)
            tvInstalledBadge = itemView.findViewById<TextView>(R.id.tv_installed_badge)
            ivChevron = itemView.findViewById<ImageView>(R.id.iv_chevron)
            bodyContainer = itemView.findViewById<LinearLayout>(R.id.body_container)
            switchRestoreLearning =
                itemView.findViewById<SwitchMaterial>(R.id.switch_restore_learning)
            cloudButtonsContainer =
                itemView.findViewById<LinearLayout>(R.id.cloud_buttons_container)
            btnImportLimedb = itemView.findViewById<MaterialButton>(R.id.btn_import_limedb)
            btnImportTxt = itemView.findViewById<MaterialButton>(R.id.btn_import_txt)
            btnImportDefaultRelated =
                itemView.findViewById<MaterialButton>(R.id.btn_import_default_related)
        }

        fun bind(family: ImFamily, isExpanded: Boolean, toggleExpand: Runnable) {
            // Family icon
            if (family.iconResId != 0) {
                ivFamilyIcon.setImageResource(family.iconResId)
                ivFamilyIcon.setVisibility(View.VISIBLE)
            } else {
                ivFamilyIcon.setVisibility(View.GONE)
            }

            tvTitle.setText(family.displayTitle)

            // Installed badge + chevron + header click lock
            if (family.isInstalled) {
                tvInstalledBadge.setVisibility(View.VISIBLE)
                ivChevron.setVisibility(View.GONE)
                cardHeader.setOnClickListener(null)
                cardHeader.setClickable(false)
            } else {
                tvInstalledBadge.setVisibility(View.GONE)
                ivChevron.setVisibility(View.VISIBLE)
                cardHeader.setClickable(true)
                cardHeader.setOnClickListener(View.OnClickListener { v: View? ->
                    val toDeg = if (isExpanded) 0f else 180f
                    ivChevron.animate().rotation(toDeg).setDuration(200).start()
                    toggleExpand.run()
                })
            }

            // Expand/collapse
            bodyContainer.setVisibility(if (isExpanded) View.VISIBLE else View.GONE)
            ivChevron.clearAnimation()
            ivChevron.setRotation(if (isExpanded) 180f else 0f)

            // Restore switch visibility
            if (family.hasRestoreSwitch) {
                switchRestoreLearning.setVisibility(View.VISIBLE)
                switchRestoreLearning.setOnCheckedChangeListener(null)
                switchRestoreLearning.setChecked(getRestorePref(family.tableName))
                switchRestoreLearning.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                    setRestorePref(
                        family.tableName,
                        isChecked
                    )
                })
            } else {
                switchRestoreLearning.setVisibility(View.GONE)
            }

            // Cloud variant rows — rebuild each bind to avoid stale listeners
            cloudButtonsContainer.removeAllViews()
            if (!family.cloudVariants.isEmpty()) {
                cloudButtonsContainer.setVisibility(View.VISIBLE)
                for (variant in family.cloudVariants) {
                    val row: View = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_cloud_variant, cloudButtonsContainer, false)
                    val tvName: TextView = row.findViewById<TextView>(R.id.tv_variant_name)
                    val tvMeta: TextView = row.findViewById<TextView>(R.id.tv_variant_meta)
                    val btnInstall: MaterialButton =
                        row.findViewById<MaterialButton>(R.id.btn_install)

                    tvName.setText(variant.label)
                    tvMeta.setText(variant.count + " · " + variant.fileSize)

                    val url = variant.url
                    btnInstall.setOnClickListener(View.OnClickListener { v: View? ->
                        val controller = setupImController
                        if (controller != null) {
                            controller.downloadAndImportZippedDb(
                                family.tableName, url,
                                switchRestoreLearning.isChecked(),
                                Runnable { onInstallComplete(family.tableName) })
                        }
                    })
                    cloudButtonsContainer.addView(row)
                }
            } else {
                cloudButtonsContainer.setVisibility(View.GONE)
            }

            // .limedb import button (all families)
            btnImportLimedb.setOnClickListener(View.OnClickListener { v: View? ->
                launchLimedbPicker(
                    family.tableName,
                    family.isRelated
                )
            })

            // .cin/.lime import (hidden for RELATED)
            if (family.isRelated) {
                btnImportTxt.setVisibility(View.GONE)
            } else {
                btnImportTxt.setVisibility(View.VISIBLE)
                btnImportTxt.setOnClickListener(View.OnClickListener { v: View? ->
                    launchTxtPicker(
                        family.tableName
                    )
                })
            }

            // Default related button (visible only for RELATED)
            if (family.isRelated) {
                btnImportDefaultRelated.setVisibility(View.VISIBLE)
                btnImportDefaultRelated.setOnClickListener(View.OnClickListener { v: View? ->
                    showDefaultRelatedConfirmDialog(
                        Runnable { onInstallComplete(family.tableName) })
                })
            } else {
                btnImportDefaultRelated.setVisibility(View.GONE)
            }
        }
    }

    // -------- Install-complete callback --------
    /**
     * Called after any install path succeeds. Marks the family installed immediately,
     * collapses its card, and refreshes just that item.
     * Must be called on the main thread.
     */
    // TODO §2.3 — verify progress hookup: ensure ProgressManager show/hide is called around download and import operations
    private fun onInstallComplete(tableName: String) {
        if (!isAdded() || currentFamilies == null || adapter == null) return
        for (i in currentFamilies!!.indices) {
            if (tableName == currentFamilies!!.get(i).tableName) {
                currentFamilies!!.get(i).isInstalled = true
                val currentAdapter = adapter ?: return
                currentAdapter.expanded[i] = false
                currentAdapter.notifyItemChanged(i)
                break
            }
        }
    }

    private fun showDefaultRelatedConfirmDialog(onSuccess: Runnable?) {
        if (activity == null) return
        AlertDialog.Builder(activity)
            .setMessage(R.string.setup_im_import_related_default_confirm)
            .setPositiveButton(
                R.string.dialog_confirm,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    val controller = setupImController
                    if (controller != null) {
                        controller.importDbDefaultRelated()
                        if (onSuccess != null) onSuccess.run()
                    }
                })
            .setNegativeButton(
                R.string.dialog_cancel,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> dialog?.dismiss() })
            .show()
    }

    companion object {
        private const val TAG = "ImInstallFragment"

        @JvmStatic
        fun newInstance(): ImInstallFragment {
            return ImInstallFragment()
        }
    }
}

package net.toload.main.hd.ui.view

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.R
import net.toload.main.hd.SearchServer
import net.toload.main.hd.ui.ShareManager
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.LIMESettings

/**
 * Per-IM detail screen shown in the detail pane of TwoPaneHostFragment.
 * 
 * 
 * Displays IM info (name, record count), keyboard layout picker,
 * link to ManageImFragment, options, and a remove button stub.
 */
class ImDetailFragment : Fragment() {
    private var activity: Activity? = null
    private lateinit var manageImController: ManageImController

    private var tableCode: String? = null
    private var imDesc: String? = null

    private lateinit var tvImName: TextView
    private lateinit var tvImRecords: TextView
    private lateinit var txtImVersion: TextView
    private lateinit var txtImEndkey: TextView
    private lateinit var tvKeyboardValue: TextView
    private lateinit var tvHeading: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (getArguments() != null) {
            tableCode = getArguments()!!.getString(ARG_IM_CODE)
            imDesc = getArguments()!!.getString(ARG_IM_DESC)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activity = getActivity()

        if (activity is LIMESettings) {
            manageImController = (activity as LIMESettings).getManageImController()
        } else {
            Log.w(TAG, "Activity is not LIMESettings; ManageImController unavailable")
        }

        val rootView: View = inflater.inflate(R.layout.fragment_im_detail, container, false)
        val scrollView: NestedScrollView? =
            rootView.findViewById<NestedScrollView?>(R.id.im_detail_scroll)
        if (scrollView != null) {
            ScrollableTabHelper.applyToNestedScrollView(activity, scrollView)
        }

        // Toolbar with back navigation (title is rendered by tv_im_detail_heading below)
        val toolbar: MaterialToolbar =
            rootView.findViewById<MaterialToolbar>(R.id.im_detail_toolbar)
        toolbar.setClickable(true)
        toolbar.setFocusable(true)
        toolbar.setTitle("")

        tvHeading = rootView.findViewById<TextView>(R.id.tv_im_detail_heading)
        tvHeading.setText(if (imDesc != null) imDesc else "")
        toolbar.setNavigationOnClickListener(View.OnClickListener { v: View? ->
            val host = getParentFragment()
            if (host != null) {
                host.getChildFragmentManager().popBackStack()
            }
        })
        // Also register OnBackPressedCallback because SlidingPaneLayout may intercept toolbar taps
        requireActivity().onBackPressedDispatcher.addCallback(
            getViewLifecycleOwner(),
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val host = getParentFragment()
                    if (host != null) {
                        host.getChildFragmentManager().popBackStack()
                    }
                }
            })

        tvImName = rootView.findViewById<TextView>(R.id.tv_im_name)
        tvImRecords = rootView.findViewById<TextView>(R.id.tv_im_records)
        txtImVersion = rootView.findViewById<TextView>(R.id.txtImVersion)
        txtImEndkey = rootView.findViewById<TextView>(R.id.txtImEndkey)
        tvKeyboardValue = rootView.findViewById<TextView>(R.id.tv_keyboard_value)

        if (imDesc != null) {
            tvImName.setText(imDesc)
        }

        val rowName: LinearLayout? = rootView.findViewById<LinearLayout?>(R.id.row_name)
        val rowVersion: LinearLayout? = rootView.findViewById<LinearLayout?>(R.id.row_version)
        val rowEndkey: LinearLayout? = rootView.findViewById<LinearLayout?>(R.id.row_endkey)

        // Keyboard row click -> show picker
        val rowKeyboard: LinearLayout = rootView.findViewById<LinearLayout>(R.id.row_keyboard)
        rowKeyboard.setOnClickListener(View.OnClickListener { v: View? -> showKeyboardPicker() })

        val isRelated = "related" == tableCode

        // 字根資料表 row click -> navigate to ManageImFragment (or ManageRelatedFragment for the synthetic 關聯字庫 row)
        val rowManageTable: LinearLayout =
            rootView.findViewById<LinearLayout>(R.id.row_manage_table)
        rowManageTable.setOnClickListener(View.OnClickListener { v: View? ->
            val parent = getParentFragment()
            if (parent is TwoPaneHostFragment && tableCode != null) {
                if (isRelated) {
                    parent.navigateToDetail(
                        ManageRelatedFragment.newInstance(1)
                    )
                } else {
                    parent.navigateToDetail(
                        ManageImFragment.newInstance(1, tableCode)
                    )
                }
            }
        })

        // Apply related-row variations: hide sections that don't apply, retext labels
        if (isRelated) {
            val sectionKeyboard = rootView.findViewById<View?>(R.id.section_keyboard)
            val sectionOptions = rootView.findViewById<View?>(R.id.section_options)
            val dividerVersion = rootView.findViewById<View?>(R.id.divider_version)
            val dividerEndkey = rootView.findViewById<View?>(R.id.divider_endkey)
            val editNameIcon = rootView.findViewById<View?>(R.id.iv_edit_name)
            val tvSectionTableLabel: TextView? =
                rootView.findViewById<TextView?>(R.id.tv_section_table_label)
            val tvManageTableLabel: TextView? =
                rootView.findViewById<TextView?>(R.id.tv_manage_table_label)
            if (sectionKeyboard != null) sectionKeyboard.setVisibility(View.GONE)
            if (sectionOptions != null) sectionOptions.setVisibility(View.GONE)
            if (rowVersion != null) rowVersion.setVisibility(View.GONE)
            if (rowEndkey != null) rowEndkey.setVisibility(View.GONE)
            if (dividerVersion != null) dividerVersion.setVisibility(View.GONE)
            if (dividerEndkey != null) dividerEndkey.setVisibility(View.GONE)
            if (editNameIcon != null) editNameIcon.setVisibility(View.GONE)
            if (tvSectionTableLabel != null) tvSectionTableLabel.setText(R.string.im_detail_section_related)
            if (tvManageTableLabel != null) tvManageTableLabel.setText(R.string.im_detail_manage_related)
        }

        if (!isRelated) {
            if (rowName != null) {
                rowName.setClickable(true)
                rowName.setFocusable(true)
                applySelectableBackground(rowName)
                rowName.setOnClickListener(View.OnClickListener { v: View? ->
                    showMetadataFieldEditor(
                        "name"
                    )
                })
            }
            if (rowVersion != null) {
                rowVersion.setClickable(true)
                rowVersion.setFocusable(true)
                applySelectableBackground(rowVersion)
                rowVersion.setOnClickListener(View.OnClickListener { v: View? ->
                    showMetadataFieldEditor(
                        "version"
                    )
                })
            }
            if (rowEndkey != null) {
                rowEndkey.setClickable(true)
                rowEndkey.setFocusable(true)
                applySelectableBackground(rowEndkey)
                rowEndkey.setOnClickListener(View.OnClickListener { v: View? ->
                    showMetadataFieldEditor(
                        LIME.IM_LIME_ENDKEY
                    )
                })
            }
        }

        // 備份選項 switch - bound to SharedPreferences (skipped for related since options card is hidden)
        val switchBackup: SwitchMaterial =
            rootView.findViewById<SwitchMaterial>(R.id.switch_backup_on_delete)
        if (tableCode != null && !isRelated) {
            val prefs: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(requireContext())
            val backupPref: Boolean = prefs.getBoolean("backup_on_delete_" + tableCode, true)
            switchBackup.setChecked(backupPref)
            switchBackup.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { btn: CompoundButton?, checked: Boolean ->
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putBoolean("backup_on_delete_" + tableCode, checked)
                    .apply()
            })
        }

        // Conditional sections based on tableCode
        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        if ("custom" == tableCode) {
            rootView.findViewById<View?>(R.id.section_custom_mapping).setVisibility(View.VISIBLE)
            val sNum: SwitchMaterial =
                rootView.findViewById<SwitchMaterial>(R.id.switchAcceptNumberIndex)
            val sSym: SwitchMaterial =
                rootView.findViewById<SwitchMaterial>(R.id.switchAcceptSymbolIndex)
            sNum.setChecked(sp.getBoolean("accept_number_index", false))
            sSym.setChecked(sp.getBoolean("accept_symbol_index", false))
            sNum.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { b: CompoundButton?, c: Boolean ->
                sp.edit().putBoolean("accept_number_index", c).apply()
            })
            sSym.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { b: CompoundButton?, c: Boolean ->
                sp.edit().putBoolean("accept_symbol_index", c).apply()
            })
        }

        if ("array10" == tableCode) {
            rootView.findViewById<View?>(R.id.section_array10).setVisibility(View.VISIBLE)
            // TODO §7 backport — spinner wiring for auto_commit (full array-adapter binding)
            val spinnerAutoCommit: Spinner = rootView.findViewById<Spinner>(R.id.spinnerAutoCommit)
            val autoCommitLabels = getResources().getStringArray(R.array.auto_commit_labels)
            val autoCommitValues = getResources().getStringArray(R.array.auto_commit_values)
            val autoCommitAdapter: ArrayAdapter<String?> = ArrayAdapter<String?>(
                requireContext(),
                android.R.layout.simple_spinner_item, autoCommitLabels
            )
            autoCommitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerAutoCommit.setAdapter(autoCommitAdapter)
            val savedAutoCommit: String = sp.getString("auto_commit", "0") ?: "0"
            for (i in autoCommitValues.indices) {
                if (autoCommitValues[i] == savedAutoCommit) {
                    spinnerAutoCommit.setSelection(i)
                    break
                }
            }
            val autoCommitValuesFinal = autoCommitValues
            spinnerAutoCommit.setOnItemSelectedListener(object :
                AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    sp.edit().putString("auto_commit", autoCommitValuesFinal[pos]).apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            })
        }

        if ("phonetic" == tableCode) {
            rootView.findViewById<View?>(R.id.section_phonetic).setVisibility(View.VISIBLE)
            // TODO §7 backport — spinner wiring for phonetic_keyboard_type
            val spinnerPhonetic: Spinner = rootView.findViewById<Spinner>(R.id.spinnerPhoneticType)
            val phoneticLabels = getResources().getStringArray(R.array.phonetic_keyboard_type)
            val phoneticValues =
                getResources().getStringArray(R.array.phonetic_keyboard_type_values)
            val phoneticAdapter: ArrayAdapter<String?> = ArrayAdapter<String?>(
                requireContext(),
                android.R.layout.simple_spinner_item, phoneticLabels
            )
            phoneticAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPhonetic.setAdapter(phoneticAdapter)
            val savedPhonetic: String = sp.getString("phonetic_keyboard_type", "standard") ?: "standard"
            for (i in phoneticValues.indices) {
                if (phoneticValues[i] == savedPhonetic) {
                    spinnerPhonetic.setSelection(i)
                    break
                }
            }
            val phoneticValuesFinal = phoneticValues
            spinnerPhonetic.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val newType = phoneticValuesFinal[pos]
                    val oldType: String = sp.getString("phonetic_keyboard_type", "standard") ?: "standard"
                    sp.edit().putString("phonetic_keyboard_type", newType).apply()
                    if (newType != oldType) {
                        applyPhoneticKeyboardType(newType)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            })
        }

        // 移除輸入法 button (also available for related — lets users clear and reload their own table)
        val btnRemove: MaterialButton = rootView.findViewById<MaterialButton>(R.id.btn_remove_im)
        btnRemove.setOnClickListener(View.OnClickListener { v: View? -> showRemoveConfirmDialog() })

        // Load version from IM metadata, retaining legacy SharedPreferences fallback.
        if (tableCode != null) {
            var version: String? = ""
            var endkey = ""
            try {
                if (manageImController != null && manageImController.getSearchServer() != null) {
                    val searchServer: SearchServer = manageImController.getSearchServer()
                    version = searchServer.getImConfig(tableCode, "version")
                    endkey = searchServer.getImConfig(tableCode, LIME.IM_LIME_ENDKEY)
                }
            } catch (ignored: Exception) {
                version = ""
                endkey = ""
            }
            if (version == null || version.isEmpty()) {
                val versionSp: SharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                version = versionSp.getString(tableCode + "mapping_version", "")
            }
            if (version == null || version.isEmpty()) {
                try {
                    if (manageImController != null && manageImController.getSearchServer() != null) {
                        version =
                            manageImController.getSearchServer().getImConfig(tableCode, "source")
                    }
                } catch (ignored: Exception) {
                    version = ""
                }
            }
            if (version == null || version.isEmpty()) {
                try {
                    if (manageImController != null && manageImController.getSearchServer() != null) {
                        version =
                            manageImController.getSearchServer().getImConfig(tableCode, "name")
                    }
                } catch (ignored: Exception) {
                    version = ""
                }
            }
            if (version == null || version.isEmpty()) version = "-"
            if (txtImVersion != null) txtImVersion.setText(version)
            if (endkey == null || endkey.isEmpty()) endkey = "-"
            if (txtImEndkey != null) txtImEndkey.setText(endkey)
        }

        // Share button (plain ImageButton overlaying toolbar — direct click handler)
        val btnShare: ImageButton? = rootView.findViewById<ImageButton?>(R.id.btn_im_share)
        if (btnShare != null) {
            btnShare.setClickable(true)
            btnShare.setFocusable(true)
            btnShare.bringToFront()
            btnShare.setOnClickListener(View.OnClickListener { v: View? -> showShareFormatDialog() })
        }

        // Load async data
        loadRecordCount()
        loadCurrentKeyboard()

        return rootView
    }

    /**
     * Apply a phonetic_keyboard_type change to the `im` table — mirrors the
     * LIMEPreference.onSharedPreferenceChanged logic so the soft-keyboard layout
     * follows the picker selection. Also refreshes the 鍵盤布局 row UI.
     */
    private fun applyPhoneticKeyboardType(newType: String) {
        val ctrl: ManageImController? = manageImController
        if (ctrl == null) return
        val ss: SearchServer = ctrl.getSearchServer()
        if (ss == null) return
        val numberRow = PreferenceManager
            .getDefaultSharedPreferences(requireContext())
            .getBoolean("number_row_in_english", false)
        Thread(Runnable {
            try {
                val kb: Keyboard?
                when (newType) {
                    net.toload.main.hd.global.LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN -> kb =
                        ss.getKeyboardConfig("phoneticet41")

                    net.toload.main.hd.global.LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26 -> kb =
                        ss.getKeyboardConfig(if (numberRow) "limenum" else "lime")

                    "eten26_symbol" -> kb = ss.getKeyboardConfig("et26")
                    net.toload.main.hd.global.LIME.IM_PHONETIC_KEYBOARD_HSU -> kb =
                        ss.getKeyboardConfig(if (numberRow) "limenum" else "lime")

                    "hsu_symbol" -> kb =
                        ss.getKeyboardConfig(net.toload.main.hd.global.LIME.IM_PHONETIC_KEYBOARD_HSU)

                    net.toload.main.hd.global.LIME.IM_PHONETIC_STANDARD -> kb =
                        ss.getKeyboardConfig("phonetic")

                    else -> kb = ss.getKeyboardConfig("phonetic")
                }
                if (kb != null) {
                    ss.setIMKeyboard("phonetic", kb.desc, kb.code)
                    val kbFinal: Keyboard? = kb
                    activity?.runOnUiThread(Runnable {
                        tvKeyboardValue.setText(kbFinal!!.desc)
                    })
                }
            } catch (e: Exception) {
                Log.e("ImDetailFragment", "applyPhoneticKeyboardType failed", e)
            }
        }).start()
    }

    private fun showShareFormatDialog() {
        if (tableCode == null) return
        val act: Activity = requireActivity()
        if (act !is LIMESettings) return
        val shareManager: ShareManager? = (act as LIMESettings).shareManager
        if (shareManager == null) return

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.share_dialog_title)
            .setItems(
                arrayOf<CharSequence?>(
                    getString(R.string.share_format_text),
                    getString(R.string.share_format_database)
                ), DialogInterface.OnClickListener { d: DialogInterface?, which: Int ->
                    if (which == 0) {
                        shareManager.shareImAsText(tableCode)
                    } else {
                        shareManager.exportAndShareImTable(tableCode)
                    }
                })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun applySelectableBackground(view: View?) {
        if (view == null || activity == null) return
        val outValue: TypedValue = TypedValue()
        activity!!.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        view.setBackgroundResource(outValue.resourceId)
    }

    private fun showMetadataFieldEditor(field: String?) {
        if (activity == null || tableCode == null || "related" == tableCode) return
        val editingName = "name" == field
        val editingVersion = "version" == field
        val editingEndkey = LIME.IM_LIME_ENDKEY.equals(field)
        if (!editingName && !editingVersion && !editingEndkey) return

        val form: LinearLayout = LinearLayout(activity)
        form.setOrientation(LinearLayout.VERTICAL)
        val padding = (24 * getResources().getDisplayMetrics().density).toInt()
        form.setPadding(padding, 8, padding, 0)

        val valueInput: EditText = EditText(activity)
        valueInput.setSingleLine(true)
        valueInput.setHint(
            if (editingName)
                R.string.im_detail_label_name
            else
                (if (editingVersion) R.string.im_detail_label_version else R.string.im_detail_label_endkey)
        )
        valueInput.setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val currentText: CharSequence?
        if (editingName) {
            currentText = if (tvImName != null) tvImName.getText() else ""
        } else if (editingVersion) {
            currentText = if (txtImVersion != null) txtImVersion.getText() else ""
        } else {
            currentText = if (txtImEndkey != null) txtImEndkey.getText() else ""
        }
        val currentValue = if (currentText == null) "" else currentText.toString()
        valueInput.setText(if (!editingName && "-" == currentValue) "" else currentValue)
        form.addView(
            valueInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog: AlertDialog = AlertDialog.Builder(activity)
            .setTitle(
                if (editingName)
                    R.string.im_detail_edit_name_title
                else
                    (if (editingVersion) R.string.im_detail_edit_version_title else R.string.im_detail_edit_endkey_title)
            )
            .setView(form)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.manage_im_save, null)
            .create()
        dialog.setOnShowListener(DialogInterface.OnShowListener { d: DialogInterface? ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                View.OnClickListener { v: View? ->
                    val editedValue =
                        if (valueInput.getText() == null) "" else valueInput.getText().toString()
                            .trim { it <= ' ' }
                    if (editingName && editedValue.isEmpty()) {
                        valueInput.setError(getString(R.string.im_detail_edit_metadata_empty_name))
                        return@OnClickListener
                    }

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false)
                    val ctrl: ManageImController? = manageImController
                    val act: Activity? = activity
                    val table = tableCode
                    Thread(Runnable {
                        val saved =
                            ctrl != null && ctrl.updateIMMetadataField(table, field, editedValue)
                        if (act == null) return@Runnable
                        act.runOnUiThread(Runnable {
                            if (!isAdded() || activity == null) return@Runnable
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true)
                            if (!saved) {
                                Toast.makeText(
                                    activity,
                                    R.string.im_detail_edit_metadata_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Runnable
                            }
                            if (editingName) {
                                imDesc = editedValue
                                if (tvImName != null) tvImName.setText(editedValue)
                                if (tvHeading != null) tvHeading.setText(editedValue)
                            } else if (editingVersion && txtImVersion != null) {
                                txtImVersion.setText(if (editedValue.isEmpty()) "-" else editedValue)
                            } else if (editingEndkey && txtImEndkey != null) {
                                txtImEndkey.setText(if (editedValue.isEmpty()) "-" else editedValue)
                            }
                            refreshListPane()
                            dialog.dismiss()
                        })
                    }).start()
                })
        })
        dialog.show()
    }

    private fun refreshListPane() {
        val parent = getParentFragment()
        if (parent == null) return
        val listFragment = parent.getChildFragmentManager().findFragmentById(R.id.im_list_pane)
        if (listFragment is ImListFragment) {
            listFragment.refreshList()
        }
    }

    private fun loadRecordCount() {
        val ctrl: ManageImController? = manageImController
        val act: Activity? = activity
        val table = tableCode
        if (ctrl == null || table == null) return

        Thread(Runnable {
            val count: Int = ctrl.countRecords(table)
            if (act == null) return@Runnable
            act.runOnUiThread(Runnable {
                if (!isAdded() || activity == null || tvImRecords == null) return@Runnable
                tvImRecords.setText(count.toString())
            })
        }).start()
    }

    private fun loadCurrentKeyboard() {
        val ctrl: ManageImController? = manageImController
        val act: Activity? = activity
        val table = tableCode
        if (ctrl == null || table == null) return

        Thread(Runnable {
            val kb: Keyboard? = ctrl.getCurrentKeyboard(table)
            if (act == null) return@Runnable
            act.runOnUiThread(Runnable {
                if (!isAdded() || activity == null || tvKeyboardValue == null) return@Runnable
                if (kb != null && kb.desc != null && !kb.desc!!.isEmpty()) {
                    tvKeyboardValue.setText(kb.desc)
                } else {
                    tvKeyboardValue.setText(R.string.im_detail_keyboard_default)
                }
            })
        }).start()
    }

    private fun showKeyboardPicker() {
        val ctrl: ManageImController? = manageImController
        val act: Activity? = activity
        if (ctrl == null || act == null || tableCode == null) return

        Thread(Runnable {
            val keyboards: MutableList<Keyboard> = ctrl.keyboardList
            val current: Keyboard? = ctrl.getCurrentKeyboard(tableCode)
            if (act == null) return@Runnable
            act.runOnUiThread(Runnable {
                if (!isAdded() || activity == null) return@Runnable
                if (keyboards == null || keyboards.isEmpty()) return@Runnable

                val names = arrayOfNulls<String>(keyboards.size)
                var checkedIndex = -1
                for (i in keyboards.indices) {
                    val keyboard = keyboards.get(i)
                    names[i] = keyboard.desc
                    if (current != null && keyboard.code == current.code) {
                        checkedIndex = i
                    }
                }

                val tbl = tableCode
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.im_detail_keyboard_picker_title)
                    .setSingleChoiceItems(
                        names,
                        checkedIndex,
                        DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                            val selected = keyboards.get(which)
                            tvKeyboardValue.setText(selected.desc)
                            Thread(Runnable { ctrl.setIMKeyboard(tbl, selected) }).start()
                            dialog?.dismiss()
                        })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show()
            })
        }).start()
    }

    private fun showRemoveConfirmDialog() {
        if (activity == null) return
        AlertDialog.Builder(activity)
            .setMessage(R.string.im_detail_remove_confirm)
            .setPositiveButton(
                R.string.dialog_confirm,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    if (tableCode != null) {
                        var backupLearning = false
                        val root = getView()
                        if (root != null) {
                            val sw: SwitchMaterial? =
                                root.findViewById<SwitchMaterial?>(R.id.switch_backup_on_delete)
                            backupLearning = sw != null && sw.isChecked()
                        }
                val ss: SearchServer = manageImController.getSearchServer()
                        val ctx = requireContext().getApplicationContext()
                        val ctrl: ManageImController = manageImController
                        val tbl = tableCode ?: return@OnClickListener
                        Log.i("ImDetailFragment", "Remove confirm: tbl=$tbl")
                        val parent = getParentFragment()
                        val parentActivity = parent?.activity
                        // Run DB ops on background, THEN pop on main thread so IM List sees fresh data
                        Thread(Runnable {
                            ss.clearTable(tbl)
                            ss.resetImConfig(tbl)
                            val imList: MutableList<ImConfig?> =
                                ctrl.imConfigFullNameList.toMutableList()
                            Log.i(
                                "ImDetailFragment",
                                "After resetImConfig, list size=" + imList.size
                            )
                            LIMEPreferenceManager(ctx)
                                .syncIMActivatedState(imList)
                            parentActivity?.runOnUiThread(Runnable {
                                if (parent != null && parent.isAdded) {
                                    parent.childFragmentManager.popBackStack()
                                }
                            })
                        }).start()
                    } else {
                        Toast.makeText(
                            getContext(),
                            R.string.manage_im_error_no_controller,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity = null
    }

    companion object {
        private const val TAG = "ImDetailFragment"
        private const val ARG_IM_CODE = "im_code"
        private const val ARG_IM_DESC = "im_desc"

        @JvmStatic
        fun newInstance(im: ImConfig): ImDetailFragment {
            val f = ImDetailFragment()
            val args: Bundle = Bundle()
            args.putString(ARG_IM_CODE, im.code)
            args.putString(ARG_IM_DESC, im.desc)
            f.setArguments(args)
            return f
        }
    }
}

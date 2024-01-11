package com.fieldbook.tracker.activities

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.FieldDetailAdapter
import com.fieldbook.tracker.adapters.FieldDetailItem
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.interfaces.FieldAdapterController
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.ExportUtil
import dagger.hilt.android.AndroidEntryPoint
import pub.devrel.easypermissions.EasyPermissions
import javax.inject.Inject


@AndroidEntryPoint
class FieldDetailFragment( private val field: FieldObject ) : Fragment() {

    @Inject
    lateinit var database: DataHelper
    private var toolbar: Toolbar? = null
    private val PERMISSIONS_REQUEST_TRAIT_DATA = 9950

    private lateinit var exportUtil: ExportUtil
    private lateinit var rootView: View
    private lateinit var fieldNameTextView: TextView
    private lateinit var importTextView: TextView
    private lateinit var entryTextView: TextView
    private lateinit var lastEditTextView: TextView
    private lateinit var lastExportTextView: TextView
    private lateinit var traitCountTextView: TextView
    private lateinit var observationCountTextView: TextView
    private lateinit var syncLinearLayout: LinearLayout
    private lateinit var lastSyncTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        Log.d("onCreateView", "Start")
        rootView = inflater.inflate(R.layout.fragment_field_detail, container, false)
        toolbar = rootView.findViewById(R.id.toolbar)
        setupToolbar()

        fieldNameTextView = rootView.findViewById(R.id.fieldName)
        fieldNameTextView.text = field.getExp_name()
        importTextView = rootView.findViewById(R.id.importTextView)
        entryTextView = rootView.findViewById(R.id.entryTextView)
        lastEditTextView = rootView.findViewById(R.id.lastEditTextView)
        lastExportTextView = rootView.findViewById(R.id.lastExportTextView)
        traitCountTextView = rootView.findViewById(R.id.traitCountTextView)
        observationCountTextView = rootView.findViewById(R.id.observationCountTextView)

        syncLinearLayout = rootView.findViewById(R.id.syncLinearLayout)
        lastSyncTextView = rootView.findViewById(R.id.lastSyncTextView)

        syncLinearLayout.visibility = View.GONE
        var source: String? = field.getExp_source()
        var observationLevel = "entries"
        if (source != null && source != "csv" && source != "excel") { // BrAPI source
            syncLinearLayout.visibility = View.VISIBLE
            observationLevel = "${field.observation_level}s"
        } else if (source == null) { // Sample file import
            source = "sample file"
        }
        importTextView.text = "Imported ${field.getDate_import().split(" ")[0]} from " + source
        entryTextView.text = "${field.getCount()} ${observationLevel} with "+"8"+" attributes"

        val lastEdit = field.getDate_edit()
        if (!lastEdit.isNullOrEmpty()) {
            lastEditTextView.text = lastEdit.split(" ")[0] // append operator name to last edit if available
        }
        val lastExport = field.getDate_export()
        if (!lastExport.isNullOrEmpty()) {
            lastExportTextView.text = lastExport.split(" ")[0]
        }
        val lastSync = ""
        if (!lastSync.isNullOrEmpty()) {
            // TODO: add last sync date to FieldObject and retrieve it
        }

        setupRecyclerView()

        Log.d("onCreateView", "End")
        return rootView
    }

    private fun setupRecyclerView() {
        val recyclerView: RecyclerView = rootView.findViewById(R.id.fieldDetailRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        val items = createTraitDetailItems() // Create items for RecyclerView
        recyclerView.adapter = FieldDetailAdapter(items)
    }

    private fun createTraitDetailItems(): List<FieldDetailItem> {
        val dataHelper = DataHelper(requireContext())
        val traitCounts = dataHelper.getTraitCountsForStudy()

        // Check if there are any traits to display
        if (traitCounts.isNullOrEmpty()) {
            return emptyList()
        }

        return traitCounts.mapNotNull { (traitName, count) ->
            if (traitName != null) {
                FieldDetailItem(
                    traitName,
                    "$count observations",
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_trait_categorical)
                )
            } else {
                null
            }
        }
    }


    override fun onResume() {
        super.onResume()
//        updateFieldData()
//        displayTraitCounts(rootView)
    }

//    private fun updateFieldData() {
//        importDateTextView.text = " ${field.getDate_import().split(" ")[0]}"
//        editDateTextView.text = " ${field.getDate_edit().split(" ")[0]}"
//        exportDateTextView.text = " ${field.getDate_export().split(" ")[0]}"
//        countTextView.text = " ${field.getCount()}"
//    }

    private fun setupToolbar() {

        toolbar?.inflateMenu(R.menu.menu_field_details)

//        toolbar?.setTitle(field.getExp_name())
        toolbar?.setTitle("Field Detail")

        toolbar?.setNavigationIcon(R.drawable.arrow_left)

        toolbar?.setNavigationOnClickListener {

            parentFragmentManager.popBackStack()
        }

        toolbar?.setOnMenuItemClickListener { item ->

            when (item.itemId) {
                android.R.id.home -> {
                    parentFragmentManager.popBackStack()
                }
                R.id.rename -> {
                }
//                R.id.sort -> {
//                    (activity as? FieldSortController)?.showSortDialog(field)
//                }
                R.id.delete -> {
                    createDeleteItemAlertDialog(field)?.show()
                }
            }

            true
        }
    }

    private fun makeConfirmDeleteListener(field: FieldObject): DialogInterface.OnClickListener? {
        return DialogInterface.OnClickListener { dialog, which ->

            // Do it when clicking Yes or No
            dialog.dismiss()

            val ep = activity?.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, AppCompatActivity.MODE_PRIVATE)
            (activity as? FieldAdapterController)?.getDatabase()?.deleteField(field.getExp_id())
            if (field.getExp_id() == ep!!.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)) {
                val ed = ep.edit()
                ed.putString(GeneralKeys.FIELD_FILE, null)
                ed.putString(GeneralKeys.FIELD_OBS_LEVEL, null)
                ed.putInt(GeneralKeys.SELECTED_FIELD_ID, -1)
                ed.putString(GeneralKeys.UNIQUE_NAME, null)
                ed.putString(GeneralKeys.PRIMARY_NAME, null)
                ed.putString(GeneralKeys.SECONDARY_NAME, null)
                ed.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false)
                ed.putString(GeneralKeys.LAST_PLOT, null)
                ed.apply()
            }
            (activity as? FieldAdapterController)?.queryAndLoadFields()
            CollectActivity.reloadData = true
            parentFragmentManager.popBackStack()
        }
    }

    private fun createDeleteItemAlertDialog(field: FieldObject): AlertDialog? {
        val builder =
            AlertDialog.Builder(
                requireContext(), R.style.AppAlertDialog
            )
        builder.setTitle(requireContext().getString(R.string.fields_delete_study))
        builder.setMessage(requireContext().getString(R.string.fields_delete_study_confirmation))
        builder.setPositiveButton(
            requireContext().getString(R.string.dialog_yes),
            makeConfirmDeleteListener(field)
        )
        builder.setNegativeButton(
            requireContext().getString(R.string.dialog_no)
        ) { dialog, which -> dialog.dismiss() }
        return builder.create()
    }

    fun checkTraitsExist(): Int {
        val traits = database.getVisibleTrait()

        return when {
            traits.isEmpty() -> {
                Toast.makeText(context, R.string.warning_traits_missing, Toast.LENGTH_SHORT).show()
                -1
            }
            else -> 1
        }
    }

    fun collectDataFilePermission() {
        var perms = arrayOf<String?>(
            Manifest.permission.VIBRATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            perms = arrayOf(
                Manifest.permission.VIBRATE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
            )
        }
        if (EasyPermissions.hasPermissions(requireActivity(), *perms)) {
            val intent = Intent()
            intent.setClassName(
                requireActivity(),
                "com.fieldbook.tracker.activities.CollectActivity"
            )
            startActivity(intent)
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
                this, getString(R.string.permission_rationale_trait_features),
                PERMISSIONS_REQUEST_TRAIT_DATA, *perms
            )
        }
    }

//    private fun displayTraitCounts(view: View) {
//
//        val layout: LinearLayout = view.findViewById(R.id.traitCountsLayout) ?: return
//        layout.removeAllViews()
//
//        val dataHelper = DataHelper(requireContext())
//        val traitCounts = dataHelper.getTraitCountsForStudy()
//        Log.d("TraitCounts", "TraitCounts: $traitCounts")
//
//        // Check if there are any traits to display
//        if (traitCounts.isNullOrEmpty()) {
//            return
//        }
//
//        val inflater = LayoutInflater.from(context)
//        traitCounts.forEach { (traitName, count) ->
//            if(traitName != null) {
//                Log.d("TraitLoop", "TraitName: $traitName, Count: $count")
//                val view = inflater.inflate(R.layout.list_item_field_trait, layout, false) as View
//                val nameTextView: TextView = view.findViewById(R.id.traitNameTextView)
//                val countTextView: TextView = view.findViewById(R.id.traitCountTextView)
//                nameTextView.text = traitName
//                countTextView.text = count.toString() + " observations"
//                view.id = View.generateViewId()
//                layout.addView(view)
//            } else {
//                Log.d("TraitCountDetail", "TraitName is null.")
//            }
//        }
//    }
}

package com.fieldbook.tracker.activities.brapi.io

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.activities.brapi.io.mapper.toTraitObject
import com.fieldbook.tracker.adapters.StudyAdapter
import com.fieldbook.tracker.adapters.StudyAdapter.Model
import com.fieldbook.tracker.brapi.model.BrapiObservationLevel
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.client.v2.model.queryParams.germplasm.GermplasmQueryParams
import org.brapi.client.v2.model.queryParams.phenotype.ObservationUnitQueryParams
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.germ.BrAPIGermplasm
import org.brapi.v2.model.pheno.BrAPIObservationUnit
import org.brapi.v2.model.pheno.BrAPIObservationVariable
import org.brapi.v2.model.pheno.BrAPIPositionCoordinateTypeEnum
import java.util.Locale
import kotlin.collections.set

/**
 * receive study information including trial
 * from trial get program
 * get obs. levels
 * get observation units, and traits, germplasm
 */
class BrapiStudyImportActivity : ThemedActivity(), CoroutineScope by MainScope() {

    companion object {

        const val EXTRA_PROGRAM_DB_ID = "programDbId"
        const val EXTRA_STUDY_DB_IDS = "studyDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiStudyImportActivity::class.java)
        }
    }

    /**
     * Lazy-instantiate the brapi service, but cancel the activity if its v1
     */
    private val brapiService by lazy {
        BrAPIServiceFactory.getBrAPIService(this).also { service ->
            if (service is BrAPIServiceV1) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@BrapiStudyImportActivity,
                        getString(R.string.brapi_v1_is_not_compatible),
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
    }

    private lateinit var loadingTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tabLayout: TabLayout
    private lateinit var listView: ListView
    private lateinit var studyList: RecyclerView
    private lateinit var importButton: MaterialButton

    private val studies = arrayListOf<BrAPIStudy>()
    private val observationLevels = hashSetOf<String>()
    private val observationVariables =
        hashMapOf<String, HashSet<BrAPIObservationVariable>>().withDefault { hashSetOf() }
    private val observationUnits =
        hashMapOf<String, HashSet<BrAPIObservationUnit>>().withDefault { hashSetOf() }
    private val germplasms =
        hashMapOf<String, HashSet<BrAPIGermplasm>>().withDefault { hashSetOf() }

    private var selectedLevel: Int = -1
    private var selectedSort: Int = -1
    private var selectedPrimary: Int = -1
    private var selectedSecondary: Int = -1

    private var attributesTable: HashMap<String, Map<String, Map<String, String>>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_study_importer)

        loadingTextView = findViewById(R.id.act_brapi_importer_tv)
        progressBar = findViewById(R.id.act_list_filter_pb)
        tabLayout = findViewById(R.id.brapi_importer_tl)
        listView = findViewById(R.id.act_study_importer_lv)
        studyList = findViewById(R.id.act_list_filter_rv)
        importButton = findViewById(R.id.act_study_importer_import_button)

        setSupportActionBar(findViewById(R.id.act_list_filter_tb))

        supportActionBar?.setTitle(R.string.act_brapi_study_import_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        parseIntentExtras()

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(BrapiStudyFilterActivity.getIntent(this))
    }

    private fun parseIntentExtras() {
        val programDbId = intent.getStringExtra(EXTRA_PROGRAM_DB_ID)
        if (programDbId == null) {
            Toast.makeText(this, getString(R.string.no_programdbid_provided), Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        } else {
            val studyDbIds =
                intent.getStringArrayExtra(EXTRA_STUDY_DB_IDS)?.toList()
                    ?: listOf()

            if (studyDbIds.isEmpty()) {
                // fetch study info
                Toast.makeText(this, getString(R.string.no_studydbids_provided), Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
            } else {

                studies.addAll(BrapiFilterCache.getStoredModels(this).map { it.study }
                    .filter { it.studyDbId in studyDbIds })

                fetchStudyInfo(programDbId, studyDbIds)
            }
        }
    }

    private suspend fun setLoadingText(text: String) {
        withContext(Dispatchers.Main) {
            loadingTextView.text = text
        }
    }

    private fun fetchStudyInfo(programDbId: String, studyDbIds: List<String>) {

        launch {

            setLoadingText(getString(R.string.act_brapi_study_import_fetch_levels))

            fetchObservationLevels(programDbId).join()
            withContext(Dispatchers.Main) {
                importButton.isEnabled = false
                loadingTextView.visibility = View.GONE
                progressBar.visibility = View.INVISIBLE
                tabLayout.visibility = View.GONE

                loadStudyList(studyDbIds)
                setupImportButton(studyDbIds)
            }
        }
    }

    enum class Tab {
        LEVELS, PRIMARY_ORDER, SECONDARY_ORDER, SORT
    }

    private fun loadTabLayout(studyDbIds: List<String>) {

        tabLayout.visibility = View.VISIBLE

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    Tab.LEVELS.ordinal -> setLevelListOptions()
                    Tab.SORT.ordinal -> setSortListOptions()
                    Tab.PRIMARY_ORDER.ordinal -> setPrimaryOrderListOptions()
                    Tab.SECONDARY_ORDER.ordinal -> setSecondaryOrderListOptions()
                }

                listView.visibility = View.VISIBLE

            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {

            }

            override fun onTabReselected(tab: TabLayout.Tab?) {

            }
        })

        listView.visibility = View.VISIBLE

        attributesTable = hashMapOf()
        attributesTable?.let { table ->
            for (study in studyDbIds) {
                table[study] = getAttributes(study)
            }
        }

        setDefaultAttributeIdentifiers()

        setLevelListOptions()
    }

    private fun existingLevels() = observationLevels.toList().intersect(observationUnits.flatMap { it.value }
        .map { it.observationUnitPosition.observationLevel.levelName }
        .toSet()).toTypedArray()

    private fun setLevelListOptions() {

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            existingLevels()
        )

        listView.setItemChecked(selectedLevel, true)

        listView.smoothScrollToPosition(selectedLevel)

        listView.setOnItemClickListener { _, _, position, _ ->

            selectedLevel = if (selectedLevel == position) {

                listView.setItemChecked(selectedLevel, false)

                -1

            } else position

            studyList.adapter?.notifyDataSetChanged()

        }
    }

    private fun getAttributeKeys() = attributesTable?.values?.flatMap { it.values }?.flatMap { it.keys }?.distinct() ?: listOf()

    private fun setSortListOptions() {

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            getAttributeKeys()
        )

        listView.setItemChecked(selectedSort, true)

        listView.smoothScrollToPosition(selectedSort)

        listView.setOnItemClickListener { _, _, position, _ ->

            selectedSort = if (selectedSort == position) {

                listView.setItemChecked(selectedSort, false)

                -1

            } else position
        }
    }

    private fun setPrimaryOrderListOptions() {

        val attributes = getAttributeKeys()

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            attributes
        )

        listView.setItemChecked(selectedPrimary, true)

        listView.smoothScrollToPosition(selectedPrimary)

        listView.setOnItemClickListener { _, _, position, _ ->

            selectedPrimary = if (selectedPrimary == position) {

                listView.setItemChecked(selectedPrimary, false)

                -1

            } else position
        }
    }

    private fun setSecondaryOrderListOptions() {

        val attributes = getAttributeKeys()

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            attributes
        )

        listView.setItemChecked(selectedSecondary, true)

        listView.smoothScrollToPosition(selectedSecondary)

        listView.setOnItemClickListener { _, _, position, _ ->

            selectedSecondary = if (selectedSecondary == position) {

                listView.setItemChecked(selectedSecondary, false)

                -1

            } else position
        }
    }

    private fun setDefaultAttributeIdentifiers() {

        val attributes = getAttributeKeys()

        val levels = existingLevels()

        if (selectedLevel == -1) {
            selectedLevel = if (levels.contains("plot")) {
                levels.indexOf("plot")
            } else {
                0
            }
        }

        if (selectedPrimary == -1) {
            selectedPrimary = if (attributes.contains("Row")) {
                attributes.indexOf("Row")
            } else if (attributes.contains("Block")) {
                attributes.indexOf("Block")
            } else {
                0
            }
        }

        if (selectedSecondary == -1) {
            selectedSecondary = if (attributes.contains("Column")) {
                attributes.indexOf("Column")
            } else if (attributes.contains("Rep")) {
                attributes.indexOf("Rep")
            } else {
                0
            }
        }
    }

    private fun getAttributes(studyDbId: String): Map<String, Map<String, String>> {

        val unitAttributes = hashMapOf<String, Map<String, String>>()
        val germs = germplasms[studyDbId] ?: listOf()

        observationUnits[studyDbId]?.forEach { unit ->

            val attributes = hashMapOf<String, String>()

            if (unit.germplasmName != null) {
                attributes["Germplasm"] = unit.germplasmName
            }

            val position = unit.observationUnitPosition
            if (position != null) {
                position.observationLevelRelationships?.forEach { level ->
                    if (level.levelName != null) {
                        attributes[level.levelName.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(
                                Locale.getDefault()
                            ) else it.toString()
                        }] = level.levelCode
                    }
                }

                position.positionCoordinateX?.let { x ->
                    attributes[getRowColStr(position.positionCoordinateXType) ?: "Row"] = x
                }

                position.positionCoordinateY?.let { y ->
                    attributes[getRowColStr(position.positionCoordinateYType) ?: "Column"] = y
                }

                if (position.entryType != null && position.entryType.brapiValue != null) {
                    attributes["EntryType"] = position.entryType.brapiValue
                }
            }

            if (germplasms.isNotEmpty() && unit.germplasmDbId != null) {

                germs.firstOrNull { it.germplasmDbId == unit.germplasmDbId }?.let { germ ->

                    germ.pedigree?.let { pedigree ->
                        attributes["Pedigree"] = pedigree
                    }

                    germ.synonyms?.let { synonyms ->
                        attributes["Synonyms"] =
                            synonyms.mapNotNull { it.synonym?.replace("\"", "\"\"") }
                                .joinToString("; ")
                    }
                }
            }

            if (unit.observationUnitDbId != null) {
                attributes["ObservationUnitDbId"] = unit.observationUnitDbId
            }

            if (unit.observationUnitName != null) {
                attributes["ObservationUnitName"] = unit.observationUnitName
            }

            unitAttributes[unit.observationUnitDbId] = attributes
        }

        return unitAttributes

    }

    private fun getRowColStr(type: BrAPIPositionCoordinateTypeEnum?): String? {
        if (null != type) {
            return when (type) {
                BrAPIPositionCoordinateTypeEnum.PLANTED_INDIVIDUAL,
                BrAPIPositionCoordinateTypeEnum.GRID_COL,
                BrAPIPositionCoordinateTypeEnum.MEASURED_COL,
                BrAPIPositionCoordinateTypeEnum.LATITUDE -> "Column"

                BrAPIPositionCoordinateTypeEnum.PLANTED_ROW,
                BrAPIPositionCoordinateTypeEnum.GRID_ROW,
                BrAPIPositionCoordinateTypeEnum.MEASURED_ROW,
                BrAPIPositionCoordinateTypeEnum.LONGITUDE -> "Row"
            }
        }
        return null
    }

    private fun loadStudyList(studyDbIds: List<String>) {

        studyList.layoutManager = LinearLayoutManager(this).also {
            it.orientation = LinearLayoutManager.VERTICAL
        }

        val cacheModels = BrapiFilterCache.getStoredModels(this@BrapiStudyImportActivity)

        val studyModels = studyDbIds.map { id ->

            val studyName =
                cacheModels.firstOrNull { it.study.studyDbId == id }?.study?.studyName ?: id

            Model(
                id = id,
                title = studyName,
            )
        }

        studyList.adapter = StudyAdapter(object : StudyAdapter.StudyLoader {
            override fun getObservationVariables(
                id: String,
                position: Int
            ): HashSet<BrAPIObservationVariable>? {
                return observationVariables[id]?.toHashSet()
            }

            override fun getObservationUnits(
                id: String,
                position: Int
            ): HashSet<BrAPIObservationUnit>? {
                return if (selectedLevel >= 0) observationUnits[id]?.toHashSet()
                    ?.filter { it.observationUnitPosition.observationLevel.levelName == existingLevels().elementAt(selectedLevel) }?.toHashSet()
                else observationUnits[id]?.toHashSet()
            }

            override fun getGermplasm(id: String, position: Int): HashSet<BrAPIGermplasm>? {
                return germplasms[id]?.toHashSet()
            }

            override fun getLocation(id: String): String {
               return cacheModels.firstOrNull { it.study.studyDbId == id }?.study?.locationName ?: ""
            }
        })

        (studyList.adapter as StudyAdapter).submitList(studyModels)

        launch {

            async {

                //fetch variables, units, and germs for all studies asynchronously
                studyModels.forEachIndexed { index, model ->

                    launch {
                        val job = fetchObservationVariables(model.id)
                        job.join()
                        withContext(Dispatchers.Main) {
                            studyList.adapter?.notifyItemChanged(index)
                        }
                    }
                }

            }.await()

            async {

                //fetch variables, units, and germs for all studies asynchronously
                studyModels.forEachIndexed { index, model ->

                    launch {
                        val job = fetchObservationUnits(model.id)
                        job.join()
                    }
                }

            }.await()

            async {

                //fetch variables, units, and germs for all studies asynchronously
                studyModels.forEachIndexed { index, model ->

                    launch {
                        val job = fetchGermplasm(model.id)
                        job.join()
                        withContext(Dispatchers.Main) {
                            studyList.adapter?.notifyItemChanged(index)
                            loadTabLayout(studyDbIds)
                        }
                    }
                }

            }.await()

            if ((studyList.adapter as StudyAdapter).currentList.any {
                    observationUnits[it.id]?.isEmpty() != false
                }) {

                Toast.makeText(this@BrapiStudyImportActivity,
                    getString(R.string.failed_to_fetch_observation_units), Toast.LENGTH_SHORT).show()

                onBackPressed()

            }

            importButton.isEnabled = true
        }
    }

    private fun setupImportButton(studyDbIds: List<String>) {

        importButton.visibility = View.VISIBLE
        importButton.setOnClickListener {

            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = true
            loadingTextView.text = getString(R.string.act_brapi_study_import_saving)
            loadingTextView.visibility = View.VISIBLE
            importButton.isEnabled = false

            launch(Dispatchers.IO) {

                studyDbIds.forEach { id ->

                    studies.firstOrNull { it.studyDbId == id }?.let {

                        saveStudy(it)

                    }
                }

                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    private fun saveStudy(study: BrAPIStudy) {

        val level = BrapiObservationLevel().also {
            it.observationLevelName = existingLevels().elementAt(selectedLevel)
        }

        attributesTable?.get(study.studyDbId)?.let { studyAttributes ->

            observationUnits[study.studyDbId]?.filter { it.observationUnitPosition.observationLevel.levelName == level.observationLevelName }
                ?.let { units ->

                    val details = BrapiStudyDetails()
                    details.studyDbId = study.studyDbId
                    details.studyName = study.studyName
                    details.commonCropName = study.commonCropName
                    details.numberOfPlots = units.size

                    details.traits = observationVariables[study.studyDbId]?.toList()
                        ?.map { it.toTraitObject(this@BrapiStudyImportActivity) } ?: listOf()

                    val attributes = studyAttributes.values.flatMap { it.keys }.distinct()
                    val primaryId = attributes[selectedPrimary]
                    val secondaryId = attributes[selectedSecondary]
                    val sortOrder = if (selectedSort == -1) "" else attributes[selectedSort]

                    details.attributes = attributes

                    val unitAttributes = ArrayList<List<String>>()
                    units.forEach { unit ->

                        val row = ArrayList<String>()

                        attributes.forEach { attr ->
                            row.add(studyAttributes[unit.observationUnitDbId]?.get(attr) ?: "")
                        }

                        unitAttributes.add(row)

                    }

                    details.values = mutableListOf()
                    details.values.addAll(unitAttributes)

                    brapiService.saveStudyDetails(
                        details,
                        level,
                        primaryId,
                        secondaryId,
                        sortOrder
                    )
                }
        }

    }

    private suspend fun fetchObservationLevels(programDbId: String) = coroutineScope {

        Log.d(TAG, "Fetching levels for $programDbId")

        launch(Dispatchers.IO) {

            brapiService.getObservationLevels(null, { levels ->

                Log.d(TAG, "${levels.size} levels fetched")

                //log the levels
                levels.forEach { level ->
                    observationLevels.add(level.observationLevelName)
                    Log.d(TAG, "Level: ${level.observationLevelName}")
                }

            }) { _ ->
                //Toast.makeText(this, "Failed to fetch observation levels", Toast.LENGTH_SHORT).show()
                setResult(RESULT_CANCELED)
                finish()
            }

            while (observationLevels.isEmpty()) {
                ensureActive()
            }

            cancel()
        }
    }

    private suspend fun setProgress(progress: Int, progressMax: Int) {
        withContext(Dispatchers.Main) {
            progressBar.isIndeterminate = false
            progressBar.progress = progress
            progressBar.max = progressMax
        }
    }

    private suspend fun fetchGermplasm(studyDbId: String) = coroutineScope {

        val germs = arrayListOf<BrAPIGermplasm>()

        Log.d(TAG, "Fetching germplasm for $studyDbId")

        launch(Dispatchers.IO) {

            (brapiService as BrAPIServiceV2).germplasmService.fetchAll(GermplasmQueryParams()
                .also {
                    it.studyDbId(studyDbId)
                })
                .catch {
                    Log.e(TAG, "Failed to fetch germplasm")
                    cancel()
                }
                .collect { result ->

                val data = result as Pair<*, *>
                val total = data.first as Int
                val models = data.second as List<*>
                models.forEach { unit ->

                    (unit as? BrAPIGermplasm)?.let { g ->
                        Log.d("Unit", g.germplasmName)
                        germs.add(g)
                        if (total == germs.size) {
                            germplasms.getOrPut(studyDbId) { hashSetOf() }
                                .addAll(germs)
                            cancel()
                        }
                    }
                }

                if (models.isEmpty() && total == 0) {
                    cancel()
                }
            }
        }
    }


    private suspend fun fetchObservationVariables(studyDbId: String) =
        coroutineScope {

            val models = arrayListOf<BrAPIObservationVariable>()

            launch(Dispatchers.IO) {
                (brapiService as BrAPIServiceV2).observationVariableService.fetchAll(
                    VariableQueryParams().also {
                        it.studyDbId(studyDbId)
                    })
                    .catch {
                        Log.e(TAG, "Failed to fetch observation variables")
                        cancel()
                    }
                    .collect { variables ->

                    val data = variables as Pair<*, *>
                    val total = data.first as Int
                    val variableModels = data.second as List<*>
                    variableModels.forEach { model ->

                        (model as? BrAPIObservationVariable)?.let { variable ->

                            Log.d(TAG, "Variable: ${variable.observationVariableName}")

                            models.add(variable)
                            Log.d(TAG, "Variable: ${models.size}/$total")

                            if (total == models.size) {
                                observationVariables.getOrPut(studyDbId) { hashSetOf() }
                                    .addAll(models)

                                cancel()
                            }
                        }
                    }

                    if (models.isEmpty() && total == 0) {
                        cancel()
                    }
                }
            }
        }

    private suspend fun fetchObservationUnits(studyDbId: String) = coroutineScope {

        val units = arrayListOf<BrAPIObservationUnit>()

        launch(Dispatchers.IO) {

            (brapiService as BrAPIServiceV2).observationUnitService.fetchAll(
                ObservationUnitQueryParams().also {
                    it.studyDbId(studyDbId)
                    //it.observationUnitLevelName("plot")
                }
            )
                .catch {
                    Log.e(TAG, "Failed to fetch observation units")
                    cancel()
                }
                .collect { response ->
                    Log.d("Unit", "Response")

                    val data = response as Pair<*, *>
                    val total = data.first as Int
                    val models = data.second as List<*>
                    models.forEach { unit ->

                        (unit as? BrAPIObservationUnit)?.let { u ->
                            Log.d("Unit", u.observationUnitName)
                            units.add(u)
                            if (total == units.size) {
                                observationUnits.getOrPut(studyDbId) { hashSetOf() }
                                    .addAll(units)
                                cancel()
                            }
                        }
                    }

                    if (models.isEmpty() && total == 0) {

                        cancel()
                    }
                }
        }
    }
}
package com.vaibhav.attendance

import android.app.Application
import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val STORAGE_KEY = "student-attendance-tracker-native-v2"
private const val HELP_DISMISSED_KEY = "attendance_help_dismissed_v1"
private val SEMESTERS = List(8) { index -> "Semester ${index + 1}" }
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val WEEKDAYS = listOf(
    WeekdayInfo(DayOfWeek.MONDAY, "Monday"),
    WeekdayInfo(DayOfWeek.TUESDAY, "Tuesday"),
    WeekdayInfo(DayOfWeek.WEDNESDAY, "Wednesday"),
    WeekdayInfo(DayOfWeek.THURSDAY, "Thursday"),
    WeekdayInfo(DayOfWeek.FRIDAY, "Friday"),
    WeekdayInfo(DayOfWeek.SATURDAY, "Saturday"),
    WeekdayInfo(DayOfWeek.SUNDAY, "Sunday"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AttendanceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                ) {
                    AttendanceRoute()
                }
            }
        }
    }
}

@Composable
private fun AttendanceRoute(viewModel: AttendanceViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showHelp by viewModel.showHelp.collectAsStateWithLifecycle()

    AttendanceScreen(
        uiState = uiState,
        showHelp = showHelp,
        onSemesterSelected = viewModel::selectSemester,
        onDateSelected = viewModel::selectDate,
        onAddSubject = viewModel::addSubject,
        onDeleteSubject = viewModel::deleteSubject,
        onToggleAttendance = viewModel::updateAttendance,
        onMarkAllPresent = viewModel::markAllPresent,
        onClearDate = viewModel::clearSelectedDate,
        onSetSuspendedSubjects = viewModel::setSuspendedSubjectsForSelectedDate,
        onSetExtraClasses = viewModel::setExtraClassesForSelectedDate,
        onSetTimetableForDay = viewModel::setTimetableForDay,
        onSetSubjectStats = viewModel::setSubjectStats,
        onSetRequiredAttendancePercentage = viewModel::setRequiredAttendancePercentage,
        onEraseAllData = viewModel::eraseAllData,
        onRestoreDeletedSubject = viewModel::restoreDeletedSubject,
        onShowHelp = viewModel::openHelp,
        onDismissHelp = viewModel::dismissHelp,
    )
}

data class WeekdayInfo(
    val dayOfWeek: DayOfWeek,
    val label: String,
) {
    val key: String = dayOfWeek.name
}

enum class AppPage {
    Attendance,
    Timetable,
    Stats,
    Settings,
}

data class AttendanceUiState(
    val selectedSemester: String = SEMESTERS.first(),
    val selectedDate: String = LocalDate.now().format(DATE_FORMATTER),
    val requiredAttendancePercentage: Int = 75,
    val semesters: Map<String, SemesterData> = SEMESTERS.associateWith { SemesterData() },
) {
    private val semesterData: SemesterData
        get() = semesters.getValue(selectedSemester)

    val subjects: List<Subject>
        get() = semesterData.subjects

    val selectedDateRecord: DateRecord
        get() = semesterData.dateRecords[selectedDate] ?: DateRecord()

    val selectedWeekday: WeekdayInfo
        get() = weekdayForDate(selectedDate)

    val selectedDateSubjectIds: Set<String>
        get() = resolveSubjectIdsForDate(selectedDate)

    val selectedDateSubjects: List<Subject>
        get() = subjects.filter { it.id in selectedDateSubjectIds }

    val selectedDateAttendanceEntries: List<AttendanceEntry>
        get() = attendanceEntriesForDate(selectedDate)

    val scheduledDateSubjects: List<Subject>
        get() = subjects.filter { it.id in resolveScheduledSubjectIdsForDate(selectedDate) }

    val presentTodayCount: Int
        get() = selectedDateAttendanceEntries.count { selectedDateRecord.attendance[it.key] == true }

    val totalTodayCount: Int
        get() = selectedDateAttendanceEntries.size

    val trackedTodayText: String
        get() = "Classes Today: ${presentTodayCount}/${totalTodayCount}"

    fun calculateStats(subjectId: String): SubjectStats {
        val derivedStats = calculateDerivedStats(subjectId)
        val adjustment = semesterData.statAdjustments[subjectId] ?: SubjectAdjustment()
        val totalClasses = (derivedStats.totalClasses + adjustment.totalOffset).coerceAtLeast(0)
        val totalPresent = (derivedStats.totalPresent + adjustment.presentOffset)
            .coerceIn(0, totalClasses)
        val percentage = if (totalClasses == 0) 0 else ((totalPresent * 100f) / totalClasses).toInt()
        return SubjectStats(totalPresent, totalClasses, percentage)
    }

    fun calculateDerivedStats(subjectId: String): SubjectStats {
        var totalClasses = 0
        var totalPresent = 0

        semesterData.dateRecords.forEach { (date, record) ->
            val classCount = totalClassesForDate(date, subjectId)
            if (classCount == 0) {
                return@forEach
            }

            totalClasses += classCount
            totalPresent += attendanceEntriesForDate(date).count { it.subjectId == subjectId && record.attendance[it.key] == true }
        }

        val percentage = if (totalClasses == 0) 0 else ((totalPresent * 100f) / totalClasses).toInt()
        return SubjectStats(totalPresent, totalClasses, percentage)
    }

    fun timetableForDay(dayKey: String): Set<String> =
        semesterData.timetable[dayKey].orEmpty().toSet()

    fun extraClassesForSelectedDate(): Map<String, Int> =
        selectedDateRecord.extraClassCounts

    fun suspendedSubjectsForSelectedDate(): Set<String> =
        selectedDateRecord.suspendedSubjectIds

    fun daySummary(dayKey: String): String {
        val names = subjects.filter { it.id in timetableForDay(dayKey) }.map { it.name }
        return if (names.isEmpty()) "No scheduled classes" else names.joinToString(", ")
    }

    private fun resolveSubjectIdsForDate(date: String): Set<String> {
        val record = semesterData.dateRecords[date] ?: return emptySet()
        if (record.suspended) {
            return emptySet()
        }

        return resolveScheduledSubjectIdsForDate(date) - record.suspendedSubjectIds
    }

    private fun resolveScheduledSubjectIdsForDate(date: String): Set<String> {
        val record = semesterData.dateRecords[date] ?: return emptySet()
        val baseSubjects = semesterData.timetable[weekdayForDate(date).key].orEmpty()
        return (baseSubjects + record.extraClassCounts.filterValues { it > 0 }.keys).toSet()
    }

    private fun attendanceEntriesForDate(date: String): List<AttendanceEntry> {
        val record = semesterData.dateRecords[date] ?: return emptyList()
        if (record.suspended) return emptyList()

        val baseSubjects = semesterData.timetable[weekdayForDate(date).key].orEmpty()
        val entries = mutableListOf<AttendanceEntry>()

        subjects.forEach { subject ->
            if (subject.id in baseSubjects && subject.id !in record.suspendedSubjectIds) {
                entries += AttendanceEntry(
                    key = normalAttendanceKey(subject.id),
                    subjectId = subject.id,
                    label = subject.name,
                )
            }

            val extraCount = record.extraClassCounts[subject.id] ?: 0
            repeat(extraCount) { index ->
                if (subject.id !in record.suspendedSubjectIds) {
                    entries += AttendanceEntry(
                        key = extraAttendanceKey(subject.id, index + 1),
                        subjectId = subject.id,
                        label = "${subject.name} (Extra ${index + 1})",
                        isExtra = true,
                    )
                }
            }
        }

        return entries
    }

    private fun totalClassesForDate(date: String, subjectId: String): Int {
        val record = semesterData.dateRecords[date] ?: return 0
        if (record.suspended || subjectId in record.suspendedSubjectIds) {
            return 0
        }

        val baseCount = if (subjectId in semesterData.timetable[weekdayForDate(date).key].orEmpty()) 1 else 0
        return baseCount + (record.extraClassCounts[subjectId] ?: 0)
    }

}

data class SemesterData(
    val subjects: List<Subject> = emptyList(),
    val timetable: Map<String, List<String>> = emptyMap(),
    val dateRecords: Map<String, DateRecord> = emptyMap(),
    val statAdjustments: Map<String, SubjectAdjustment> = emptyMap(),
)

data class Subject(
    val id: String,
    val name: String,
)

data class DateRecord(
    val attendance: Map<String, Boolean> = emptyMap(),
    val suspended: Boolean = false,
    val extraClassCounts: Map<String, Int> = emptyMap(),
    val suspendedSubjectIds: Set<String> = emptySet(),
)

data class SubjectAdjustment(
    val presentOffset: Int = 0,
    val totalOffset: Int = 0,
)

data class SubjectStats(
    val totalPresent: Int,
    val totalClasses: Int,
    val percentage: Int,
)

private data class DeletedSubjectSnapshot(
    val semester: String,
    val subject: Subject,
    val timetableDays: List<String>,
    val attendanceByDate: Map<String, Map<String, Boolean>>,
    val extraClassCountsByDate: Map<String, Int>,
    val suspendedDates: Set<String>,
    val statAdjustment: SubjectAdjustment?,
)

data class AttendanceEntry(
    val key: String,
    val subjectId: String,
    val label: String,
    val isExtra: Boolean = false,
)

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AttendanceRepository(application)
    private val _uiState = MutableStateFlow(repository.loadState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()
    private val _showHelp = MutableStateFlow(repository.shouldShowHelp())
    val showHelp: StateFlow<Boolean> = _showHelp.asStateFlow()
    private var lastDeletedSubject: DeletedSubjectSnapshot? = null

    init {
        selectDate(_uiState.value.selectedDate)
    }

    fun selectSemester(semester: String) {
        updateState {
            val stateWithSemester = copy(selectedSemester = semester)
            stateWithSemester.ensureDateRecordForSelectedDate()
        }
    }

    fun selectDate(date: String) {
        updateState {
            copy(selectedDate = date).ensureDateRecordForSelectedDate()
        }
    }

    fun addSubject(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        updateState {
            val semesterData = semesters.getValue(selectedSemester)
            val updatedSubjects = semesterData.subjects + Subject(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
            )

            copy(
                semesters = semesters + (selectedSemester to semesterData.copy(subjects = updatedSubjects)),
            )
        }
    }

    fun deleteSubject(subjectId: String) {
        updateState {
            val semesterData = semesters.getValue(selectedSemester)
            val subject = semesterData.subjects.firstOrNull { it.id == subjectId } ?: return@updateState this
            lastDeletedSubject = DeletedSubjectSnapshot(
                semester = selectedSemester,
                subject = subject,
                timetableDays = semesterData.timetable.filterValues { subjectId in it }.keys.toList(),
                attendanceByDate = semesterData.dateRecords.mapNotNull { (date, record) ->
                    val attendanceForSubject = record.attendance.filterKeys { attendanceKeyBelongsToSubject(it, subjectId) }
                    attendanceForSubject.takeIf { it.isNotEmpty() }?.let { date to it }
                }.toMap(),
                extraClassCountsByDate = semesterData.dateRecords.mapNotNull { (date, record) ->
                    record.extraClassCounts[subjectId]?.takeIf { it > 0 }?.let { date to it }
                }.toMap(),
                suspendedDates = semesterData.dateRecords
                    .filterValues { subjectId in it.suspendedSubjectIds }
                    .keys,
                statAdjustment = semesterData.statAdjustments[subjectId],
            )
            val updatedTimetable = semesterData.timetable.mapValues { (_, ids) ->
                ids.filterNot { it == subjectId }
            }
            val updatedRecords = semesterData.dateRecords.mapValues { (_, record) ->
                record.copy(
                    attendance = record.attendance.filterKeys { !attendanceKeyBelongsToSubject(it, subjectId) },
                    extraClassCounts = record.extraClassCounts - subjectId,
                    suspendedSubjectIds = record.suspendedSubjectIds - subjectId,
                )
            }

            copy(
                semesters = semesters + (
                    selectedSemester to semesterData.copy(
                        subjects = semesterData.subjects.filterNot { it.id == subjectId },
                        timetable = updatedTimetable,
                        dateRecords = updatedRecords,
                        statAdjustments = semesterData.statAdjustments - subjectId,
                    )
                ),
            )
        }
    }

    fun restoreDeletedSubject() {
        val snapshot = lastDeletedSubject ?: return

        updateState {
            val semesterData = semesters.getValue(snapshot.semester)
            if (semesterData.subjects.any { it.id == snapshot.subject.id }) {
                return@updateState this
            }

            val restoredTimetable = semesterData.timetable.toMutableMap().apply {
                snapshot.timetableDays.forEach { dayKey ->
                    put(dayKey, get(dayKey).orEmpty() + snapshot.subject.id)
                }
            }
            val restoredRecords = semesterData.dateRecords.toMutableMap().apply {
                snapshot.attendanceByDate.forEach { (date, savedAttendance) ->
                    val record = this[date] ?: DateRecord()
                    this[date] = record.copy(
                        attendance = record.attendance + savedAttendance,
                    )
                }
                snapshot.extraClassCountsByDate.forEach { (date, classCount) ->
                    val record = this[date] ?: DateRecord()
                    this[date] = record.copy(
                        extraClassCounts = record.extraClassCounts + (snapshot.subject.id to classCount),
                    )
                }
                snapshot.suspendedDates.forEach { date ->
                    val record = this[date] ?: DateRecord()
                    this[date] = record.copy(
                        suspendedSubjectIds = record.suspendedSubjectIds + snapshot.subject.id,
                    )
                }
            }
            val restoredAdjustments = semesterData.statAdjustments.toMutableMap().apply {
                snapshot.statAdjustment?.let { put(snapshot.subject.id, it) }
            }

            lastDeletedSubject = null
            copy(
                semesters = semesters + (
                    snapshot.semester to semesterData.copy(
                        subjects = semesterData.subjects + snapshot.subject,
                        timetable = restoredTimetable,
                        dateRecords = restoredRecords,
                        statAdjustments = restoredAdjustments,
                    )
                ),
            )
        }
    }

    fun updateAttendance(attendanceKey: String, present: Boolean) {
        updateState {
            val semesterData = semesters.getValue(selectedSemester)
            val record = semesterData.dateRecords[selectedDate] ?: DateRecord()
            val updatedRecord = record.copy(
                attendance = record.attendance + (attendanceKey to present),
            )

            copy(
                semesters = semesters + (
                    selectedSemester to semesterData.copy(
                        dateRecords = semesterData.dateRecords + (selectedDate to updatedRecord),
                    )
                ),
            )
        }
    }

    fun markAllPresent() {
        updateState {
            val semesterData = semesters.getValue(selectedSemester)
            val record = semesterData.dateRecords[selectedDate] ?: DateRecord()
            val updatedAttendance = record.attendance.toMutableMap()
            attendanceEntriesForDate(semesterData, selectedDate, record).forEach { entry ->
                updatedAttendance[entry.key] = true
            }

            copy(
                semesters = semesters + (
                    selectedSemester to semesterData.copy(
                        dateRecords = semesterData.dateRecords + (
                            selectedDate to record.copy(attendance = updatedAttendance)
                        ),
                    )
                ),
            )
        }
    }

    fun clearSelectedDate() {
        updateState {
            val semesterData = semesters.getValue(selectedSemester)
            copy(
                semesters = semesters + (
                    selectedSemester to semesterData.copy(
                        dateRecords = semesterData.dateRecords + (selectedDate to DateRecord()),
                    )
                ),
            ).ensureDateRecordForSelectedDate()
        }
    }

    fun setSuspendedSubjectsForSelectedDate(subjectIds: Set<String>) {
        updateState {
            val semesterData = semesters.getValue(selectedSemester)
            val record = semesterData.dateRecords[selectedDate] ?: DateRecord()
            val validSubjectIds = semesterData.subjects.map { it.id }.toSet()
            val filteredSuspended = subjectIds.filterTo(linkedSetOf()) { it in validSubjectIds }
            val effectiveSubjectIds = effectiveSubjectIds(
                semesterData = semesterData,
                date = selectedDate,
                record = record.copy(suspended = false, suspendedSubjectIds = filteredSuspended),
            )
            val updatedRecord = record.copy(
                suspended = false,
                suspendedSubjectIds = filteredSuspended,
                attendance = record.attendance.filterKeys { key ->
                    val subjectId = subjectIdFromAttendanceKey(key)
                    subjectId != null && subjectId in effectiveSubjectIds
                },
            )

            copy(
                semesters = semesters + (
                    selectedSemester to semesterData.copy(
                        dateRecords = semesterData.dateRecords + (selectedDate to updatedRecord),
                    )
                ),
            )
        }
    }

    fun setExtraClassesForSelectedDate(extraClassCounts: Map<String, Int>) {
        updateState {
            val semesterData = semesters.getValue(selectedSemester)
            val record = semesterData.dateRecords[selectedDate] ?: DateRecord()
            val validSubjectIds = semesterData.subjects.map { it.id }.toSet()
            val filteredExtras = extraClassCounts
                .filterKeys { it in validSubjectIds }
                .mapValues { (_, count) -> count.coerceAtLeast(0) }
                .filterValues { it > 0 }
            val effectiveSubjectIds = effectiveSubjectIds(
                semesterData = semesterData,
                date = selectedDate,
                record = record.copy(extraClassCounts = filteredExtras),
            )

            val filteredAttendance = record.attendance
                .filterKeys { key ->
                    val subjectId = subjectIdFromAttendanceKey(key)
                    subjectId != null && subjectId in effectiveSubjectIds && attendanceKeyIsValidForRecord(
                        key = key,
                        semesterData = semesterData,
                        date = selectedDate,
                        record = record.copy(extraClassCounts = filteredExtras),
                    )
                }
            val updatedRecord = record.copy(
                extraClassCounts = filteredExtras,
                attendance = filteredAttendance,
            )

            copy(
                semesters = semesters + (
                    selectedSemester to semesterData.copy(
                        dateRecords = semesterData.dateRecords + (selectedDate to updatedRecord),
                    )
                ),
            )
        }
    }

    fun setTimetableForDay(dayKey: String, subjectIds: Set<String>) {
        updateState {
            val semesterData = semesters.getValue(selectedSemester)
            val validSubjectIds = semesterData.subjects.map { it.id }.toSet()
            val updatedIds = subjectIds.filterTo(linkedSetOf()) { it in validSubjectIds }.toList()

            copy(
                semesters = semesters + (
                    selectedSemester to semesterData.copy(
                        timetable = semesterData.timetable + (dayKey to updatedIds),
                    )
                ),
            )
        }
    }

    fun setSubjectStats(subjectId: String, totalPresent: Int, totalClasses: Int) {
        updateState {
            val semesterData = semesters.getValue(selectedSemester)
            val derivedStats = calculateDerivedStats(subjectId)
            val sanitizedTotalClasses = totalClasses.coerceAtLeast(0)
            val sanitizedTotalPresent = totalPresent.coerceIn(0, sanitizedTotalClasses)
            val updatedAdjustment = SubjectAdjustment(
                presentOffset = sanitizedTotalPresent - derivedStats.totalPresent,
                totalOffset = sanitizedTotalClasses - derivedStats.totalClasses,
            )

            copy(
                semesters = semesters + (
                    selectedSemester to semesterData.copy(
                        statAdjustments = semesterData.statAdjustments + (subjectId to updatedAdjustment),
                    )
                ),
            )
        }
    }

    fun setRequiredAttendancePercentage(percentage: Int) {
        updateState {
            copy(requiredAttendancePercentage = percentage.coerceIn(0, 100))
        }
    }

    fun eraseAllData() {
        repository.eraseAllData()
        _uiState.value = AttendanceUiState().ensureDateRecordForSelectedDate()
        _showHelp.value = true
    }

    fun openHelp() {
        _showHelp.value = true
    }

    fun dismissHelp() {
        repository.markHelpDismissed()
        _showHelp.value = false
    }

    private fun effectiveSubjectIds(
        semesterData: SemesterData,
        date: String,
        record: DateRecord,
    ): Set<String> {
        if (record.suspended) {
            return emptySet()
        }

        val base = semesterData.timetable[weekdayForDate(date).key].orEmpty()
        return (base + record.extraClassCounts.filterValues { it > 0 }.keys).toSet() - record.suspendedSubjectIds
    }

    private fun attendanceEntriesForDate(
        semesterData: SemesterData,
        date: String,
        record: DateRecord,
    ): List<AttendanceEntry> {
        if (record.suspended) return emptyList()
        val baseSubjects = semesterData.timetable[weekdayForDate(date).key].orEmpty()
        val entries = mutableListOf<AttendanceEntry>()
        semesterData.subjects.forEach { subject ->
            if (subject.id in baseSubjects && subject.id !in record.suspendedSubjectIds) {
                entries += AttendanceEntry(
                    key = normalAttendanceKey(subject.id),
                    subjectId = subject.id,
                    label = subject.name,
                )
            }
            repeat(record.extraClassCounts[subject.id] ?: 0) { index ->
                if (subject.id !in record.suspendedSubjectIds) {
                    entries += AttendanceEntry(
                        key = extraAttendanceKey(subject.id, index + 1),
                        subjectId = subject.id,
                        label = "${subject.name} (Extra ${index + 1})",
                        isExtra = true,
                    )
                }
            }
        }
        return entries
    }

    private fun attendanceKeyIsValidForRecord(
        key: String,
        semesterData: SemesterData,
        date: String,
        record: DateRecord,
    ): Boolean = attendanceEntriesForDate(semesterData, date, record).any { it.key == key }

    private fun AttendanceUiState.ensureDateRecordForSelectedDate(): AttendanceUiState {
        val semesterData = semesters.getValue(selectedSemester)
        if (semesterData.dateRecords.containsKey(selectedDate)) {
            return this
        }

        return copy(
            semesters = semesters + (
                selectedSemester to semesterData.copy(
                    dateRecords = semesterData.dateRecords + (selectedDate to DateRecord()),
                )
            ),
        )
    }

    private fun updateState(transform: AttendanceUiState.() -> AttendanceUiState) {
        val updated = _uiState.value.transform()
        _uiState.value = updated
        repository.saveState(updated)
    }
}

private class AttendanceRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("attendance_native_storage", Context.MODE_PRIVATE)

    fun loadState(): AttendanceUiState {
        val raw = preferences.getString(STORAGE_KEY, null)
        if (raw.isNullOrBlank()) {
            return loadLegacyState() ?: AttendanceUiState()
        }

        return runCatching {
            val root = JSONObject(raw)
            parseState(root)
        }.getOrElse {
            loadLegacyState() ?: AttendanceUiState()
        }
    }

    fun saveState(state: AttendanceUiState) {
        val semestersJson = JSONObject()
        state.semesters.forEach { (semester, data) ->
            semestersJson.put(semester, data.toJson())
        }

        val root = JSONObject()
            .put("selectedSemester", state.selectedSemester)
            .put("selectedDate", state.selectedDate)
            .put("requiredAttendancePercentage", state.requiredAttendancePercentage)
            .put("semesters", semestersJson)

        preferences.edit {
            putString(STORAGE_KEY, root.toString())
        }
    }

    fun eraseAllData() {
        preferences.edit {
            remove(STORAGE_KEY)
            remove("student-attendance-tracker-native-v1")
            remove(HELP_DISMISSED_KEY)
        }
    }

    fun shouldShowHelp(): Boolean =
        !preferences.getBoolean(HELP_DISMISSED_KEY, false)

    fun markHelpDismissed() {
        preferences.edit {
            putBoolean(HELP_DISMISSED_KEY, true)
        }
    }

    private fun loadLegacyState(): AttendanceUiState? {
        val raw = preferences.getString("student-attendance-tracker-native-v1", null) ?: return null

        return runCatching {
            val root = JSONObject(raw)
            val selectedSemester = root.optString("selectedSemester").takeIf { it in SEMESTERS } ?: SEMESTERS.first()
            val selectedDate = root.optString("selectedDate").takeIf { it.isNotBlank() }
                ?: LocalDate.now().format(DATE_FORMATTER)

            val semesters = SEMESTERS.associateWith { semester ->
                val semesterJson = root.optJSONObject("semesters")?.optJSONObject(semester)
                semesterJson.toLegacySemesterData()
            }

            AttendanceUiState(
                selectedSemester = selectedSemester,
                selectedDate = selectedDate,
                requiredAttendancePercentage = 75,
                semesters = semesters,
            )
        }.getOrNull()
    }

    private fun parseState(root: JSONObject): AttendanceUiState {
        val selectedSemester = root.optString("selectedSemester").takeIf { it in SEMESTERS } ?: SEMESTERS.first()
        val selectedDate = root.optString("selectedDate").takeIf { it.isNotBlank() }
            ?: LocalDate.now().format(DATE_FORMATTER)
        val semesterState = SEMESTERS.associateWith { semester ->
            root.optJSONObject("semesters")?.optJSONObject(semester).toSemesterData()
        }

        return AttendanceUiState(
            selectedSemester = selectedSemester,
            selectedDate = selectedDate,
            requiredAttendancePercentage = root.optInt("requiredAttendancePercentage", 75).coerceIn(0, 100),
            semesters = semesterState,
        )
    }

    private fun JSONObject?.toLegacySemesterData(): SemesterData {
        if (this == null) return SemesterData()

        val subjects = parseSubjects(optJSONArray("subjects"))
        val legacyAttendanceJson = optJSONObject("attendance") ?: JSONObject()
        val dateRecords = buildMap {
            legacyAttendanceJson.keys().forEach { date ->
                val dateEntries = legacyAttendanceJson.optJSONObject(date) ?: return@forEach
                val attendance = buildMap {
                    dateEntries.keys().forEach { subjectId ->
                        val entry = dateEntries.optJSONObject(subjectId) ?: return@forEach
                        when {
                            entry.has("present") -> put(normalAttendanceKey(subjectId), entry.optBoolean("present"))
                            entry.has("status") -> put(normalAttendanceKey(subjectId), entry.optString("status") == "present")
                        }
                    }
                }
                put(
                    date,
                    DateRecord(
                        attendance = attendance,
                        extraClassCounts = attendance.keys.associateWith { 1 },
                    ),
                )
            }
        }

        return SemesterData(
            subjects = subjects,
            timetable = emptyMap(),
            dateRecords = dateRecords,
        )
    }

    private fun JSONObject?.toSemesterData(): SemesterData {
        if (this == null) return SemesterData()

        val subjects = parseSubjects(optJSONArray("subjects"))
        val timetable = buildMap {
            val timetableJson = optJSONObject("timetable") ?: JSONObject()
            timetableJson.keys().forEach { dayKey ->
                val ids = buildList {
                    val array = timetableJson.optJSONArray(dayKey) ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val id = array.optString(index)
                        if (id.isNotBlank()) add(id)
                    }
                }
                put(dayKey, ids)
            }
        }
        val dateRecords = buildMap {
            val recordsJson = optJSONObject("dateRecords") ?: JSONObject()
            recordsJson.keys().forEach { date ->
                val recordJson = recordsJson.optJSONObject(date) ?: return@forEach
                put(date, parseDateRecord(recordJson))
            }
        }
        val statAdjustments = buildMap {
            val adjustmentsJson = optJSONObject("statAdjustments") ?: JSONObject()
            adjustmentsJson.keys().forEach { subjectId ->
                val adjustmentJson = adjustmentsJson.optJSONObject(subjectId) ?: return@forEach
                put(
                    subjectId,
                    SubjectAdjustment(
                        presentOffset = adjustmentJson.optInt("presentOffset", 0),
                        totalOffset = adjustmentJson.optInt("totalOffset", 0),
                    ),
                )
            }
        }

        return SemesterData(
            subjects = subjects,
            timetable = timetable,
            dateRecords = dateRecords,
            statAdjustments = statAdjustments,
        )
    }

    private fun parseSubjects(subjectsArray: JSONArray?): List<Subject> = buildList {
        val array = subjectsArray ?: JSONArray()
        for (index in 0 until array.length()) {
            val subjectJson = array.optJSONObject(index) ?: continue
            val id = subjectJson.optString("id")
            val name = subjectJson.optString("name")
            if (id.isNotBlank() && name.isNotBlank()) {
                add(Subject(id = id, name = name))
            }
        }
    }

    private fun parseDateRecord(recordJson: JSONObject): DateRecord {
        val attendance = buildMap {
            val attendanceJson = recordJson.optJSONObject("attendance") ?: JSONObject()
            attendanceJson.keys().forEach { subjectId ->
                when (val rawValue = attendanceJson.opt(subjectId)) {
                    is Boolean -> put(subjectId, rawValue)
                    is Number -> {
                        val normalizedSubjectId = subjectIdFromAttendanceKey(subjectId) ?: subjectId
                        val attendedCount = rawValue.toInt().coerceAtLeast(0)
                        if (attendedCount > 0) {
                            put(normalAttendanceKey(normalizedSubjectId), true)
                            repeat((attendedCount - 1).coerceAtLeast(0)) { index ->
                                put(extraAttendanceKey(normalizedSubjectId, index + 1), true)
                            }
                        }
                    }
                    else -> put(subjectId, attendanceJson.optBoolean(subjectId))
                }
            }
        }
        val extras = buildMap {
            val extraCountsJson = recordJson.optJSONObject("extraClassCounts")
            extraCountsJson?.keys()?.forEach { subjectId ->
                put(subjectId, extraCountsJson.optInt(subjectId, 0).coerceAtLeast(0))
            }
            val array = recordJson.optJSONArray("extraSubjectIds") ?: JSONArray()
            for (index in 0 until array.length()) {
                val id = array.optString(index)
                if (id.isNotBlank() && id !in this) put(id, 1)
            }
        }

        return DateRecord(
            attendance = attendance,
            suspended = recordJson.optBoolean("suspended", false),
            extraClassCounts = extras.filterValues { it > 0 },
            suspendedSubjectIds = buildSet {
                val array = recordJson.optJSONArray("suspendedSubjectIds") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val id = array.optString(index)
                    if (id.isNotBlank()) add(id)
                }
            },
        )
    }

    private fun SemesterData.toJson(): JSONObject {
        val subjectsJson = JSONArray()
        subjects.forEach { subject ->
            subjectsJson.put(
                JSONObject()
                    .put("id", subject.id)
                    .put("name", subject.name),
            )
        }

        val timetableJson = JSONObject()
        timetable.forEach { (dayKey, subjectIds) ->
            val array = JSONArray()
            subjectIds.forEach { array.put(it) }
            timetableJson.put(dayKey, array)
        }

        val recordsJson = JSONObject()
        dateRecords.forEach { (date, record) ->
            val attendanceJson = JSONObject()
            record.attendance.forEach { (attendanceKey, present) ->
                attendanceJson.put(attendanceKey, present)
            }

            val extraJson = JSONArray()
            val extraCountJson = JSONObject()
            record.extraClassCounts.forEach { (subjectId, count) ->
                extraJson.put(subjectId)
                extraCountJson.put(subjectId, count)
            }
            val suspendedJson = JSONArray()
            record.suspendedSubjectIds.forEach { suspendedJson.put(it) }

            recordsJson.put(
                date,
                JSONObject()
                    .put("attendance", attendanceJson)
                    .put("suspended", record.suspended)
                    .put("extraSubjectIds", extraJson)
                    .put("extraClassCounts", extraCountJson)
                    .put("suspendedSubjectIds", suspendedJson),
            )
        }

        val adjustmentsJson = JSONObject()
        statAdjustments.forEach { (subjectId, adjustment) ->
            adjustmentsJson.put(
                subjectId,
                JSONObject()
                    .put("presentOffset", adjustment.presentOffset)
                    .put("totalOffset", adjustment.totalOffset),
            )
        }

        return JSONObject()
            .put("subjects", subjectsJson)
            .put("timetable", timetableJson)
            .put("dateRecords", recordsJson)
            .put("statAdjustments", adjustmentsJson)
    }
}

@Suppress("UNUSED_VALUE")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceScreen(
    uiState: AttendanceUiState,
    showHelp: Boolean,
    onSemesterSelected: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onAddSubject: (String) -> Unit,
    onDeleteSubject: (String) -> Unit,
    onToggleAttendance: (String, Boolean) -> Unit,
    onMarkAllPresent: () -> Unit,
    onClearDate: () -> Unit,
    onSetSuspendedSubjects: (Set<String>) -> Unit,
    onSetExtraClasses: (Map<String, Int>) -> Unit,
    onSetTimetableForDay: (String, Set<String>) -> Unit,
    onSetSubjectStats: (String, Int, Int) -> Unit,
    onSetRequiredAttendancePercentage: (Int) -> Unit,
    onEraseAllData: () -> Unit,
    onRestoreDeletedSubject: () -> Unit,
    onShowHelp: () -> Unit,
    onDismissHelp: () -> Unit,
) {
    var currentPage by rememberSaveable { mutableStateOf(AppPage.Attendance.name) }
    var showSemesterMenu by remember { mutableStateOf(false) }
    var editingTimetableDayKey by remember { mutableStateOf<String?>(null) }
    var editingExtraClasses by remember { mutableStateOf(false) }
    var editingSuspendedSubjects by remember { mutableStateOf(false) }
    var showEraseAllDataConfirmation by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Subject?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedDate = remember(uiState.selectedDate) {
        LocalDate.parse(uiState.selectedDate, DATE_FORMATTER)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D1118), Color(0xFF121722)),
                ),
            ),
    ) {
        when (val page = AppPage.valueOf(currentPage)) {
            AppPage.Settings -> PanelCard(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            ) {
                SettingsPage(
                    selectedSemester = uiState.selectedSemester,
                    subjects = uiState.subjects,
                    statsForSubject = uiState::calculateStats,
                    requiredAttendancePercentage = uiState.requiredAttendancePercentage,
                    onAddSubject = onAddSubject,
                    onDeleteSubject = { pendingDelete = it },
                    onSetSubjectStats = onSetSubjectStats,
                    onSetRequiredAttendancePercentage = onSetRequiredAttendancePercentage,
                    onBack = { currentPage = AppPage.Attendance.name },
                    onEraseAllData = { showEraseAllDataConfirmation = true },
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            ) {
                HeroCard(
                    selectedSemester = uiState.selectedSemester,
                    selectedDate = uiState.selectedDate,
                    trackedText = uiState.trackedTodayText,
                    expanded = showSemesterMenu,
                    onExpandedChange = { showSemesterMenu = it },
                    onSemesterSelected = {
                        showSemesterMenu = false
                        onSemesterSelected(it)
                    },
                    onDateClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                onDateSelected(LocalDate.of(year, month + 1, dayOfMonth).format(DATE_FORMATTER))
                            },
                            selectedDate.year,
                            selectedDate.monthValue - 1,
                            selectedDate.dayOfMonth,
                        ).show()
                    },
                    onHelpClick = onShowHelp,
                    onSettingsClick = { currentPage = AppPage.Settings.name },
                )

                Spacer(modifier = Modifier.height(10.dp))

                PanelCard(
                    modifier = Modifier.weight(1f),
                ) {
                    SegmentedPageSwitcher(
                        page = page,
                        onPageSelected = { currentPage = it.name },
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    when (page) {
                        AppPage.Attendance -> AttendancePage(
                            uiState = uiState,
                            onToggleAttendance = onToggleAttendance,
                            onMarkAllPresent = onMarkAllPresent,
                            onClearDate = onClearDate,
                            onEditExtras = { editingExtraClasses = true },
                            onEditSuspendedSubjects = { editingSuspendedSubjects = true },
                        )

                        AppPage.Timetable -> TimetablePage(
                            uiState = uiState,
                            onEditDay = { editingTimetableDayKey = it },
                        )

                        AppPage.Stats -> StatsPage(
                            uiState = uiState,
                        )

                        AppPage.Settings -> Unit

                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
        )
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete subject?") },
            text = { Text("Delete \"${pendingDelete?.name}\"? You can undo it from the popup that appears next.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val subject = pendingDelete ?: return@TextButton
                        onDeleteSubject(subject.id)
                        pendingDelete = null
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Deleted ${subject.name}",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onRestoreDeletedSubject()
                            }
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (editingExtraClasses) {
        ExtraClassCountDialog(
            subjects = uiState.subjects,
            selectedCounts = uiState.extraClassesForSelectedDate(),
            emptyMessage = "Set the number of extra classes for each subject on ${uiState.selectedWeekday.label}.",
            onDismiss = { editingExtraClasses = false },
            onConfirm = {
                onSetExtraClasses(it)
                editingExtraClasses = false
            },
        )
    }

    if (editingSuspendedSubjects) {
        SubjectSelectionDialog(
            title = "Suspended Subjects",
            subjects = uiState.scheduledDateSubjects,
            selectedIds = uiState.suspendedSubjectsForSelectedDate(),
            emptyMessage = "Select the subjects that were suspended on ${uiState.selectedWeekday.label}.",
            onDismiss = { editingSuspendedSubjects = false },
            onConfirm = {
                onSetSuspendedSubjects(it)
                editingSuspendedSubjects = false
            },
        )
    }

    if (editingTimetableDayKey != null) {
        val dayKey = editingTimetableDayKey ?: return
        val weekday = WEEKDAYS.first { it.key == dayKey }
        SubjectSelectionDialog(
            title = "${weekday.label} Timetable",
            subjects = uiState.subjects,
            selectedIds = uiState.timetableForDay(dayKey),
            emptyMessage = "Select the subjects that normally happen every ${weekday.label.lowercase()}.",
            onDismiss = { editingTimetableDayKey = null },
            onConfirm = {
                onSetTimetableForDay(dayKey, it)
                editingTimetableDayKey = null
            },
        )
    }

    if (showEraseAllDataConfirmation) {
        AlertDialog(
            onDismissRequest = { showEraseAllDataConfirmation = false },
            title = { Text("Erase all data?") },
            text = { Text("This will permanently remove all saved semesters, subjects, timetable entries, and attendance records on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEraseAllData()
                        showEraseAllDataConfirmation = false
                        currentPage = AppPage.Attendance.name
                        showSemesterMenu = false
                        editingTimetableDayKey = null
                        editingExtraClasses = false
                        editingSuspendedSubjects = false
                    },
                ) {
                    Text("Erase")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseAllDataConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showHelp) {
        HelpDialog(onDismiss = onDismissHelp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroCard(
    selectedSemester: String,
    selectedDate: String,
    trackedText: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSemesterSelected: (String) -> Unit,
    onDateClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171B24)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "attraker",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onHelpClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "How to use the app",
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Open settings",
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            )
            {
                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = onExpandedChange,
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = selectedSemester,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            singleLine = true,
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { onExpandedChange(false) },
                        ) {
                            SEMESTERS.forEach { semester ->
                                DropdownMenuItem(
                                    text = { Text(semester) },
                                    onClick = { onSemesterSelected(semester) },
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onDateClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Text(
                            text = selectedDate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "Select date",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = trackedText,
                    color = Color(0xFFA3ADBF),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun HelpDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to Use This App") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Open Settings to add your subjects and set any existing attended and total counts.")
                Text("2. Use Timetable to choose which subjects normally happen on each weekday.")
                Text("3. On Attendance, pick a date and tick the checkbox for each class you attended that day.")
                Text("4. Use Extra Classes to add additional class entries for a subject, or Suspended Subjects when a day differs from the normal timetable.")
                Text("5. Your data is stored only on this device, and Erase All Data clears everything.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
    )
}

@Composable
private fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171B24)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SegmentedPageSwitcher(
    page: AppPage,
    onPageSelected: (AppPage) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onPageSelected(AppPage.Attendance) },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (page == AppPage.Attendance) Color(0xFF2F7EF7) else Color(0xFF30384A),
            ),
        ) {
            Text(
                text = "Attendance",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
            )
        }

        OutlinedButton(
            onClick = { onPageSelected(AppPage.Timetable) },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (page == AppPage.Timetable) Color(0xFF2F7EF7) else Color(0xFF30384A),
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "Timetable",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
            )
        }

        OutlinedButton(
            onClick = { onPageSelected(AppPage.Stats) },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (page == AppPage.Stats) Color(0xFF2F7EF7) else Color(0xFF30384A),
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "Stats",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SettingsPage(
    selectedSemester: String,
    subjects: List<Subject>,
    statsForSubject: (String) -> SubjectStats,
    requiredAttendancePercentage: Int,
    onAddSubject: (String) -> Unit,
    onDeleteSubject: (Subject) -> Unit,
    onSetSubjectStats: (String, Int, Int) -> Unit,
    onSetRequiredAttendancePercentage: (Int) -> Unit,
    onBack: () -> Unit,
    onEraseAllData: () -> Unit,
) {
    var newSubjectName by rememberSaveable { mutableStateOf("") }
    var requiredAttendanceText by remember(requiredAttendancePercentage) {
        mutableStateOf(requiredAttendancePercentage.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = "Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Manage subjects for $selectedSemester, set the required attendance percentage, and edit the displayed attended and total class counts.",
            color = Color(0xFFA3ADBF),
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = requiredAttendanceText,
            onValueChange = { value ->
                val sanitized = sanitizeCountInput(value).take(3)
                requiredAttendanceText = sanitized
                onSetRequiredAttendancePercentage(sanitized.toIntOrNull() ?: 0)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Required Attendance %") },
            supportingText = { Text("Subjects below this value are shown in red on Stats.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Spacer(modifier = Modifier.height(16.dp))

        SubjectInputRow(
            subjectName = newSubjectName,
            onSubjectNameChange = { newSubjectName = it },
            onAddSubject = {
                onAddSubject(newSubjectName)
                newSubjectName = ""
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (subjects.isEmpty()) {
            EmptyMessage("No subjects yet. Add one here, then edit its counts below.")
        } else {
            subjects.forEachIndexed { index, subject ->
                SubjectSettingsRow(
                    subject = subject,
                    stats = statsForSubject(subject.id),
                    onDeleteSubject = { onDeleteSubject(subject) },
                    onSetSubjectStats = onSetSubjectStats,
                )

                if (index != subjects.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onEraseAllData,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE24F4F)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Erase All Data")
        }
    }
}

@Composable
private fun SubjectSettingsRow(
    subject: Subject,
    stats: SubjectStats,
    onDeleteSubject: () -> Unit,
    onSetSubjectStats: (String, Int, Int) -> Unit,
) {
    var attendedText by remember(subject.id, stats.totalPresent) {
        mutableStateOf(stats.totalPresent.toString())
    }
    var totalText by remember(subject.id, stats.totalClasses) {
        mutableStateOf(stats.totalClasses.toString())
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10141C)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subject.name,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onDeleteSubject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE24F4F),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = attendedText,
                    onValueChange = { value ->
                        val sanitized = sanitizeCountInput(value)
                        attendedText = sanitized
                        onSetSubjectStats(
                            subject.id,
                            sanitized.toIntOrNull() ?: 0,
                            totalText.toIntOrNull() ?: 0,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Attended") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                OutlinedTextField(
                    value = totalText,
                    onValueChange = { value ->
                        val sanitized = sanitizeCountInput(value)
                        totalText = sanitized
                        onSetSubjectStats(
                            subject.id,
                            attendedText.toIntOrNull() ?: 0,
                            sanitized.toIntOrNull() ?: 0,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Total") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${stats.totalPresent} / ${stats.totalClasses} • ${stats.percentage}%",
                color = Color(0xFFA3ADBF),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SubjectInputRow(
    subjectName: String,
    onSubjectNameChange: (String) -> Unit,
    onAddSubject: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = subjectName,
            onValueChange = onSubjectNameChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Subject") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAddSubject() }),
        )

        Button(
            onClick = onAddSubject,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Add")
        }
    }
}

@Composable
private fun AttendancePage(
    uiState: AttendanceUiState,
    onToggleAttendance: (String, Boolean) -> Unit,
    onMarkAllPresent: () -> Unit,
    onClearDate: () -> Unit,
    onEditExtras: () -> Unit,
    onEditSuspendedSubjects: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10141C)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                uiState.subjects.isEmpty() -> EmptyMessage("Add subjects first, then configure the timetable.")
                uiState.selectedDateSubjects.isEmpty() -> EmptyMessage("No classes are scheduled for ${uiState.selectedWeekday.label}.")
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        uiState.selectedDateAttendanceEntries.forEachIndexed { index, entry ->
                            val stats = uiState.calculateStats(entry.subjectId)
                            SubjectRow(
                                label = entry.label,
                                stats = stats,
                                checked = uiState.selectedDateRecord.attendance[entry.key] == true,
                                onCheckedChange = { onToggleAttendance(entry.key, it) },
                            )

                            if (index != uiState.selectedDateAttendanceEntries.lastIndex) {
                                HorizontalDivider(color = Color(0xFF263045))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactActionButton(
                label = "All Present",
                onClick = onMarkAllPresent,
                modifier = Modifier.weight(1f),
                filled = true,
            )
            CompactActionButton(
                label = "Reset",
                onClick = onClearDate,
                modifier = Modifier.weight(1f),
                containerColor = Color(0xFF747E91),
            )
            CompactActionButton(
                label = "Extra",
                onClick = onEditExtras,
                modifier = Modifier.weight(1f),
                containerColor = Color(0xFF30384A),
            )
            CompactActionButton(
                label = "Suspend",
                onClick = onEditSuspendedSubjects,
                modifier = Modifier.weight(1f),
                containerColor = Color(0xFF30384A),
            )
        }
    }
}

@Composable
private fun TimetablePage(
    uiState: AttendanceUiState,
    onEditDay: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Weekly Timetable",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Choose which subjects normally happen on each day. Daily extra classes and suspensions are handled on the attendance page.",
            color = Color(0xFFA3ADBF),
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.subjects.isEmpty()) {
            EmptyMessage("Add subjects first to build your timetable.")
            return@Column
        }

        WEEKDAYS.forEachIndexed { index, weekday ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10141C)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = weekday.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.daySummary(weekday.key),
                                color = Color(0xFFA3ADBF),
                            )
                        }

                        OutlinedButton(
                            onClick = { onEditDay(weekday.key) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF30384A),
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Edit")
                        }
                    }
                }
            }

            if (index != WEEKDAYS.lastIndex) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun StatsPage(
    uiState: AttendanceUiState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Subject Stats",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "See the attendance percentage for every subject in the selected semester. Required attendance: ${uiState.requiredAttendancePercentage}%.",
            color = Color(0xFFA3ADBF),
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.subjects.isEmpty()) {
            EmptyMessage("Add subjects first to see attendance stats.")
            return@Column
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10141C)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                uiState.subjects.forEachIndexed { index, subject ->
                    val stats = uiState.calculateStats(subject.id)
                    StatsRow(
                        subject = subject,
                        stats = stats,
                        requiredAttendancePercentage = uiState.requiredAttendancePercentage,
                    )

                    if (index != uiState.subjects.lastIndex) {
                        HorizontalDivider(color = Color(0xFF263045))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    containerColor: Color = Color(0xFF30384A),
) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = containerColor,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun StatsRow(
    subject: Subject,
    stats: SubjectStats,
    requiredAttendancePercentage: Int,
) {
    val percentageColor = if (stats.percentage < requiredAttendancePercentage) {
        Color(0xFFE85B5B)
    } else {
        Color(0xFF53D769)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subject.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${stats.totalPresent} / ${stats.totalClasses} classes",
                color = Color(0xFFA3ADBF),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${stats.percentage}%",
            color = percentageColor,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun SubjectSelectionDialog(
    title: String,
    subjects: List<Subject>,
    selectedIds: Set<String>,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var draftSelection by remember(title, subjects, selectedIds) {
        mutableStateOf(selectedIds)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (subjects.isEmpty()) {
                Text(emptyMessage)
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    subjects.forEach { subject ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = subject.id in draftSelection,
                                onCheckedChange = { checked ->
                                    draftSelection = if (checked) {
                                        draftSelection + subject.id
                                    } else {
                                        draftSelection - subject.id
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = subject.name,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draftSelection) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ExtraClassCountDialog(
    subjects: List<Subject>,
    selectedCounts: Map<String, Int>,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Int>) -> Unit,
) {
    var draftCounts by remember(subjects, selectedCounts) {
        mutableStateOf(selectedCounts.filterValues { it > 0 })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extra Classes") },
        text = {
            if (subjects.isEmpty()) {
                Text(emptyMessage)
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    subjects.forEach { subject ->
                        val currentCount = draftCounts[subject.id] ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = subject.name,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                            )
                            CompactCounter(
                                value = currentCount,
                                onValueChange = { updatedCount ->
                                    draftCounts = if (updatedCount <= 0) {
                                        draftCounts - subject.id
                                    } else {
                                        draftCounts + (subject.id to updatedCount)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draftCounts) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SubjectRow(
    label: String,
    stats: SubjectStats,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${stats.totalPresent} / ${stats.totalClasses} classes",
                color = Color(0xFFA3ADBF),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${stats.percentage}%",
            color = Color(0xFFEEF2FF),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CompactCounter(
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int = 0,
    maxValue: Int = Int.MAX_VALUE,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedButton(
            onClick = { onValueChange((value - 1).coerceAtLeast(minValue)) },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text("-")
        }
        Text(
            text = value.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        OutlinedButton(
            onClick = { onValueChange((value + 1).coerceAtMost(maxValue)) },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text("+")
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color(0xFFA3ADBF))
    }
}

@Composable
private fun AttendanceTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF2F7EF7),
        secondary = Color(0xFF747E91),
        background = Color(0xFF0D1118),
        surface = Color(0xFF171B24),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFFEEF2FF),
        onSurface = Color(0xFFEEF2FF),
        error = Color(0xFFE24F4F),
    )

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}

private fun weekdayForDate(date: String): WeekdayInfo {
    val dayOfWeek = LocalDate.parse(date, DATE_FORMATTER).dayOfWeek
    return WEEKDAYS.first { it.dayOfWeek == dayOfWeek }
}

private fun normalAttendanceKey(subjectId: String): String = subjectId

private fun extraAttendanceKey(subjectId: String, index: Int): String = "$subjectId::extra::$index"

private fun subjectIdFromAttendanceKey(key: String): String? =
    key.substringBefore("::extra::").takeIf { it.isNotBlank() }

private fun attendanceKeyBelongsToSubject(key: String, subjectId: String): Boolean =
    subjectIdFromAttendanceKey(key) == subjectId

private fun sanitizeCountInput(value: String): String =
    value.filter { it.isDigit() }.ifEmpty { "0" }

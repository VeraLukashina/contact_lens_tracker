package com.example.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LensViewModel(private val repository: LensRepository) : ViewModel() {

    val activeLens: StateFlow<LensWear?> = repository.activeLensWear
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allStocks: StateFlow<List<LensStock>> = repository.allLensStock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ophthalmologistVisit: StateFlow<OphthalmologistVisit?> = repository.ophthalmologistVisit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Derived State: Current wear statistics
    val activeLensStats: StateFlow<LensStats?> = activeLens.map { lens ->
        if (lens == null) return@map null

        val skippedSet = lens.skippedDates.split(",")
            .filter { it.isNotEmpty() }
            .toSet()

        val allDates = DateUtils.getDaysBetween(lens.startDate, System.currentTimeMillis())
        val skippedCount = allDates.count { it in skippedSet }
        
        val daysElapsed = DateUtils.getDaysElapsed(lens.startDate, System.currentTimeMillis())
        val daysWorn = (daysElapsed - skippedCount).coerceAtLeast(0)
        
        val isOverdue = daysWorn > lens.durationDays
        val remainingDays = if (isOverdue) 0 else lens.durationDays - daysWorn
        val overdueDays = if (isOverdue) daysWorn - lens.durationDays else 0
        val isTodaySkipped = DateUtils.getTodayString() in skippedSet

        LensStats(
            daysWorn = daysWorn,
            remainingDays = remainingDays,
            overdueDays = overdueDays,
            isOverdue = isOverdue,
            isTodaySkipped = isTodaySkipped,
            totalDaysElapsed = daysElapsed,
            skippedDaysCount = skippedSet.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Derived State: Alerts regarding low stock (exactly 1 pair remains)
    val stockWarnings: StateFlow<List<String>> = allStocks.map { stocks ->
        val warnings = mutableListOf<String>()
        stocks.forEach { stock ->
            val totalPairs = (stock.boxCount * stock.pairsPerBox) + stock.pairsInOpenBox
            if (totalPairs == 1) {
                if (stock.brand == "Контактные линзы" && stock.diopter == "0.0") {
                    warnings.add("Внимание: у вас осталась последняя пара линз!")
                } else {
                    warnings.add("Внимание: у вас осталась последняя пара линз марки \"${stock.brand}\" (${stock.diopter} D)!")
                }
            } else if (totalPairs == 0) {
                if (stock.brand == "Контактные линзы" && stock.diopter == "0.0") {
                    warnings.add("Запасы линз полностью закончились!")
                } else {
                    warnings.add("Запасы линз марки \"${stock.brand}\" (${stock.diopter} D) полностью закончились!")
                }
            }
        }
        warnings
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived State: Ophthalmologist checkup alerts
    val ophthalmologistAlert: StateFlow<OphAlert?> = ophthalmologistVisit.map { visit ->
        if (visit == null || visit.lastVisitDate == null) return@map null

        val oneYearLater = DateUtils.addOneYear(visit.lastVisitDate)
        val today = System.currentTimeMillis()

        if (today >= oneYearLater) {
            // Check if next appointment is not booked, or already passed
            val nextBooked = visit.nextAppointmentDate
            if (nextBooked == null || nextBooked < today) {
                val weeksOverdue = DateUtils.getWeeksPassed(oneYearLater, today)
                val totalReminders = weeksOverdue + 1
                return@map OphAlert(
                    isCritical = true,
                    message = "Вам необходимо записаться на осмотр к офтальмологу! Последний визит был более 1 года назад (${DateUtils.formatDateToDisplay(visit.lastVisitDate)}).",
                    weeksOverdue = weeksOverdue,
                    remindersCount = totalReminders
                )
            }
        }
        null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Actions
    fun startNewLensPair(durationDays: Int, stockToUse: LensStock?) {
        viewModelScope.launch {
            repository.startNewLensPair(durationDays, stockToUse)
        }
    }

    fun stopWearingActiveLens() {
        viewModelScope.launch {
            repository.deactivateActiveLens()
        }
    }

    fun updateActiveLensStartDate(newStartDate: Long) {
        viewModelScope.launch {
            val lens = activeLens.value ?: return@launch
            val updatedLens = lens.copy(startDate = newStartDate)
            repository.updateLensWear(updatedLens)
        }
    }

    fun updateActiveLensDuration(durationDays: Int) {
        viewModelScope.launch {
            val lens = activeLens.value ?: return@launch
            val updatedLens = lens.copy(durationDays = durationDays)
            repository.updateLensWear(updatedLens)
        }
    }

    fun toggleTodaySkipped() {
        viewModelScope.launch {
            val lens = activeLens.value ?: return@launch
            val todayStr = DateUtils.getTodayString()
            val skippedList = lens.skippedDates.split(",").filter { it.isNotEmpty() }.toMutableList()

            if (skippedList.contains(todayStr)) {
                skippedList.remove(todayStr)
            } else {
                skippedList.add(todayStr)
            }

            val updatedLens = lens.copy(skippedDates = skippedList.joinToString(","))
            repository.updateLensWear(updatedLens)
        }
    }

    fun addNewStock(brand: String, diopter: String, pairsPerBox: Int, boxCount: Int, pairsInOpenBox: Int) {
        viewModelScope.launch {
            val newStock = LensStock(
                brand = brand,
                diopter = diopter,
                pairsPerBox = pairsPerBox,
                boxCount = boxCount,
                pairsInOpenBox = pairsInOpenBox
            )
            repository.addLensStock(newStock)
        }
    }

    fun updateStock(stock: LensStock) {
        viewModelScope.launch {
            repository.updateLensStock(stock)
        }
    }

    fun deleteStock(stock: LensStock) {
        viewModelScope.launch {
            repository.deleteLensStock(stock)
        }
    }

    fun recordCheckup(lastVisitDate: Long, nextAppointmentDate: Long?) {
        viewModelScope.launch {
            repository.saveOphthalmologistVisit(lastVisitDate, nextAppointmentDate)
        }
    }

    fun clearOphthalmologistData() {
        viewModelScope.launch {
            repository.saveOphthalmologistVisit(null, null)
        }
    }
}

// Data structures for ViewModel UI State mapping
data class LensStats(
    val daysWorn: Int,
    val remainingDays: Int,
    val overdueDays: Int,
    val isOverdue: Boolean,
    val isTodaySkipped: Boolean,
    val totalDaysElapsed: Int,
    val skippedDaysCount: Int
)

data class OphAlert(
    val isCritical: Boolean,
    val message: String,
    val weeksOverdue: Int,
    val remindersCount: Int
)

class LensViewModelFactory(private val repository: LensRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LensViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LensViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

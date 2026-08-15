package com.example.data

import kotlinx.coroutines.flow.Flow

class LensRepository(private val lensDao: LensDao) {
    val allLensWear: Flow<List<LensWear>> = lensDao.getAllLensWear()
    val activeLensWear: Flow<LensWear?> = lensDao.getActiveLensWear()
    val allLensStock: Flow<List<LensStock>> = lensDao.getAllLensStock()
    val ophthalmologistVisit: Flow<OphthalmologistVisit?> = lensDao.getOphthalmologistVisit()

    suspend fun insertLensWear(lensWear: LensWear) {
        lensDao.insertLensWear(lensWear)
    }

    suspend fun updateLensWear(lensWear: LensWear) {
        lensDao.updateLensWear(lensWear)
    }

    suspend fun deactivateActiveLens() {
        lensDao.deactivateAllLensWear()
    }

    suspend fun startNewLensPair(durationDays: Int, stockToUse: LensStock?) {
        // 1. Deactivate any currently active lens
        lensDao.deactivateAllLensWear()

        // 2. Reduce stock if a specific stock package is selected
        if (stockToUse != null) {
            val updatedStock = when {
                stockToUse.pairsInOpenBox > 0 -> {
                    stockToUse.copy(pairsInOpenBox = stockToUse.pairsInOpenBox - 1)
                }
                stockToUse.boxCount > 0 -> {
                    stockToUse.copy(
                        boxCount = stockToUse.boxCount - 1,
                        pairsInOpenBox = stockToUse.pairsPerBox - 1
                    )
                }
                else -> {
                    stockToUse // Already completely empty
                }
            }
            lensDao.updateLensStock(updatedStock)
        }

        // 3. Create and insert the new active lens record
        val newLens = LensWear(
            startDate = System.currentTimeMillis(),
            durationDays = durationDays,
            isActive = true,
            skippedDates = ""
        )
        lensDao.insertLensWear(newLens)
    }

    suspend fun addLensStock(stock: LensStock) {
        lensDao.insertLensStock(stock)
    }

    suspend fun updateLensStock(stock: LensStock) {
        lensDao.updateLensStock(stock)
    }

    suspend fun deleteLensStock(stock: LensStock) {
        lensDao.deleteLensStock(stock)
    }

    suspend fun saveOphthalmologistVisit(lastVisit: Long?, nextAppointment: Long?) {
        val visit = OphthalmologistVisit(
            id = 1,
            lastVisitDate = lastVisit,
            nextAppointmentDate = nextAppointment
        )
        lensDao.insertOphthalmologistVisit(visit)
    }
}

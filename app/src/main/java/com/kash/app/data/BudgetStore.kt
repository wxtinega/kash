package com.kash.app.data

import com.kash.insights.Budget
import com.kash.insights.Category
import com.kash.parser.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BudgetSettings(val enabled: Boolean = false, val budgets: List<Budget> = emptyList())

/** The user's budgets, kept encrypted in [KashPrefs]. Screens observe [state]. */
class BudgetStore(private val prefs: KashPrefs) {
    private val _state = MutableStateFlow(BudgetSettings(prefs.budgetsEnabled(), prefs.budgets()))
    val state: StateFlow<BudgetSettings> = _state.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.setBudgetsEnabled(on)
        _state.value = _state.value.copy(enabled = on)
    }

    /** Add or change the limit for [category] (null = all spending). */
    fun set(category: Category?, limit: Money) {
        val list = _state.value.budgets.filter { it.category != category } + Budget(category, limit)
        save(list.sortedBy { it.category?.ordinal ?: -1 })
    }

    fun remove(category: Category?) = save(_state.value.budgets.filter { it.category != category })

    private fun save(list: List<Budget>) {
        prefs.setBudgets(list)
        _state.value = _state.value.copy(budgets = list)
    }
}

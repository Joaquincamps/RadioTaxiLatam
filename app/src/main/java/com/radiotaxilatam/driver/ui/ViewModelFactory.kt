package com.radiotaxilatam.driver.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.radiotaxilatam.driver.TaxiApp
import com.radiotaxilatam.driver.ui.history.HistoryViewModel
import com.radiotaxilatam.driver.ui.home.HomeViewModel
import com.radiotaxilatam.driver.ui.login.LoginViewModel

val AppViewModelFactory = viewModelFactory {
    initializer { LoginViewModel((this[APPLICATION_KEY] as TaxiApp).repository) }
    initializer { HomeViewModel((this[APPLICATION_KEY] as TaxiApp).repository) }
    initializer { HistoryViewModel((this[APPLICATION_KEY] as TaxiApp).repository) }
}

package com.dev.usdi_wallet.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dev.usdi_wallet.domain.auth.AndroidWalletAuthManager
import com.dev.usdi_wallet.domain.auth.WalletAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LockState {
    object Idle : LockState()
    object Authenticating : LockState()
    object Authenticated : LockState()
    object Failed : LockState()
}

class LockViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val walletAuthManager = AndroidWalletAuthManager.getInstance()
    private val _state = MutableStateFlow<LockState>(LockState.Idle)
    val state = _state.asStateFlow()

    fun authenticate() {
        if (_state.value == LockState.Authenticating || _state.value == LockState.Authenticated) return
        viewModelScope.launch {
            _state.value = LockState.Authenticating
            val success = walletAuthManager.requestAuth()
            _state.value = if (success) LockState.Authenticated else LockState.Failed
        }
    }
}
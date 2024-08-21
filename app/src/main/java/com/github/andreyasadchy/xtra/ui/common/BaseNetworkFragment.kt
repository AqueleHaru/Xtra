package com.github.andreyasadchy.xtra.ui.common

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.ui.main.MainViewModel
import com.github.andreyasadchy.xtra.util.isNetworkAvailable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BaseNetworkFragment : Fragment() {

    private companion object {
        const val LAST_KEY = "last"
        const val RESTORE_KEY = "restore"
        const val CREATED_KEY = "created"
    }

    private val mainViewModel: MainViewModel by activityViewModels()

    protected open var enableNetworkCheck = true
    private var lastIsOnlineState = false
    private var shouldRestore = false
    protected var isInitialized = false
    private var created = false

    abstract fun initialize()
    abstract fun onNetworkRestored()
    open fun onNetworkLost() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (enableNetworkCheck) {
            lastIsOnlineState = savedInstanceState?.getBoolean(LAST_KEY) ?: requireContext().isNetworkAvailable
            shouldRestore = savedInstanceState?.getBoolean(RESTORE_KEY) ?: false
            created = savedInstanceState?.getBoolean(CREATED_KEY) ?: false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (enableNetworkCheck) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    mainViewModel.isNetworkAvailable.collectLatest { online ->
                        if (online != null) {
                            if (online) {
                                if (!lastIsOnlineState) {
                                    shouldRestore = if (isResumed) {
                                        if (isInitialized) {
                                            onNetworkRestored()
                                        } else {
                                            init()
                                        }
                                        false
                                    } else {
                                        true
                                    }
                                }
                            } else {
                                if (isInitialized) {
                                    onNetworkLost()
                                }
                            }
                            lastIsOnlineState = online
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (enableNetworkCheck) {
            if (!isInitialized) {
                if (created || lastIsOnlineState) {
                    init()
                }
            } else if (shouldRestore && lastIsOnlineState) {
                onNetworkRestored()
                shouldRestore = false
            }
        } else {
            initialize()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (enableNetworkCheck) {
            isInitialized = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (enableNetworkCheck) {
            outState.putBoolean(LAST_KEY, lastIsOnlineState)
            outState.putBoolean(RESTORE_KEY, shouldRestore)
            outState.putBoolean(CREATED_KEY, created)
        }
    }

    private fun init() {
        initialize()
        isInitialized = true
        created = true
    }
}
package com.dev.usdi_wallet.ui.contact

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dev.usdi_wallet.databinding.FragmentContactBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.ComposeView
class ContactFragment : Fragment() {
private val viewModel: ContactViewModel by viewModels();
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                ContactScreen(viewModel)
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(): ContactFragment = ContactFragment()
    }

//    private fun showSendMessageDialog(message: String) {
//        val input = EditText(requireContext()).apply {
//            hint = "Raw DIDComm JSON message"
//            minLines = 5
//            maxLines = 10
//            setPadding(48, 24, 48, 8)
//        }
//
//        AlertDialog.Builder(requireContext())
//            .setTitle("Send message")
//            .setMessage("Paste a raw DIDComm-compatible JSON body")
//            .setView(input)
//            .setPositiveButton("Send") { _, _ ->
//                viewModel.sendRawMessage(input.text.toString())
//            }
//            .setNegativeButton("Cancel") { _, _ ->
//                viewModel.onSendMessageDialogDismissed()
//            }
//            .setOnCancelListener {
//                viewModel.onSendMessageDialogDismissed()
//            }
//            .show()
//    }
}
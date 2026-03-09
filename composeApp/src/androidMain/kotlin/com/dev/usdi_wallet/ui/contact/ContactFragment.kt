package com.dev.usdi_wallet.ui.contact

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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

class ContactFragment : Fragment() {
    private var _binding: FragmentContactBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ContactViewModel by viewModels()
    private val adapter = ContactAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ContactFragment.adapter
        }
    }

    private fun setupFab() {
        binding.fabAddContact.setOnClickListener {
            viewModel.onAddContactClicked()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.contacts.collect { contacts ->
                        adapter.submitList(contacts)
                        binding.tvEmptyState.isVisible = contacts.isEmpty()
                        binding.rvContacts.isVisible = contacts.isNotEmpty()
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state.isLoading

                        if (state.showInvitationDialog) showInvitationDialog()

//                        if (state.showSendMessageDialog && state.selectedContact != null) {
//                            showSendMessageDialog(state.selectedContact.name)
//                        }

                        state.error?.let { msg ->
                            Snackbar.make(
                                binding.root,
                                msg,
                                Snackbar.LENGTH_LONG
                            ).show()
                            viewModel.onErrorShown()
                        }

                        state.snackbarMessage?.let { msg ->
                            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                            viewModel.onSnackbarShown()
                        }
                    }
                }
            }
        }
    }

    private fun showInvitationDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Paste OOB invitation URL or JSON"
            minLines = 3
            maxLines = 8
            setPadding(48, 24, 48, 8)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add contact")
            .setMessage("Paste an Out-of-Band invitation to connect.")
            .setView(input)
            .setPositiveButton("Accept") { _, _ ->
                viewModel.submitInvitation(input.text.toString())
            }
            .setNegativeButton("Cancel") { _, _ ->
                viewModel.onInvitationDialogDismissed()
            }
            .show()
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
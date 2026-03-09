package com.dev.usdi_wallet.ui.credential

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dev.usdi_wallet.credential.Credential
import com.dev.usdi_wallet.databinding.FragmentCredentialBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CredentialFragment : Fragment() {
    private var _binding: FragmentCredentialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CredentialViewModel by viewModels()

    private val adapter = CredentialAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCredentialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        binding.rvCredentials.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@CredentialFragment.adapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.credentials.collect { credentials ->
                        adapter.submitList(credentials)
                        binding.tvEmptyState.isVisible = credentials.isEmpty()
                        binding.rvCredentials.isVisible = credentials.isNotEmpty()
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        state.selectedCredential?.let {
                            showDetailDialog(it)
                            viewModel.onDetailDismissed()
                        }

                        state.error?.let { msg ->
                            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                            viewModel.onErrorShown()
                        }
                    }
                }
            }
        }
    }

    private fun showDetailDialog(credential: Credential) {
        val claims = credential.claims

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(credential.subject)
            .setMessage(claims)
            .setPositiveButton("Close", null)
            .show()
    }

    companion object {
        fun newInstance() = CredentialFragment()
    }
}
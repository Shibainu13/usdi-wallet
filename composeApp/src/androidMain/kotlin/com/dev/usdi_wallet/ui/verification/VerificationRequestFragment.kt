package com.dev.usdi_wallet.ui.verification

import android.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dev.usdi_wallet.databinding.FragmentVerificationRequestBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class VerificationRequestFragment : Fragment() {
    private var _binding: FragmentVerificationRequestBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VerificationRequestViewModel by viewModels()

    private lateinit var claimCheckAdapter: ClaimCheckAdapter
    private lateinit var manualClaimAdapter: ManualClaimAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerificationRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupTabs()
        setupButtons()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAdapters() {
        claimCheckAdapter = ClaimCheckAdapter(
            onChecked = viewModel::onClaimChecked,
            onConstraintChanged = viewModel::onClaimConstraintChanged,
            onPredicateOperatorChanged = viewModel::onClaimPredicateOperatorChanged,
            onPredicateValueChanged = viewModel::onClaimPredicateValueChanged,
        )
        binding.rvClaimItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = claimCheckAdapter
        }

        manualClaimAdapter = ManualClaimAdapter(
            onNameChanged = viewModel::onManualRowNameChanged,
            onTypeChanged = viewModel::onManualRowTypeChanged,
            onConstraintChanged = viewModel::onManualRowConstraintChanged,
            onPredicateOperatorChanged = viewModel::onManualRowPredicateOperatorChanged,
            onPredicateValueChanged = viewModel::onManualRowPredicateValueChanged,
            onRemove = viewModel::removeManualRow,
        )
        binding.rvManualClaims.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = manualClaimAdapter
        }
    }

    private fun setupTabs() {
        showFromCredentialTab()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> showFromCredentialTab()
                    1 -> showManualTab()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupButtons() {
        binding.btnSendFromCredential.setOnClickListener { viewModel.sendFromCredential() }
        binding.btnSendManual.setOnClickListener { viewModel.sendManual() }
        binding.btnAddRow.setOnClickListener { viewModel.addManualRow() }
        binding.btnRegenerateChallenge.setOnClickListener { viewModel.regenerateChallenge() }
        binding.etDomain.doAfterTextChanged { viewModel.onDomainChanged(it.toString()) }
        binding.etChallenge.doAfterTextChanged { viewModel.onChallengeChanged(it.toString()) }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.credentials.collect { credentials ->
                        val credentialLabels = credentials.map { it.subject }
                        binding.spinnerCredential.adapter = ArrayAdapter(
                            requireContext(),
                            R.layout.simple_spinner_dropdown_item,
                            credentialLabels,
                        )
                        binding.spinnerCredential.onItemSelectedListener =
                            object : AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(
                                    parent: AdapterView<*>?,
                                    view: View?,
                                    position: Int,
                                    id: Long,
                                ) {
                                    if (credentials.isNotEmpty()) {
                                        viewModel.onCredentialSelected(credentials[position])
                                    }
                                }

                                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                            }
                        binding.tvNoCredential.isVisible = credentials.isEmpty()
                    }
                }

                launch {
                    viewModel.contacts.collect { contacts ->
                        val contactLabels = contacts.map { it.holder }
                        binding.spinnerContact.adapter = ArrayAdapter(
                            requireContext(),
                            R.layout.simple_spinner_dropdown_item,
                            contactLabels,
                        )
                        binding.spinnerContact.onItemSelectedListener =
                            object : AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(
                                    parent: AdapterView<*>?,
                                    view: View?,
                                    position: Int,
                                    id: Long
                                ) {
                                    if (contacts.isNotEmpty()) {
                                        viewModel.onContactSelected(contacts[position])
                                    }
                                }

                                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                            }
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state.isLoading
                        binding.btnSendFromCredential.isEnabled = !state.isLoading
                        binding.btnSendManual.isEnabled = !state.isLoading

                        if (binding.etChallenge.text.toString() != state.challenge) {
                            binding.etChallenge.setText(state.challenge)
                        }

                        claimCheckAdapter.submitList(state.claimItems)
                        manualClaimAdapter.submitList(state.manualClaimRows)

                        state.error?.let { msg ->
                            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                        }

                        if (state.success) {
                            Snackbar.make(binding.root, "Verification request sent", Snackbar.LENGTH_SHORT).show()
                            viewModel.onSuccessHandled()
                        }
                    }
                }
            }
        }
    }

    private fun showFromCredentialTab() {
        binding.fromCredentialContent.isVisible = true
        binding.manualContent.isVisible = false
    }

    private fun showManualTab() {
        binding.fromCredentialContent.isVisible = false
        binding.manualContent.isVisible = true
    }

    companion object {
        fun newInstance() = VerificationRequestFragment()
    }
}
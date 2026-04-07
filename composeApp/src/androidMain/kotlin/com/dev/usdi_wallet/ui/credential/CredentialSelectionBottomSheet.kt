package com.dev.usdi_wallet.ui.credential

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dev.usdi_wallet.credential.Credential
import com.dev.usdi_wallet.databinding.BottomSheetCredentialBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class CredentialSelectionBottomSheet(
    private val credentials: List<Credential>,
    private val onSelected: suspend (Credential) -> Unit,
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCredentialBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetCredentialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCredentials.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = CredentialAdapter(
                onClick = { credential ->
                    lifecycleScope.launch {
                        onSelected(credential)
                        dismiss()
                    }
                }
            )
                .also { it.submitList(credentials) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CredentialSelectionBottomSheet"
    }
}
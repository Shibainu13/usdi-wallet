package com.dev.usdi_wallet.ui.agent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.dev.usdi_wallet.connection.ConnectionStartupConfig
import com.dev.usdi_wallet.hyperledger_identus.IdentusDIDCommConnectionManager
import com.dev.usdi_wallet.databinding.FragmentAgentBinding

class AgentFragment : Fragment() {
    private var _binding: FragmentAgentBinding? = null
    private val viewModel: AgentViewModel by viewModels()

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAgentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnConnect.setOnClickListener {
            val mediatorDID = binding.etMediatorDid.text.toString()
            viewModel.startAgents(listOf(
                ConnectionStartupConfig(
                    IdentusDIDCommConnectionManager.PROTOCOL_ID,
                    mediatorDID
                )
            ))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(): AgentFragment = AgentFragment()
    }
}
package com.dev.usdi_wallet.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dev.usdi_wallet.connection.ConnectionState
import com.dev.usdi_wallet.databinding.ActivityMainBinding
import com.dev.usdi_wallet.hyperledger_identus.IdentusJWTProtocol
import com.dev.usdi_wallet.protocol.Protocol
import com.dev.usdi_wallet.ui.credential.CredentialSelectionBottomSheet
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sectionsPagerAdapter: SectionPagerAdapter
    private lateinit var protocols: List<Protocol<*,*>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        protocols = listOf<Protocol<*,*>>(
            IdentusJWTProtocol.getInstance(application),
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sectionsPagerAdapter = SectionPagerAdapter(this, supportFragmentManager)
        startAgents()
        observeProofRequests()
    }

    private fun startAgents() {
        lifecycleScope.launch {
            protocols.forEach { it.startConnection() }
        }

        lifecycleScope.launch {
            combine(protocols.map { it.connectionManager.state }) { states ->
                states.all { it == ConnectionState.RUNNING }
            }.collect { allRunning ->
                if (allRunning) showMainContent()
            }
        }
    }

    private fun <C, M> observeProtocolProofRequests(protocol: Protocol<C, M>) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                protocol.credentialManager.getProofRequestsToProcess().collect { requests ->
                    requests.forEach { request ->
                        val tag = "proof_${UUID.randomUUID()}"
                        if (supportFragmentManager.findFragmentByTag(tag) == null) {
                            CredentialSelectionBottomSheet(
                                credentials = protocol.credentialManager.let {
                                    it.getCredentials().first().map { credential ->
                                        it.toUiCredential(credential)
                                    }
                                },
                                onSelected = { credential ->
                                    lifecycleScope.launch {
                                        protocol.credentialManager.let {
                                            it.preparePresentationProof(
                                                it.toSdkCredential(credential),
                                                request
                                            )
                                        }
                                    }
                                },
                            ).show(supportFragmentManager, tag)
                        }
                    }
                }
            }
        }
    }

    private fun observeProofRequests() {
        protocols.forEach { protocol -> observeProtocolProofRequests(protocol) }
    }

    private fun showMainContent() {
        if (binding.viewPager.adapter == null) {
            binding.viewPager.adapter = sectionsPagerAdapter
            binding.tabs.setupWithViewPager(binding.viewPager)
        }
        binding.tabs.isVisible = true
        binding.viewPager.isVisible = true
    }
}
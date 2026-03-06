package com.dev.usdi_wallet.ui.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dev.usdi_wallet.R
import com.dev.usdi_wallet.connection.ConnectionState
import com.dev.usdi_wallet.databinding.ActivityMainBinding
import com.dev.usdi_wallet.ui.agent.AgentFragment
import com.dev.usdi_wallet.ui.agent.AgentViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sectionPagerAdapter: SectionPagerAdapter

    private val agentViewModel: AgentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sectionPagerAdapter = SectionPagerAdapter(this, supportFragmentManager)

        if (savedInstanceState == null) {
            showAgentFragment()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                agentViewModel.aggregateState.collect { state ->
                    when (state) {
                        ConnectionState.RUNNING -> showMainContent()
                        ConnectionState.STOPPED,
                        ConnectionState.ERROR   -> showAgentFragment()
                        else                    -> Unit
                    }
                }
            }
        }
    }

    private fun showAgentFragment() {
        binding.tabs.isVisible = false
        binding.viewPager.isVisible = false
        binding.setupContainer.isVisible = true

        if (supportFragmentManager.findFragmentByTag(TAG_AGENT) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.setup_container, AgentFragment.newInstance(), TAG_AGENT)
                .commit()
        }
    }

    private fun showMainContent() {
        binding.setupContainer.isVisible = false

        if (binding.viewPager.adapter == null) {
            binding.viewPager.adapter = sectionPagerAdapter
            binding.tabs.setupWithViewPager(binding.viewPager)
        }

        binding.tabs.isVisible = true
        binding.viewPager.isVisible = true
    }

    companion object {
        private const val TAG_AGENT = "agent_fragment"
    }
}
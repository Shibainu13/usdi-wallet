package com.dev.usdi_wallet.ui.main

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.dev.usdi_wallet.R
import com.dev.usdi_wallet.ui.agent.AgentFragment
import com.dev.usdi_wallet.ui.contact.ContactFragment

private val TAB_TITLES = arrayOf(
    R.string.tab_agent,
    R.string.tab_contact,
)

class SectionPagerAdapter(
    private val context: Context,
    fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getItem(position: Int): Fragment = when (position) {
        0 -> AgentFragment.newInstance()
        1 -> ContactFragment.newInstance()
        else -> throw IllegalArgumentException("No fragment at position $position")
    }

    override fun getPageTitle(position: Int): CharSequence {
        return context.resources.getString(TAB_TITLES[position])
    }

    override fun getCount(): Int = TAB_TITLES.size
}
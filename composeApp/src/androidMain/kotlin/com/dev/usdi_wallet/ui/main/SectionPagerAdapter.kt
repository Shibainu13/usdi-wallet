package com.dev.usdi_wallet.ui.main

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.dev.usdi_wallet.R
import com.dev.usdi_wallet.ui.contact.ContactFragment
import com.dev.usdi_wallet.ui.credential.CredentialFragment
import com.dev.usdi_wallet.ui.verification.VerificationRequestFragment

private val TAB_TITLES = arrayOf(
    R.string.tab_contact,
    R.string.tab_credential,
    R.string.tab_verify,
)

class SectionPagerAdapter(
    private val context: Context,
    fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getItem(position: Int): Fragment = when (position) {
        0 -> ContactFragment.newInstance()
        1 -> CredentialFragment.newInstance()
        2 -> VerificationRequestFragment.newInstance()
        else -> throw IllegalArgumentException("No fragment at position $position")
    }

    override fun getPageTitle(position: Int): CharSequence {
        return context.resources.getString(TAB_TITLES[position])
    }

    override fun getCount(): Int = TAB_TITLES.size
}
package com.dev.usdi_wallet.ui.credential

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.databinding.ItemCredentialBinding

class CredentialAdapter(
    private val onClick: (Credential) -> Unit = {},
) : ListAdapter<Credential, CredentialAdapter.CredentialViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CredentialViewHolder {
        val binding = ItemCredentialBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CredentialViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CredentialViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CredentialViewHolder(
        private val binding: ItemCredentialBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(credential: Credential) {
            binding.tvCredentialType.text = credential.protocol ?: "Credential"
            binding.tvSubject.text = credential.subject
            binding.tvIssuer.text = credential.issuer
            binding.tvClaims.text = credential.claims.toString()
            binding.root.setOnClickListener { onClick(credential) }
        }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<Credential>() {
            override fun areItemsTheSame(old: Credential, new: Credential) = old.id == new.id
            override fun areContentsTheSame(old: Credential, new: Credential) = old == new
        }
    }
}
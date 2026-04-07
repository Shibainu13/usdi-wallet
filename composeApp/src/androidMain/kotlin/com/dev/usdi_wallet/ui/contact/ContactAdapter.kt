package com.dev.usdi_wallet.ui.contact

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dev.usdi_wallet.contact.Contact
import com.dev.usdi_wallet.databinding.ItemContactBinding

class ContactAdapter(
    private val onClick: (Contact) -> Unit = {},
) : ListAdapter<Contact, ContactAdapter.ContactHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ContactHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ContactHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ContactHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: Contact) {
            binding.tvContactName.text   = contact.name
            binding.tvContactHolder.text = contact.holder
            binding.root.setOnClickListener { onClick(contact) }
        }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(old: Contact, new: Contact) =
                old.holder == new.holder && old.name == new.name
            override fun areContentsTheSame(old: Contact, new: Contact) = old == new
        }
    }
}
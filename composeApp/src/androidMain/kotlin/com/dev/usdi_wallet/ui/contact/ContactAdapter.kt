package com.dev.usdi_wallet.ui.contact

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.dev.usdi_wallet.contact.Contact
import com.dev.usdi_wallet.databinding.PlaceholderContactBinding

class ContactAdapter(
    private var data: MutableList<Contact> = mutableListOf()
) : RecyclerView.Adapter<ContactAdapter.ContactHolder>() {

    fun updateContacts(updatedContacts: List<Contact>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = data.size

            override fun getNewListSize() = updatedContacts.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                data[oldItemPosition].holder == updatedContacts[newItemPosition].holder &&
                        data[oldItemPosition].name == updatedContacts[newItemPosition].name

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                data[oldItemPosition] == updatedContacts[newItemPosition]
        })

        data.clear()
        data.addAll(updatedContacts)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactHolder {
        val binding = PlaceholderContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount() = data.size

    class ContactHolder(
        private val binding: PlaceholderContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: Contact) {
            binding.tvContactName.text = contact.name
            binding.tvContactHolder.text = contact.holder
        }
    }
}
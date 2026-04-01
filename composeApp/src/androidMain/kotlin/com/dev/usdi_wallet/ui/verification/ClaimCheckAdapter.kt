package com.dev.usdi_wallet.ui.verification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dev.usdi_wallet.credential.ClaimType
import com.dev.usdi_wallet.credential.PredicateOperator
import com.dev.usdi_wallet.databinding.ItemClaimCheckBinding

class ClaimCheckAdapter(
    private val onChecked: (Int, Boolean) -> Unit,
    private val onConstraintChanged: (Int, String) -> Unit,
    private val onPredicateOperatorChanged: (Int, PredicateOperator?) -> Unit,
    private val onPredicateValueChanged: (Int, String) -> Unit,
) : ListAdapter<ClaimCheckItem, ClaimCheckAdapter.ClaimViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClaimViewHolder {
        val binding = ItemClaimCheckBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ClaimViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClaimViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ClaimViewHolder(
        private val binding: ItemClaimCheckBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(claimCheckItem: ClaimCheckItem) {
            binding.cbClaim.text = claimCheckItem.name
            binding.cbClaim.isChecked = claimCheckItem.checked
            binding.cbClaim.setOnCheckedChangeListener(null)
            binding.tvType.text = claimCheckItem.type.name.lowercase()
            binding.constraintGroup.isVisible = claimCheckItem.checked
            binding.cbClaim.setOnCheckedChangeListener { _, checked ->
                onChecked(layoutPosition, checked)
            }

            if (claimCheckItem.type == ClaimType.NUMBER) {
                binding.tilConstraint.isVisible = false
                binding.predicateGroup.isVisible = claimCheckItem.checked

                val operators = PredicateOperator.entries.toTypedArray().map { it.symbol }
                binding.spinnerOperator.adapter = ArrayAdapter(
                    binding.root.context,
                    android.R.layout.simple_spinner_dropdown_item,
                    operators
                )
                binding.spinnerOperator.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val op = when (position) {
                            0 -> null
                            1 -> PredicateOperator.GREATER_THAN_OR_EQUAL
                            2 -> PredicateOperator.GREATER_THAN
                            3 -> PredicateOperator.LESS_THAN_OR_EQUAL
                            4 -> PredicateOperator.LESS_THAN
                            else -> null
                        }
                        onPredicateOperatorChanged(position, op)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
                binding.etPredicateValue.doAfterTextChanged { text ->
                    onPredicateValueChanged(layoutPosition, text?.toString() ?: "")
                }
            } else {
                binding.predicateGroup.isVisible = false
                binding.tilConstraint.isVisible = claimCheckItem.checked
                binding.etConstraint.doAfterTextChanged { text ->
                    onConstraintChanged(layoutPosition, text?.toString() ?: "")
                }
            }
        }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<ClaimCheckItem>() {
            override fun areItemsTheSame(oldItem: ClaimCheckItem, newItem: ClaimCheckItem): Boolean =
                oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: ClaimCheckItem, newItem: ClaimCheckItem): Boolean =
                oldItem == newItem
        }
    }
}
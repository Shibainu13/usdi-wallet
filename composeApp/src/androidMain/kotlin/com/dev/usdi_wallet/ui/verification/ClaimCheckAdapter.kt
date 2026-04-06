package com.dev.usdi_wallet.ui.verification

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ClaimViewHolder(ItemClaimCheckBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ClaimViewHolder, position: Int) =
        holder.bind(getItem(position), position)

    inner class ClaimViewHolder(
        private val binding: ItemClaimCheckBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var constraintWatcher: TextWatcher? = null
        private var predicateValueWatcher: TextWatcher? = null

        fun bind(item: ClaimCheckItem, position: Int) {
            // Remove all stale listeners before touching any view
            binding.cbClaim.setOnCheckedChangeListener(null)
            binding.spinnerOperator.onItemSelectedListener = null
            constraintWatcher?.let { binding.etConstraint.removeTextChangedListener(it) }
            predicateValueWatcher?.let { binding.etPredicateValue.removeTextChangedListener(it) }
            constraintWatcher = null
            predicateValueWatcher = null

            // Static values
            binding.cbClaim.text = item.name
            binding.tvType.text = item.type.name.lowercase()
            binding.cbClaim.isChecked = item.checked
            binding.constraintGroup.isVisible = item.checked

            binding.cbClaim.setOnCheckedChangeListener { _, checked ->
                onChecked(layoutPosition, checked)
            }

            if (item.type == ClaimType.NUMBER) {
                binding.tilConstraint.isVisible = false
                binding.predicateGroup.isVisible = item.checked

                val operators = PredicateOperator.entries.map { it.symbol }
                binding.spinnerOperator.adapter = ArrayAdapter(
                    binding.root.context,
                    android.R.layout.simple_spinner_dropdown_item,
                    operators,
                )
                binding.spinnerOperator.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?, v: View?, pos: Int, id: Long,
                        ) {
                            onPredicateOperatorChanged(layoutPosition, PredicateOperator.entries.getOrNull(pos))
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }

                if (binding.etPredicateValue.text.toString() != item.predicateValue) {
                    binding.etPredicateValue.setText(item.predicateValue)
                }
                predicateValueWatcher = makeWatcher { onPredicateValueChanged(layoutPosition, it) }
                binding.etPredicateValue.addTextChangedListener(predicateValueWatcher)

            } else {
                binding.predicateGroup.isVisible = false
                binding.tilConstraint.isVisible = item.checked
                binding.tilConstraint.hint = when (item.type) {
                    ClaimType.BOOLEAN -> "true or false (optional)"
                    else              -> "Must equal (optional)"
                }

                val current = item.constraint ?: ""
                if (binding.etConstraint.text.toString() != current) {
                    binding.etConstraint.setText(current)
                }
                constraintWatcher = makeWatcher { onConstraintChanged(layoutPosition, it) }
                binding.etConstraint.addTextChangedListener(constraintWatcher)
            }
        }
    }

    private fun makeWatcher(onChanged: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) { onChanged(s?.toString() ?: "") }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<ClaimCheckItem>() {
            override fun areItemsTheSame(old: ClaimCheckItem, new: ClaimCheckItem) = old.name == new.name
            override fun areContentsTheSame(old: ClaimCheckItem, new: ClaimCheckItem) =
                old.checked == new.checked && old.name == new.name
        }
    }
}
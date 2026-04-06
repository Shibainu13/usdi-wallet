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
import com.dev.usdi_wallet.databinding.ItemManualClaimBinding

class ManualClaimAdapter(
    private val onNameChanged: (String, String) -> Unit,
    private val onTypeChanged: (String, ClaimType) -> Unit,
    private val onConstraintChanged: (String, String) -> Unit,
    private val onPredicateOperatorChanged: (String, PredicateOperator?) -> Unit,
    private val onPredicateValueChanged: (String, String) -> Unit,
    private val onRemove: (String) -> Unit,
) : ListAdapter<ManualClaimRow, ManualClaimAdapter.ManualClaimViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ManualClaimViewHolder(ItemManualClaimBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ManualClaimViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ManualClaimViewHolder(
        private val binding: ItemManualClaimBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var nameWatcher: TextWatcher? = null
        private var constraintWatcher: TextWatcher? = null
        private var predicateValueWatcher: TextWatcher? = null

        fun bind(row: ManualClaimRow) {
            // Remove all stale listeners before touching any view
            binding.spinnerType.onItemSelectedListener = null
            binding.spinnerOperator.onItemSelectedListener = null
            nameWatcher?.let { binding.etClaimName.removeTextChangedListener(it) }
            constraintWatcher?.let { binding.etConstraint.removeTextChangedListener(it) }
            predicateValueWatcher?.let { binding.etPredicateValue.removeTextChangedListener(it) }
            nameWatcher = null
            constraintWatcher = null
            predicateValueWatcher = null

            // Claim name
            if (binding.etClaimName.text.toString() != row.name) {
                binding.etClaimName.setText(row.name)
            }
            nameWatcher = makeWatcher { onNameChanged(row.id, it) }
            binding.etClaimName.addTextChangedListener(nameWatcher)

            // Type spinner
            val types = ClaimType.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
            binding.spinnerType.adapter = ArrayAdapter(
                binding.root.context,
                android.R.layout.simple_spinner_dropdown_item,
                types,
            )
            binding.spinnerType.setSelection(ClaimType.entries.indexOf(row.type))
            binding.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    onTypeChanged(row.id, ClaimType.entries[pos])
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

            if (row.type == ClaimType.NUMBER) {
                binding.tilConstraint.isVisible = false
                binding.predicateGroup.isVisible = true

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
                            onPredicateOperatorChanged(row.id, PredicateOperator.entries.getOrNull(pos))
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }

                if (binding.etPredicateValue.text.toString() != row.predicateValue) {
                    binding.etPredicateValue.setText(row.predicateValue)
                }
                predicateValueWatcher = makeWatcher { onPredicateValueChanged(row.id, it) }
                binding.etPredicateValue.addTextChangedListener(predicateValueWatcher)

            } else {
                binding.predicateGroup.isVisible = false
                binding.tilConstraint.isVisible = true
                binding.tilConstraint.hint = when (row.type) {
                    ClaimType.BOOLEAN -> "true or false (optional)"
                    else              -> "Must equal (optional)"
                }

                if (binding.etConstraint.text.toString() != row.constraint) {
                    binding.etConstraint.setText(row.constraint)
                }
                constraintWatcher = makeWatcher { onConstraintChanged(row.id, it) }
                binding.etConstraint.addTextChangedListener(constraintWatcher)
            }

            binding.btnRemove.setOnClickListener { onRemove(row.id) }
        }
    }

    private fun makeWatcher(onChanged: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) { onChanged(s?.toString() ?: "") }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<ManualClaimRow>() {
            override fun areItemsTheSame(old: ManualClaimRow, new: ManualClaimRow) = old.id == new.id
            override fun areContentsTheSame(old: ManualClaimRow, new: ManualClaimRow) =
                old.type == new.type && old.name == new.name
        }
    }
}
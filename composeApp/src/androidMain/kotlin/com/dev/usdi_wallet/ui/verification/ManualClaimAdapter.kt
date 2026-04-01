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
import com.dev.usdi_wallet.databinding.ItemManualClaimBinding
import com.dev.usdi_wallet.credential.ClaimType
import com.dev.usdi_wallet.credential.PredicateOperator

class ManualClaimAdapter(
    private val onNameChanged: (String, String) -> Unit,
    private val onTypeChanged: (String, ClaimType) -> Unit,
    private val onConstraintChanged: (String, String) -> Unit,
    private val onPredicateOperatorChanged: (String, PredicateOperator?) -> Unit,
    private val onPredicateValueChanged: (String, String) -> Unit,
    private val onRemove: (String) -> Unit,
) : ListAdapter<ManualClaimRow, ManualClaimAdapter.ManualClaimViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManualClaimViewHolder {
        val binding = ItemManualClaimBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ManualClaimViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ManualClaimViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ManualClaimViewHolder(private val binding: ItemManualClaimBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ManualClaimRow) {
            // Claim name
            if (binding.etClaimName.text.toString() != row.name) {
                binding.etClaimName.setText(row.name)
            }
            binding.etClaimName.doAfterTextChanged { text ->
                onNameChanged(row.id, text?.toString() ?: "")
            }

            // Type spinner — String / Number / Boolean
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

            // NUMBER: predicate operator + value
            if (row.type == ClaimType.NUMBER) {
                binding.tilConstraint.isVisible  = false
                binding.predicateGroup.isVisible = true

                val operators = listOf("= (equals)", "≥", "≤", ">", "<")
                binding.spinnerOperator.adapter = ArrayAdapter(
                    binding.root.context,
                    android.R.layout.simple_spinner_dropdown_item,
                    operators,
                )
                binding.spinnerOperator.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        val op = when (pos) {
                            0 -> null
                            1 -> PredicateOperator.GREATER_THAN_OR_EQUAL
                            2 -> PredicateOperator.LESS_THAN_OR_EQUAL
                            3 -> PredicateOperator.GREATER_THAN
                            4 -> PredicateOperator.LESS_THAN
                            else -> null
                        }
                        onPredicateOperatorChanged(row.id, op)
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
                binding.etPredicateValue.doAfterTextChanged { text ->
                    onPredicateValueChanged(row.id, text?.toString() ?: "")
                }
            } else {
                // STRING / BOOLEAN: optional equality constraint
                binding.predicateGroup.isVisible = false
                binding.tilConstraint.isVisible  = true
                binding.etConstraint.hint = when (row.type) {
                    ClaimType.BOOLEAN -> "true or false (optional)"
                    else              -> "Must equal (optional)"
                }
                binding.etConstraint.doAfterTextChanged { text ->
                    onConstraintChanged(row.id, text?.toString() ?: "")
                }
            }

            binding.btnRemove.setOnClickListener { onRemove(row.id) }
        }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<ManualClaimRow>() {
            override fun areItemsTheSame(old: ManualClaimRow, new: ManualClaimRow) = old.id == new.id
            override fun areContentsTheSame(old: ManualClaimRow, new: ManualClaimRow) = old == new
        }
    }
}
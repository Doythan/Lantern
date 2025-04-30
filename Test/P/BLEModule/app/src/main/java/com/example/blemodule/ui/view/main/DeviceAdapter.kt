package com.example.blemodule.ui.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.blemodule.data.model.BleDevice
import com.example.blemodule.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onClick: (BleDevice) -> Unit
) : ListAdapter<BleDevice, DeviceAdapter.DeviceVH>(Diff) {

    override fun onCreateViewHolder(p: ViewGroup, vType: Int) =
        DeviceVH(ItemDeviceBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: DeviceVH, pos: Int) = h.bind(getItem(pos))

    inner class DeviceVH(private val b: ItemDeviceBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(item: BleDevice) = with(b) {
            tvName.text = item.name ?: "Unknown"
            tvAddr.text = item.address
            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<BleDevice>() {
            override fun areItemsTheSame(o: BleDevice, n: BleDevice) = o.address == n.address
            override fun areContentsTheSame(o: BleDevice, n: BleDevice) = o == n
        }
    }
}
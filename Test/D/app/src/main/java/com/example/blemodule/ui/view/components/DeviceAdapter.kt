package com.example.blemodule.ui.view.components

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.blemodule.R
import com.example.blemodule.utils.PermissionHelper

class DeviceAdapter : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val deviceList = mutableListOf<BluetoothDevice>()

    fun addDevice(device: BluetoothDevice) {
        if (deviceList.none { it.address == device.address }) {
            deviceList.add(device)
            notifyItemInserted(deviceList.lastIndex)
        }
    }

    fun clearDevices() {
        deviceList.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(deviceList[position])
    }

    override fun getItemCount(): Int = deviceList.size

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceNameTextView: TextView = itemView.findViewById(R.id.textDeviceName)
        private val deviceAddressTextView: TextView = itemView.findViewById(R.id.textDeviceAddress)

        fun bind(device: BluetoothDevice) {
            val context = itemView.context

            if (PermissionHelper.hasBluetoothConnectPermission(context)) {
                deviceNameTextView.text = device.name ?: "Unknown Device"
                deviceAddressTextView.text = device.address
            } else {
                deviceNameTextView.text = "Permission Required"
                deviceAddressTextView.text = "Permission Required"
            }
        }
    }
}

package com.example.boschlaser.platform

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.boschlaser.R

/** Android's candidate chooser only reports an index; connection remains with the host. */
internal fun showBleDevicePicker(context: Context, labels: List<String>, onSelected: (Int) -> Unit): AlertDialog =
    AlertDialog.Builder(context)
        .setTitle(R.string.device_choose_title)
        .setItems(labels.toTypedArray()) { _, index -> onSelected(index) }
        .setNegativeButton(R.string.action_cancel, null)
        .show()

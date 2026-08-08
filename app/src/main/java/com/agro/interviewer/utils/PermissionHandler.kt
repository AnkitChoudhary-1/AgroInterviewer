package com.agro.interviewer.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.RECORD_AUDIO
)

fun Context.hasAllPermissions(): Boolean {
    return REQUIRED_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun rememberPermissionState(
    onGranted: () -> Unit,
    onDenied: () -> Unit
): PermissionState {
    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionsDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            permissionsGranted = true
            onGranted()
        } else {
            permissionsDenied = true
            onDenied()
        }
    }

    return remember {
        PermissionState(
            requestPermissions = { launcher.launch(REQUIRED_PERMISSIONS) },
            isGranted = permissionsGranted,
            isDenied = permissionsDenied
        )
    }
}

data class PermissionState(
    val requestPermissions: () -> Unit,
    val isGranted: Boolean,
    val isDenied: Boolean
)

package net.donething.android.flowwall

// Created by Donething on 2017/09/09.

data class JSONResult(
        var success: Boolean = false,
        var code: Int = 0,
        var msg: String = "",
        var result: Any? = null
)
package com.example

object NodeBridge {
    init {
        try {
            System.loadLibrary("node")
            System.loadLibrary("native-lib")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var outputListener: ((String) -> Unit)? = null

    fun setOutputListener(listener: (String) -> Unit) {
        outputListener = listener
    }

    // Called from C++ via JNI
    @Suppress("unused")
    fun onOutput(output: String) {
        outputListener?.invoke(output)
    }

    external fun startNodeWithArguments(args: Array<String>): Int
    external fun sendInput(input: String)
}

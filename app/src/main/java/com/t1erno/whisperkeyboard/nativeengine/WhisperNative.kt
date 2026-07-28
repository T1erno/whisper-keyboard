package com.t1erno.whisperkeyboard.nativeengine

object WhisperNative {

    init {
        try {
            System.loadLibrary("whisper_native")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    /**
     * Initializes whisper context from GGML model file path.
     * Returns pointer handle on success, 0 on failure.
     */
    external fun initContext(modelPath: String): Long

    /**
     * Frees whisper context handle.
     */
    external fun freeContext(contextPtr: Long)

    /**
     * Transcribes 16kHz mono float PCM samples directly using loaded model context.
     */
    external fun transcribeData(
        contextPtr: Long,
        numThreads: Int,
        samples: FloatArray,
        language: String
    ): String
}

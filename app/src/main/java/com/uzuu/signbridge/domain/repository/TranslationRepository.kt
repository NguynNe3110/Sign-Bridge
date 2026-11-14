package com.uzuu.signbridge.domain.repository

import com.uzuu.signbridge.domain.model.*

interface TranslationRepository {
    suspend fun checkHealth(): Boolean
    suspend fun expand(context: ServiceContext, chips: List<String>, previousIncoming: String?): String
    suspend fun recognizeSign(
        context: ServiceContext, frames: List<SignFrame>,
        personalChips: List<String>, previousIncoming: String?,
    ): SignResult
    /** Mục 4.2a: ký hiệu đã nhận diện trên máy → server kiểm tra và ghép câu. */
    suspend fun composeSignGlosses(
        context: ServiceContext, glosses: List<GlossPrediction>,
        personalChips: List<String>, previousIncoming: String?,
    ): SignResult
    suspend fun summarize(context: ServiceContext, audio: RecordedAudio, previousOutgoing: String?): SpeechSummary
}


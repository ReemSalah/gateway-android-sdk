package com.mastercard.gateway.threeds2

import java.io.Serializable

interface ChallengeStatusReceiver : Serializable {
    fun completed(completionEvent: CompletionEvent)
    fun cancelled()
    fun timedout()
    fun protocolError(protocolErrorEvent: ProtocolErrorEvent)
    fun runtimeError(runtimeErrorEvent: RuntimeErrorEvent)
}
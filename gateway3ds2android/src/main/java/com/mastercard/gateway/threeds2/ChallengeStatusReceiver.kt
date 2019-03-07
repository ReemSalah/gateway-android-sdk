package com.mastercard.gateway.threeds2

interface ChallengeStatusReceiver {
    fun completed(completionEvent: CompletionEvent)
    fun cancelled()
    fun timedout()
    fun protocolError(protocolErrorEvent: ProtocolErrorEvent)
    fun runtimeError(runtimeErrorEvent: RuntimeErrorEvent)
}
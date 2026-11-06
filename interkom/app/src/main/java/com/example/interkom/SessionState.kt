package com.example.interkom
import kotlinx.coroutines.flow.MutableStateFlow
object SessionState {
 val status = MutableStateFlow("Bağlı değil")
 val code = MutableStateFlow("")
 val count = MutableStateFlow(0)
 val live = MutableStateFlow(false)
 val mic = MutableStateFlow(false)
 val error = MutableStateFlow("")
 fun reset() { status.value="Bağlı değil"; code.value=""; count.value=0; live.value=false; mic.value=false }
}

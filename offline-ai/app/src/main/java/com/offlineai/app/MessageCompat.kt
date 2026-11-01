package com.offlineai.app

import com.google.ai.edge.litertlm.Message

internal fun Message.isNotEmpty(): Boolean = toString().isNotEmpty()

package com.example.data.models

import com.example.data.Room
import com.example.other.Constants.TYPE_PHASE_CHANGE

data class PhaseChange(
    var phase:Room.Phase?=null,
    var time:Long,
    val drawingPlayer:String?=null,
):BaseModel(TYPE_PHASE_CHANGE)

package com.example.data.models

import com.example.other.Constants.TYPE_CHAT_MASSAGE

data class ChatMassage(
    val from:String,
    val roomName:String,
    val message:String,
    val timestamp: Long
):BaseModel(TYPE_CHAT_MASSAGE)

package com.example.data.models

import com.example.other.Constants.TYPE_JOIN_ROOM_ERROR

data class JoinRoomHandShake(
    val userName:String,
    val roomName:String,
    val clientId:String
):BaseModel(TYPE_JOIN_ROOM_ERROR)

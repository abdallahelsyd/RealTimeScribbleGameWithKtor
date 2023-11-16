package com.example.data.models

import com.example.other.Constants.TYPE_PLAYER_LIST

data class PlayersList(
    val players:List<PlayerData>
):BaseModel(TYPE_PLAYER_LIST)
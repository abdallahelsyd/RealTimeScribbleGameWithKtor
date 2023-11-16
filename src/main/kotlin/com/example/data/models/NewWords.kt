package com.example.data.models

import com.example.other.Constants.TYPE_NEW_WORD

data class NewWords(
    val newWords: List<String>
):BaseModel(TYPE_NEW_WORD)

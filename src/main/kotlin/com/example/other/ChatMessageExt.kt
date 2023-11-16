package com.example.other

import com.example.data.models.ChatMassage

fun ChatMassage.matchesWord(word:String):Boolean{
    return message.lowercase().trim()==word.lowercase().trim()
}
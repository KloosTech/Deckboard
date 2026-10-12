package de.kloostech.deckboard

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
package com.ankitsilwal.buttonhatesyou.avatar

// What the button is wearing, as plain values a renderer can use. Built from
// the equipped item ids by the catalog (economy.Catalog.lookOf). Each string is
// an item's `look` (e.g. "hoodie", "cap", "glasses"), or null for nothing.
data class Look(
    val color: Long = 0xFFE2674F, // body, ARGB
    val dark: Long = 0xFFB84A36, // body shadow / base
    val outfit: String? = null, // tee, hoodie, track, denim, varsity, blazer, leather, puffer
    val eyes: String? = null, // sleepy, googly, hearts, shades, stars, chill, glasses, aviators, visor
    val ears: String? = null, // bear, cat, bunny, devil, earbuds, headphones
    val scarf: String? = null, // red, sage, striped, rainbow, lanyard, bandana, chain
    val hat: String? = null, // beanie, party, top, crown, headband, cap, bucket, beret
)

// How the button feels and looks right now, on top of its outfit.
enum class Mood { Calm, Smug, Annoyed, Angry, Sad, Scared, Tired, Bored, Sleep, Happy, Worried, Closed }

data class Face(
    val mood: Mood = Mood.Calm,
    val sleepy: Boolean = false, // dark circles
    val dirty: Int = 0, // 0 clean, 1 grubby, 2 filthy (flies)
    val grime: Float = 0f, // 0..1 how much dirt shows
)

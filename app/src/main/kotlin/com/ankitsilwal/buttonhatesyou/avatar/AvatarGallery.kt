package com.ankitsilwal.buttonhatesyou.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A dev screen: every mood, need and outfit item on one scrolling grid.
// Not wired into the app; drop AvatarGallery() into any screen to check art.

private class Tile(val label: String, val look: Look, val face: Face = Face(), val legs: Boolean = false)

private val COLORS = listOf(
    0xFFE2674F to 0xFFB84A36,
    0xFF6F9A6E to 0xFF4E7550,
    0xFF5B7FA8 to 0xFF3F6189,
    0xFFF0C36D to 0xFFC99A45,
    0xFF9A7BC4 to 0xFF6F55A0,
    0xFF3A3D47 to 0xFF23252C,
)

@Composable
fun AvatarGallery(modifier: Modifier = Modifier) {
    val base = Look()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier.fillMaxSize().background(Color(0xFFF4E7D4)),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        header("Sizes (tap to poke)")
        item(span = { GridItemSpan(maxLineSpan) }) { Sizes() }

        header("Moods")
        section(Mood.entries.map { Tile(it.name, base, Face(mood = it)) })

        header("Needs")
        section(
            listOf(
                Tile("sleepy", base, Face(sleepy = true)),
                Tile("grubby", base, Face(dirty = 1, grime = 0.4f)),
                Tile("filthy", base, Face(mood = Mood.Annoyed, dirty = 2, grime = 0.9f)),
                Tile("tired+dirty", base, Face(mood = Mood.Tired, sleepy = true, dirty = 2, grime = 0.7f)),
                Tile("legs", base, legs = true),
            ),
        )

        header("Colours")
        section(COLORS.map { (c, d) -> Tile("#%06X".format(c and 0xFFFFFF), Look(color = c, dark = d)) })

        header("Ears")
        section(Wardrobe.EARS.map { Tile(it, base.copy(ears = it)) })
        header("Eyes")
        section(Wardrobe.EYES.map { Tile(it, base.copy(eyes = it)) })
        header("Neck")
        section(Wardrobe.SCARVES.map { Tile(it, base.copy(scarf = it)) })
        header("Hats")
        section(Wardrobe.HATS.map { Tile(it, base.copy(hat = it)) })
        header("Outfits")
        section(Wardrobe.OUTFITS.map { Tile(it, base.copy(outfit = it)) })

        header("Fits")
        section(
            listOf(
                Tile("street", Look(color = 0xFF5B7FA8, dark = 0xFF3F6189, outfit = "hoodie", hat = "cap", eyes = "shades", scarf = "chain", ears = "earbuds"), legs = true),
                Tile("cozy", Look(outfit = "puffer", hat = "beanie", scarf = "striped", ears = "bear"), Face(mood = Mood.Happy)),
                Tile("boss", Look(color = 0xFF3A3D47, dark = 0xFF23252C, outfit = "blazer", hat = "top", eyes = "aviators"), Face(mood = Mood.Smug)),
                Tile("rock", Look(outfit = "leather", eyes = "visor", ears = "devil", scarf = "bandana"), Face(mood = Mood.Angry)),
                Tile("campus", Look(color = 0xFFF0C36D, dark = 0xFFC99A45, outfit = "varsity", hat = "bucket", eyes = "glasses", scarf = "lanyard", ears = "headphones")),
                Tile("art kid", Look(color = 0xFF9A7BC4, dark = 0xFF6F55A0, outfit = "denim", hat = "beret", eyes = "stars", ears = "cat"), Face(mood = Mood.Worried)),
                Tile("party", Look(outfit = "tee", hat = "party", eyes = "googly", scarf = "rainbow", ears = "bunny"), Face(mood = Mood.Scared)),
                Tile("royal", Look(color = 0xFF6F9A6E, dark = 0xFF4E7550, outfit = "track", hat = "crown", eyes = "hearts", scarf = "red"), Face(mood = Mood.Sad)),
                Tile("gym", Look(outfit = "track", hat = "headband", eyes = "chill", scarf = "sage"), Face(mood = Mood.Bored, sleepy = true)),
            ),
        )
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(40.dp)) }
    }
}

private fun LazyGridScope.header(text: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A3428), modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    }
}

private fun LazyGridScope.section(tiles: List<Tile>) {
    items(tiles) { TileView(it) }
}

@Composable
private fun TileView(tile: Tile, size: Dp = 64.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        // room for hats and ears above, legs below
        Box(Modifier.padding(top = size * 0.5f, bottom = size * 0.26f, start = size * 0.12f, end = size * 0.12f)) {
            Avatar(tile.look, tile.face, size, legs = tile.legs)
        }
        Text(tile.label, fontSize = 12.sp, color = Color(0xFF8A6F5E))
    }
}

@Composable
private fun Sizes() {
    var pokes by remember { mutableIntStateOf(0) }
    val look = Look(outfit = "hoodie", hat = "beanie", scarf = "red")
    Column {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (s in listOf(40.dp, 64.dp, 110.dp)) {
                Box(Modifier.padding(top = s * 0.5f, bottom = s * 0.26f)) {
                    Avatar(look, Face(mood = if (pokes % 2 == 1) Mood.Annoyed else Mood.Calm), s, onPoke = { pokes++ })
                }
            }
        }
        Box(Modifier.padding(top = 110.dp, bottom = 58.dp, start = 24.dp)) {
            Avatar(look, Face(mood = if (pokes % 2 == 1) Mood.Angry else Mood.Smug), 220.dp, onPoke = { pokes++ })
        }
        Text("pokes: $pokes", fontSize = 12.sp, color = Color(0xFF8A6F5E))
    }
}

@Preview(widthDp = 400, heightDp = 900)
@Composable
private fun AvatarGalleryPreview() {
    AvatarGallery()
}

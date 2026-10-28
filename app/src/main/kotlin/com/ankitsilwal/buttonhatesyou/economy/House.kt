package com.ankitsilwal.buttonhatesyou.economy

import kotlinx.serialization.Serializable

// The button's house and its fridge (a port of src/economy/house.js). Prices
// are on the same scale as the outfits; food is cheap because it's a need.

data class Spot(val id: String, val name: String)

// A room you can decorate. `required` spots always hold a piece (it can be
// swapped, never taken down): the bedroom's bed and bedside lamp.
data class RoomInfo(
    val id: String,
    val name: String,
    val icon: String,
    val spots: List<String>,
    val required: List<String> = emptyList(),
)

// `kind`: "wall" (wallpaper), "floor" or "furniture". `rooms`: where it can go.
// `spots`: where a piece of furniture fits.
data class HouseItem(
    override val id: String,
    val kind: String,
    val name: String,
    override val price: Price? = null,
    val spots: List<String> = emptyList(),
    val rooms: List<String> = House.ALL,
) : Priced

// One room as saved in the wallet: wallpaper, floor and furniture by spot.
// A blank wall/floor means "not saved" and is filled in by Rules.normalize.
@Serializable
data class Room(
    val wall: String = "",
    val floor: String = "",
    val place: Map<String, String> = emptyMap(),
)

// A food: `hunger`/`energy` are how much it restores, `fx` a funny side effect.
data class Food(
    override val id: String,
    val name: String,
    override val price: Price,
    val hunger: Int,
    val energy: Int,
    val fx: String? = null,
    val quip: String,
) : Priced

// A paid way to get him out of bed, with some energy back.
data class WakeOption(
    override val id: String,
    val name: String,
    val icon: String,
    override val price: Price,
    val energy: Int,
    val quip: String,
) : Priced

object House {
    val SPOTS = listOf(
        Spot("ceiling", "Ceiling"),
        Spot("wall-left", "Wall, left"),
        Spot("wall-right", "Wall, right"),
        Spot("bed", "Bed"),
        Spot("nightstand", "Nightstand"),
        Spot("counter", "Counter"),
        Spot("floor-left", "Floor, left"),
        Spot("floor-right", "Floor, right"),
        Spot("corner", "Corner"),
        Spot("rug", "Rug"),
    )
    val SPOT: Map<String, Spot> = SPOTS.associateBy { it.id }

    val ROOMS: Map<String, RoomInfo> = linkedMapOf(
        "living" to RoomInfo("living", "Living Room", "🛋️", listOf("ceiling", "wall-left", "wall-right", "corner", "floor-left", "floor-right", "rug")),
        "kitchen" to RoomInfo("kitchen", "Kitchen", "🍳", listOf("ceiling", "wall-left", "wall-right", "counter", "floor-left", "floor-right", "rug")),
        "bedroom" to RoomInfo(
            "bedroom", "Bedroom", "🛏️",
            listOf("ceiling", "wall-left", "wall-right", "bed", "nightstand", "corner", "floor-left", "floor-right", "rug"),
            required = listOf("bed", "nightstand"),
        ),
    )
    val ROOM_IDS: List<String> = ROOMS.keys.toList()

    val ALL = listOf("living", "kitchen", "bedroom")
    private val LB = listOf("living", "bedroom")
    private val BED = listOf("bedroom")
    private val KIT = listOf("kitchen")

    private fun coins(n: Int) = Price(coins = n)
    private fun gems(n: Int) = Price(diamonds = n)
    private fun wall(id: String, name: String, price: Price? = null, rooms: List<String> = ALL) = HouseItem(id, "wall", name, price, rooms = rooms)
    private fun floor(id: String, name: String, price: Price? = null, rooms: List<String> = ALL) = HouseItem(id, "floor", name, price, rooms = rooms)
    private fun piece(id: String, name: String, spots: List<String>, price: Price?, rooms: List<String>) = HouseItem(id, "furniture", name, price, spots, rooms)
    private val WALLS = listOf("wall-left", "wall-right")
    private val FLOORS = listOf("floor-left", "floor-right")

    val HOUSE_ITEMS: List<HouseItem> = listOf(
        wall("wall-cozy", "Cozy Cream"),
        wall("wall-mint", "Mint Stripes", coins(300)),
        wall("wall-candy", "Candy Shop", coins(500)),
        wall("wall-beach", "Beach House", coins(900)),
        wall("wall-city", "Night City", coins(1200)),
        wall("wall-gamer", "Gamer RGB", coins(1800)),
        wall("wall-space", "Space Station", gems(8)),
        wall("wall-loft", "Brick Loft", coins(1000)),
        wall("wall-studio", "Dark Studio", coins(1400)),
        wall("wall-graffiti", "Street Art", coins(1600)),
        // the kitchen's own
        wall("wall-kitchen-tile", "Subway Tile", rooms = KIT),
        wall("wall-kitchen-sage", "Sage Tile", coins(400), KIT),
        wall("wall-kitchen-sun", "Butter Yellow", coins(500), KIT),
        wall("wall-kitchen-noir", "Black Tile", coins(1100), KIT),
        // the bedroom's own
        wall("wall-bedroom-blue", "Midnight Blue", rooms = BED),
        wall("wall-bedroom-plum", "Plum Velvet", coins(500), BED),
        wall("wall-bedroom-forest", "Forest Green", coins(600), BED),
        wall("wall-bedroom-galaxy", "Galaxy Ceiling", gems(8), BED),

        floor("floor-oak", "Oak Boards"),
        floor("floor-carpet", "Plush Carpet", coins(300)),
        floor("floor-checker", "Diner Checker", coins(450)),
        floor("floor-bamboo", "Bamboo Mat", coins(600)),
        floor("floor-marble", "Marble", coins(1200)),
        floor("floor-neon", "Neon Grid", gems(6)),
        floor("floor-concrete", "Polished Concrete", coins(700)),
        floor("floor-walnut", "Dark Walnut", coins(900)),
        floor("floor-kitchen-checker", "Kitchen Checker", rooms = KIT),
        floor("floor-kitchen-terrazzo", "Terrazzo", coins(800), KIT),
        floor("floor-kitchen-gold", "Gold Tiles", gems(6), KIT),
        floor("floor-bedroom-rug", "Plum Carpet", coins(500), BED),

        piece("bed-basic", "Old Mattress", listOf("bed"), null, BED),
        piece("bed-cozy", "Cozy Bed", listOf("bed"), coins(1200), BED),
        piece("bed-cloud", "Cloud Bed", listOf("bed"), gems(12), BED),
        piece("bed-loft", "Loft Bed", listOf("bed"), gems(14), BED),

        // bedside lamps: there is always one, it is how he goes to sleep
        piece("lamp-bedside", "Bedside Lamp", listOf("nightstand"), null, BED),
        piece("nightstand-modern", "Modern Nightstand", listOf("nightstand"), coins(450), BED),

        piece("lamp-arc", "Arc Lamp", listOf("corner"), coins(600), LB),
        piece("lamp-lava", "Lava Lamp", FLOORS, coins(900), LB),
        piece("lamp-pendant", "Pendant Lamp", listOf("ceiling"), coins(400), ALL),
        piece("lights-fairy", "Fairy Lights", listOf("ceiling"), coins(500), LB),
        piece("disco-ball", "Disco Ball", listOf("ceiling"), coins(2500), listOf("living")),

        piece("plant-monstera", "Monstera", listOf("corner", "floor-left", "floor-right"), coins(400), ALL),
        piece("plant-cactus", "Grumpy Cactus", FLOORS, coins(150), ALL),
        piece("fish-bowl", "Fish Bowl", FLOORS, coins(1500), ALL),
        piece("beanbag", "Beanbag", listOf("floor-right", "floor-left"), coins(800), LB),
        piece("tv-retro", "Retro TV", listOf("floor-right", "floor-left"), coins(2000), LB),

        piece("rug-round", "Round Rug", listOf("rug"), coins(350), ALL),
        piece("rug-rainbow", "Rainbow Rug", listOf("rug"), gems(6), ALL),

        piece("poster-band", "Band Poster", WALLS, coins(150), LB),
        piece("clock-wall", "Wall Clock", WALLS, coins(250), ALL),
        piece("shelf-books", "Book Shelf", WALLS, coins(400), LB),
        piece("art-portrait", "Self Portrait", WALLS, coins(3000), ALL),
        piece("neon-sign", "Neon Sign", WALLS, gems(10), ALL),

        piece("poster-vinyl", "Album Poster", WALLS, coins(350), LB),
        piece("photo-wall", "Photo Wall", WALLS, coins(500), LB),
        piece("skate-decks", "Skate Decks", WALLS, coins(700), LB),
        piece("led-strip", "LED Strips", listOf("ceiling"), coins(900), ALL),
        piece("rug-shag", "Shag Rug", listOf("rug"), coins(900), LB),
        piece("plant-snake", "Snake Plant", listOf("corner", "floor-left", "floor-right"), coins(450), ALL),
        piece("mini-fridge", "Mini Fridge", FLOORS, coins(1100), LB),
        piece("record-player", "Record Player", FLOORS, coins(1400), LB),
        piece("guitar-stand", "Electric Guitar", listOf("corner", "floor-left", "floor-right"), coins(1600), LB),
        piece("chair-gaming", "Gaming Chair", FLOORS, coins(1800), LB),
        piece("desk-gaming", "Gaming Desk", listOf("floor-right", "floor-left"), coins(2800), LB),

        // the kitchen
        piece("toaster", "Toaster", listOf("counter"), coins(300), KIT),
        piece("fruit-bowl", "Fruit Bowl", listOf("counter"), coins(250), KIT),
        piece("coffee-machine", "Coffee Machine", listOf("counter"), coins(700), KIT),
        piece("wall-shelf-spices", "Spice Shelf", WALLS, coins(450), KIT),
        piece("pendant-kitchen", "Kitchen Pendant", listOf("ceiling"), coins(600), KIT),
        piece("stove-range", "Stove Range", FLOORS, coins(1500), KIT),
        piece("kitchen-table", "Table and Stools", listOf("floor-right", "floor-left"), coins(1200), KIT),
        piece("kitchen-island", "Kitchen Island", FLOORS, coins(2400), KIT),

        // the bedroom
        piece("string-lights", "String Lights", listOf("ceiling"), coins(700), BED),
        piece("floor-mirror", "Floor Mirror", listOf("floor-left", "floor-right", "corner"), coins(600), BED),
        piece("wardrobe-tall", "Tall Wardrobe", listOf("floor-right", "floor-left"), coins(1300), BED),
        piece("desk-study", "Study Desk", listOf("floor-right", "floor-left"), coins(1500), BED),
    )
    val HOUSE_ITEM: Map<String, HouseItem> = HOUSE_ITEMS.associateBy { it.id }

    // What each room starts with: free wallpaper and floor, and in the bedroom a
    // bed and a bedside lamp so nobody is ever stuck without a way to sleep.
    // DEFAULT_HOUSE is the living room (wallet.house); the others are wallet.rooms.
    val DEFAULT_HOUSE = Room("wall-cozy", "floor-oak", emptyMap())
    val DEFAULT_ROOMS: Map<String, Room> = linkedMapOf(
        "kitchen" to Room("wall-kitchen-tile", "floor-kitchen-checker", emptyMap()),
        "bedroom" to Room("wall-bedroom-blue", "floor-oak", linkedMapOf("bed" to "bed-basic", "nightstand" to "lamp-bedside")),
    )

    // A room's wallpaper, floor and furniture out of a wallet.
    fun roomOf(wallet: Wallet, room: String): Room =
        if (room == "living") wallet.house else wallet.rooms[room] ?: DEFAULT_ROOMS[room] ?: DEFAULT_HOUSE

    val FOODS = listOf(
        Food("food-cookie", "Browser Cookie", Price(coins = 15), 10, 8, "sugar", "Accept all cookies? Yes. Obviously."),
        Food("food-pixels", "Bowl of Pixels", Price(coins = 20), 20, 0, "glitch", "Crunchy. A bit low-res."),
        Food("food-bubble", "Bubble Wrap Chips", Price(coins = 25), 15, 5, "hiccup", "Pop. Pop. *hic*. Pop."),
        Food("food-usb", "USB Stick Sandwich", Price(coins = 30), 30, 5, null, "Took three tries to get it in. Classic."),
        Food("food-tacks", "Thumbtack Cereal", Price(coins = 35), 25, 10, null, "Crunchy. Pointy. Part of a balanced breakfast."),
        Food("food-cables", "Spaghetti Cables", Price(coins = 40), 35, 0, null, "Just like nonna used to tangle."),
        Food("food-battery", "AA Battery Smoothie", Price(coins = 50), 5, 35, "glow", "I can taste the voltage. Tingly."),
        Food("food-gluedog", "Hot Glue Hot Dog", Price(coins = 60), 40, 10, "hiccup", "My mouth is stuck. Worth it."),
        Food("food-floppy", "Floppy Disk Waffle", Price(diamonds = 1), 60, 60, "glow", "1.44 megabytes of pure flavor."),
    )
    val FOOD: Map<String, Food> = FOODS.associateBy { it.id }

    // Waking up by himself is free but leaves him as groggy as he was; these
    // wake him on the spot and give some energy back.
    val WAKE_OPTIONS = listOf(
        WakeOption("wake-splash", "Cold Water Splash", "💦", Price(coins = 25), 6, "AAAH. COLD. I am awake. I hate you."),
        WakeOption("wake-alarm", "Alarm Clock", "⏰", Price(coins = 40), 12, "BEEP BEEP BEEP. Fine. FINE. I am up."),
        WakeOption("wake-coffee", "Espresso Shot", "☕", Price(coins = 80), 22, "Everything is vibrating. In a good way."),
        WakeOption("wake-band", "Marching Band", "🥁", Price(diamonds = 1), 40, "WHY IS THERE A TUBA IN MY HOUSE. I feel amazing."),
    )
    val WAKE_OPTION: Map<String, WakeOption> = WAKE_OPTIONS.associateBy { it.id }
}

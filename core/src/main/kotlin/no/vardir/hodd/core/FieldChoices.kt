package no.vardir.hodd.core

import java.text.Normalizer
import java.util.Locale

/** Values stay human-readable in schema 1; choosing a preset is always explicit. */
data class FieldChoice(val value: String, val label: String = value, val keywords: String = "")
data class GamePlatform(val brand: String, val name: String, val aliases: String = "")

object FieldChoices {
    private fun values(vararg names: String) = names.map { FieldChoice(it) }
    val kinds = values("Book", "Game", "Movie", "Vinyl", "CD", "Comic", "Coin", "Other")
    val conditions = values("New / sealed", "Like new", "Very good", "Good", "Acceptable", "Poor", "For parts / incomplete")
    val bindings = values("Hardback", "Paperback", "Board book", "Spiral-bound", "Leather-bound", "Stapled", "Loose-leaf", "Digital")
    val editions = values("Standard", "Collector's edition", "Limited edition", "Deluxe edition", "Special edition", "Complete edition", "Game of the Year edition", "First edition", "Reissue")
    val resolutions = values("SD", "HD (720p)", "Full HD (1080p)", "4K UHD (2160p)", "8K UHD (4320p)")
    val materials = values("Gold", "Silver", "Copper", "Bronze", "Brass", "Nickel", "Copper-nickel", "Aluminum", "Zinc", "Steel", "Bimetallic", "Platinum", "Palladium")
    val countries: List<FieldChoice> by lazy {
        Locale.getISOCountries().map { code -> FieldChoice(Locale("", code).getDisplayCountry(Locale.ENGLISH), keywords = code) }.sortedBy { it.label }
    }
    val languages: List<FieldChoice> by lazy {
        val common = values("Norwegian", "Norwegian Bokmål", "Norwegian Nynorsk", "English", "Swedish", "Danish", "Finnish", "German", "French", "Spanish", "Italian", "Japanese", "Korean", "Chinese", "Multiple languages", "No linguistic content")
        val all = Locale.getISOLanguages().map { code -> FieldChoice(Locale(code).getDisplayLanguage(Locale.ENGLISH), keywords = code) }
        (common.map { choice -> choice.copy(keywords = all.firstOrNull { it.value == choice.value }?.keywords.orEmpty()) } + all).distinctBy { it.value.lowercase(Locale.ROOT) }
    }
    val publishers = values("Nintendo", "Sony Interactive Entertainment", "Microsoft", "Xbox Game Studios", "Sega", "Atari", "Bandai Namco", "Capcom", "Konami", "Square Enix", "Electronic Arts", "Ubisoft", "Bethesda", "Activision", "Blizzard Entertainment", "THQ Nordic", "Devolver Digital", "505 Games", "Warner Bros.", "Paramount", "Universal Pictures", "Sony Pictures", "Walt Disney", "Penguin Random House", "HarperCollins", "Hachette", "Macmillan", "Simon & Schuster", "Scholastic", "Bloomsbury", "Cappelen Damm", "Gyldendal", "Aschehoug", "Vigmostad & Bjørke", "Bonnier", "Marvel", "DC", "Dark Horse", "Image Comics", "IDW Publishing", "Kodansha", "Shueisha", "Viz Media", "Universal Music", "Sony Music", "Warner Music", "EMI", "Columbia", "RCA", "Atlantic", "Capitol", "Deutsche Grammophon", "Naxos", "ECM", "Nuclear Blast", "Sub Pop", "Domino", "4AD", "Beggars Banquet", "XL Recordings")
    val developers = values("Nintendo", "Game Freak", "HAL Laboratory", "Intelligent Systems", "Retro Studios", "Monolith Soft", "Sony Interactive Entertainment", "Naughty Dog", "Insomniac Games", "Santa Monica Studio", "Guerrilla Games", "Sucker Punch", "Polyphony Digital", "Xbox Game Studios", "Rare", "Obsidian Entertainment", "Playground Games", "Turn 10 Studios", "Mojang Studios", "Bethesda Game Studios", "id Software", "Sega", "Sonic Team", "Capcom", "Konami", "Square Enix", "FromSoftware", "Bandai Namco", "Ubisoft", "Electronic Arts", "BioWare", "DICE", "Respawn Entertainment", "Rockstar Games", "CD Projekt Red", "Larian Studios", "Valve", "Bungie", "Remedy Entertainment", "Supergiant Games", "Team Cherry", "ConcernedApe", "Toby Fox", "Atlus", "Koei Tecmo", "SNK")

    fun formats(kind: String): List<FieldChoice> = when (kind) {
        "Book" -> bindings + values("Audiobook", "E-book")
        "Comic" -> values("Single issue", "Trade paperback", "Hardcover", "Omnibus", "Manga volume", "Digital")
        "Game" -> values("Cartridge", "Game card", "Disc", "Digital", "Cassette", "Floppy disk", "UMD", "HuCard")
        "Movie" -> values("DVD", "Blu-ray", "4K UHD Blu-ray", "VHS", "Betamax", "LaserDisc", "Video CD", "HD DVD", "Digital", "Film reel", "UMD Video")
        "Vinyl" -> values("LP (12-inch)", "EP", "Single (7-inch)", "10-inch", "12-inch single", "Picture disc", "Box set")
        "CD" -> values("CD", "CD single", "CD-R", "SACD", "Enhanced CD", "Box set")
        "Coin" -> values("Circulation", "Commemorative", "Bullion", "Proof", "Uncirculated", "Token", "Medal", "Coin set")
        else -> values("Physical", "Digital", "Box set")
    }
    fun regions(kind: String, format: String): List<FieldChoice> = when {
        kind == "Game" -> values("Region-free", "Europe / PAL", "North America / NTSC-U", "Japan / NTSC-J", "Asia", "Australia / PAL", "South Korea", "China", "Brazil")
        format.contains("Blu-ray", true) -> values("Region-free", "Region A", "Region B", "Region C")
        format.equals("DVD", true) -> values("Region-free / 0", "Region 1", "Region 2", "Region 3", "Region 4", "Region 5", "Region 6")
        else -> values("Region-free", "PAL", "NTSC", "SECAM")
    }

    fun denominations(country: String): List<FieldChoice> = when (country) {
        "Norway", "Denmark" -> values("1 øre", "2 øre", "5 øre", "10 øre", "25 øre", "50 øre", "1 krone", "2 kroner", "5 kroner", "10 kroner", "20 kroner")
        "Sweden" -> values("1 öre", "2 öre", "5 öre", "10 öre", "25 öre", "50 öre", "1 krona", "2 kronor", "5 kronor", "10 kronor")
        "United States" -> values("Half cent", "1 cent", "2 cents", "3 cents", "5 cents", "10 cents", "20 cents", "25 cents", "50 cents", "1 dollar", "2.50 dollars", "5 dollars", "10 dollars", "20 dollars")
        "United Kingdom" -> values("Farthing", "Halfpenny", "Penny", "Threepence", "Sixpence", "Shilling", "Florin", "Half crown", "Crown", "Half sovereign", "Sovereign", "1 penny", "2 pence", "5 pence", "10 pence", "20 pence", "50 pence", "1 pound", "2 pounds", "5 pounds")
        "Germany", "France", "Italy", "Spain", "Portugal", "Netherlands", "Belgium", "Austria", "Finland", "Ireland", "Greece", "Luxembourg" -> values("1 euro cent", "2 euro cents", "5 euro cents", "10 euro cents", "20 euro cents", "50 euro cents", "1 euro", "2 euros")
        else -> values("0.01", "0.02", "0.05", "0.10", "0.20", "0.25", "0.50", "1", "2", "5", "10", "20", "25", "50", "100", "500", "1000")
    }

    val platforms: List<GamePlatform> = buildList {
        fun brand(name: String, vararg systems: Pair<String, String>) { systems.forEach { add(GamePlatform(name, it.first, it.second)) } }
        brand("Nintendo", "Nintendo Switch 2" to "switch2 ns2", "Nintendo Switch" to "switch oled lite ns", "Wii U" to "wiiu", "Wii" to "revolution", "Nintendo GameCube" to "gamecube gcn ngc", "Nintendo 64" to "n64", "Super Nintendo Entertainment System" to "snes super nes super famicom", "Nintendo Entertainment System" to "nes famicom", "Nintendo 3DS" to "3ds 2ds new 3ds", "Nintendo DS" to "ds dsi ds lite", "Game Boy Advance" to "gba sp micro", "Game Boy Color" to "gbc", "Game Boy" to "gb pocket light", "Virtual Boy" to "vb", "Game & Watch" to "game and watch")
        brand("Sony", "PlayStation 5" to "ps5 pro slim", "PlayStation 4" to "ps4 pro slim", "PlayStation 3" to "ps3 slim", "PlayStation 2" to "ps2 slim", "PlayStation" to "ps1 psx ps one", "PlayStation Vita" to "ps vita psv", "PlayStation Portable" to "psp psp go")
        brand("Microsoft", "Xbox Series X" to "xsx", "Xbox Series S" to "xss", "Xbox One" to "xbone one s one x", "Xbox 360" to "x360", "Xbox" to "original xbox")
        brand("Sega", "Dreamcast" to "dc", "Saturn" to "sega saturn", "Mega Drive / Genesis" to "mega drive genesis md", "Mega-CD / Sega CD" to "megacd sega cd", "32X" to "sega 32x", "Master System" to "sms sega mark iii", "Game Gear" to "gg", "SG-1000" to "sg1000", "Pico" to "sega pico")
        brand("Atari", "Atari 2600" to "vcs", "Atari 5200" to "5200", "Atari 7800" to "7800", "Atari Jaguar" to "jaguar", "Atari Jaguar CD" to "jaguar cd", "Atari Lynx" to "lynx", "Atari 8-bit" to "400 800 xl xe", "Atari ST" to "st ste falcon")
        brand("SNK", "Neo Geo AES" to "neo geo", "Neo Geo MVS" to "arcade", "Neo Geo CD" to "neogeo cd", "Neo Geo Pocket" to "ngp", "Neo Geo Pocket Color" to "ngpc")
        brand("NEC", "PC Engine / TurboGrafx-16" to "pce tg16", "PC Engine CD / TurboGrafx-CD" to "cd rom2", "SuperGrafx" to "sgx", "PC-FX" to "pcfx")
        brand("3DO", "3DO Interactive Multiplayer" to "panasonic goldstar sanyo")
        brand("Philips", "CD-i" to "cdi", "Videopac G7000" to "odyssey2 odyssey 2")
        brand("Magnavox", "Odyssey" to "magnavox odyssey")
        brand("Bandai", "WonderSwan" to "ws", "WonderSwan Color" to "wsc", "Pippin" to "apple pippin", "Playdia" to "playdia")
        brand("Nokia", "N-Gage" to "ngage qd")
        brand("Commodore", "Commodore 64" to "c64", "Commodore 128" to "c128", "VIC-20" to "vic20", "Amiga" to "a500 a600 a1200", "Amiga CD32" to "cd32", "CDTV" to "cdtv")
        brand("Amstrad", "Amstrad CPC" to "cpc", "Amstrad GX4000" to "gx4000")
        brand("Sinclair", "ZX Spectrum" to "spectrum zx", "ZX81" to "zx81")
        brand("Coleco", "ColecoVision" to "colecovision")
        brand("Mattel", "Intellivision" to "intellivision")
        brand("GCE", "Vectrex" to "vectrex")
        brand("Fairchild", "Channel F" to "ves channel f")
        brand("Milton Bradley", "Microvision" to "microvision")
        brand("PC / computer", "Windows" to "pc win steam epic gog", "Linux" to "pc linux", "macOS" to "mac macintosh os x", "MS-DOS" to "dos ibm pc", "MSX" to "msx msx2", "ZX Spectrum Next" to "spectrum next", "BBC Micro" to "acorn bbc", "Acorn Archimedes" to "risc os", "Sharp X68000" to "x68k", "NEC PC-98" to "pc98")
        brand("Mobile", "Android" to "google mobile", "iOS" to "apple iphone ipad ipados", "Java ME" to "j2me mobile phone", "Symbian" to "nokia s60")
        brand("Other systems", "Arcade" to "coin op mame", "Ouya" to "ouya", "Playdate" to "panic playdate", "Evercade" to "blaze evercade")
    }
    val brands get() = platforms.map { it.brand }.distinct().map { brand ->
        FieldChoice(brand, keywords = platforms.filter { it.brand == brand }.joinToString(" ") { "${it.name} ${it.aliases}" })
    }
    fun platformsFor(brand: String) = platforms.filter { it.brand.equals(brand, true) }
        .map { FieldChoice(it.name, keywords = "${it.brand} ${it.aliases}") }
    fun brandFor(platform: String): String? = platforms.firstOrNull { it.name.equals(platform.trim(), true) }?.brand
        ?: mapOf("Switch" to "Nintendo", "PS5" to "Sony", "PS4" to "Sony", "PS3" to "Sony", "PS2" to "Sony", "PS1" to "Sony", "PSP" to "Sony", "SNES" to "Nintendo", "NES" to "Nintendo", "GameCube" to "Nintendo", "N64" to "Nintendo").entries.firstOrNull { it.key.equals(platform.trim(), true) }?.value

    /** Existing values are retained verbatim, including older names and custom choices. */
    fun withExisting(choices: List<FieldChoice>, current: String, saved: List<String> = emptyList()): List<FieldChoice> =
        (listOf(current).filter { it.isNotBlank() }.map { value -> choices.firstOrNull { it.value == value } ?: FieldChoice(value) } + choices + saved.filter { it.isNotBlank() }.map { FieldChoice(it) })
            .distinctBy { it.value.lowercase(Locale.ROOT) }
    private fun searchable(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    fun search(choices: List<FieldChoice>, query: String): List<FieldChoice> {
        val terms = searchable(query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return choices.filter { choice -> terms.all { term -> searchable("${choice.label} ${choice.keywords}").contains(term) } }
    }
}

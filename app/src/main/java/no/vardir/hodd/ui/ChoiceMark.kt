package no.vardir.hodd.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import no.vardir.hodd.R
import no.vardir.hodd.core.FieldChoices

@Composable
internal fun ChoiceMark(field: String, value: String) {
    val logo = when (value) {
        "Nintendo" -> R.drawable.brand_nintendo
        "Sony" -> R.drawable.brand_sony
        "Microsoft", "Xbox Game Studios", "Xbox", "Xbox 360", "Xbox One", "Xbox Series X", "Xbox Series S" -> R.drawable.brand_xbox
        "Sega" -> R.drawable.brand_sega
        "Atari" -> R.drawable.brand_atari
        "NEC" -> R.drawable.brand_nec
        "Nokia" -> R.drawable.brand_nokia
        "Commodore" -> R.drawable.brand_commodore
        "Nintendo Switch" -> R.drawable.brand_nintendoswitch
        "Nintendo 3DS" -> R.drawable.brand_nintendo3ds
        "Nintendo GameCube" -> R.drawable.brand_nintendogamecube
        "Wii" -> R.drawable.brand_wii
        "Wii U" -> R.drawable.brand_wiiu
        "Sony Interactive Entertainment", "PlayStation", "PlayStation Portable" -> R.drawable.brand_playstation
        "PlayStation 2" -> R.drawable.brand_playstation2
        "PlayStation 3" -> R.drawable.brand_playstation3
        "PlayStation 4" -> R.drawable.brand_playstation4
        "PlayStation 5" -> R.drawable.brand_playstation5
        "PlayStation Vita" -> R.drawable.brand_playstationvita
        "Windows", "MS-DOS" -> R.drawable.brand_windows
        "Linux" -> R.drawable.brand_linux
        "macOS" -> R.drawable.brand_apple
        "Android" -> R.drawable.brand_android
        "iOS" -> R.drawable.brand_ios
        "Square Enix" -> R.drawable.brand_squareenix
        "Konami" -> R.drawable.brand_konami
        "Ubisoft" -> R.drawable.brand_ubisoft
        "Valve" -> R.drawable.brand_valve
        "Mojang Studios" -> R.drawable.brand_mojangstudios
        "Rockstar Games" -> R.drawable.brand_rockstargames
        "CD Projekt Red" -> R.drawable.brand_cdprojekt
        "Warner Bros." -> R.drawable.brand_warnerbros
        else -> when (if (field == "platform") FieldChoices.brandFor(value) else null) {
            "Nintendo" -> R.drawable.brand_nintendo
            "Sony" -> R.drawable.brand_playstation
            "Sega" -> R.drawable.brand_sega
            "Atari" -> R.drawable.brand_atari
            "NEC" -> R.drawable.brand_nec
            "Commodore" -> R.drawable.brand_commodore
            "Nokia" -> R.drawable.brand_nokia
            else -> null
        }
    }
    if (logo != null) Image(painterResource(logo), null, Modifier.size(width = 48.dp, height = 32.dp))
    else Box(Modifier.size(width = 48.dp, height = 32.dp), contentAlignment = Alignment.Center) { Icon(when (field) {
        "platform", "platformBrand", "developer" -> Icons.Outlined.SportsEsports
        "language" -> Icons.Outlined.Translate
        "country", "region" -> Icons.Outlined.Public
        "condition" -> Icons.Outlined.Verified
        "publisher", "label" -> Icons.Outlined.Business
        "binding", "format" -> Icons.Outlined.Category
        "releaseYear", "mintYear" -> Icons.Outlined.CalendarToday
        else -> Icons.Outlined.Label
    }, null, Modifier.size(24.dp), tint = Muted) }
}

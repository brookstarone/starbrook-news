package com.makrydensystems.news.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * StarBrook shapes. Cards are soft but not bubbly (16dp); chips and
 * pills are fully round; bottom sheets keep a wide 24dp top radius
 * so the sheet reads as a distinct layer over the feed.
 */
object StarBrookShapes {
    val card = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(999.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

    /** Top corners of a card only — for a 16:9 image that sits flush above card text. */
    val cardTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}

/** Material shapes wired from the StarBrook radii. */
val StarBrookMaterialShapes = Shapes(
    small = RoundedCornerShape(999.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

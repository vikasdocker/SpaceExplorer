package com.spaceexplorer

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView

class InfoPanelManager(
    private val context:   Context,
    private val card:      CardView,
    private val onDismiss: () -> Unit
) {
    private val tvName:   TextView  = card.findViewById(R.id.tvPlanetName)
    private val tvDesc:   TextView  = card.findViewById(R.id.tvPlanetDesc)
    private val tvOrbit:  TextView  = card.findViewById(R.id.tvPlanetOrbit)
    private val tvSize:   TextView  = card.findViewById(R.id.tvPlanetSize)
    private val ivColor:  ImageView = card.findViewById(R.id.ivPlanetColor)
    private val btnClose: View      = card.findViewById(R.id.btnDismiss)

    private var isShowing = false

    init {
        card.visibility  = View.INVISIBLE
        card.translationY = 600f
        btnClose.setOnClickListener { dismiss() }
    }

    fun show(planet: Planet) {
        populate(planet)
        card.visibility = View.VISIBLE
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "translationY", 600f, 0f).apply {
                    duration = 420; interpolator = OvershootInterpolator(1.2f)
                },
                ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).apply { duration = 300 }
            )
            start()
        }
        isShowing = true
    }

    fun dismiss() {
        if (!isShowing) return
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "translationY", 0f, 700f).apply {
                    duration = 320; interpolator = DecelerateInterpolator(1.5f)
                },
                ObjectAnimator.ofFloat(card, "alpha", 1f, 0f).apply { duration = 280 }
            )
            start()
        }
        isShowing = false
        onDismiss()
    }

    fun isVisible() = isShowing

    private fun populate(planet: Planet) {
        tvName.text  = planet.displayName
        tvDesc.text  = planet.description
        tvOrbit.text = buildOrbitText(planet)
        tvSize.text  = buildSizeText(planet)

        val r = (planet.colorR * 255).toInt().coerceIn(0, 255)
        val g = (planet.colorG * 255).toInt().coerceIn(0, 255)
        val b = (planet.colorB * 255).toInt().coerceIn(0, 255)

        if (planet.id == "sun") {
            val sunGradient = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    android.graphics.Color.rgb(255, 220, 100),
                    android.graphics.Color.rgb(255, 140, 0)
                )
            ).apply {
                shape        = android.graphics.drawable.GradientDrawable.OVAL
                gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
                gradientRadius = 80f
            }
            ivColor.setImageDrawable(sunGradient)
        } else {
            ivColor.setImageDrawable(
                android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.rgb(r, g, b))
                }
            )
        }
    }

    private fun buildOrbitText(planet: Planet): String = when (planet.id) {
        "sun"   -> "☀️  Position: Center of Solar System\n🔄  Rotation period: ~25 Earth days"
        "earth" -> "🌍  Orbit radius: 1 AU (149.6M km)\n📅  Orbital period: 365.25 days\n🔄  Day length: 24 hours"
        "moon"  -> "🌕  Orbit radius: 384,400 km from Earth\n📅  Orbital period: 27.3 days\n🔄  Tidally locked to Earth"
        "mars"  -> "🔴  Orbit radius: 1.52 AU (227.9M km)\n📅  Orbital period: 687 Earth days\n🔄  Day length: 24h 37m"
        else    -> "Orbit data unavailable"
    }

    private fun buildSizeText(planet: Planet): String = when (planet.id) {
        "sun"   -> "⭐  Diameter: 1,392,700 km\n⚖️  Mass: 1.989 × 10³⁰ kg\n🌡️  Surface temp: 5,778 K"
        "earth" -> "🌐  Diameter: 12,742 km\n⚖️  Mass: 5.972 × 10²⁴ kg\n🌡️  Avg surface temp: 15°C"
        "moon"  -> "🌑  Diameter: 3,474 km\n⚖️  Mass: 7.342 × 10²² kg\n🌡️  Surface temp: −53°C avg"
        "mars"  -> "🪨  Diameter: 6,779 km\n⚖️  Mass: 6.417 × 10²³ kg\n🌡️  Avg surface temp: −60°C"
        else    -> "Size data unavailable"
    }
}

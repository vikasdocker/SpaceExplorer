package com.spaceexplorer

data class Planet(
    val id:          String,
    val displayName: String,
    val radius:      Float,
    val colorR:      Float,
    val colorG:      Float,
    val colorB:      Float,
    val roughness:   Float,
    val metallic:    Float,
    val emissiveR:   Float = 0f,
    val emissiveG:   Float = 0f,
    val emissiveB:   Float = 0f,
    val orbitRadius:     Float,
    val orbitSpeedDeg:   Float,
    val orbitTiltDeg:    Float = 0f,
    val initialAngleDeg: Float = 0f,
    val selfRotSpeedDeg: Float,
    val axialTiltDeg:    Float = 0f,
    val parentId: String? = null,
    val description: String = ""
) {
    companion object {
        val SUN = Planet(
            id = "sun", displayName = "Sun",
            radius = 2.8f,
            colorR = 1.0f, colorG = 0.85f, colorB = 0.30f,
            roughness = 0.9f, metallic = 0.0f,
            emissiveR = 1.0f, emissiveG = 0.75f, emissiveB = 0.20f,
            orbitRadius = 0f, orbitSpeedDeg = 0f,
            selfRotSpeedDeg = 3f, axialTiltDeg = 7.25f,
            description = "Our star. Surface temp ≈ 5 778 K. Diameter: 1.39 million km. Contains 99.86% of the Solar System's mass."
        )

        val EARTH = Planet(
            id = "earth", displayName = "Earth",
            radius = 0.60f,
            colorR = 0.15f, colorG = 0.40f, colorB = 0.82f,
            roughness = 0.60f, metallic = 0.05f,
            orbitRadius = 9.0f, orbitSpeedDeg = 12f,
            orbitTiltDeg = 1.6f, initialAngleDeg = 20f,
            selfRotSpeedDeg = 80f, axialTiltDeg = 23.5f,
            description = "Our home. 1 AU from the Sun. Diameter: 12 742 km. Surface 71% water. Only known planet with confirmed life."
        )

        val MOON = Planet(
            id = "moon", displayName = "Moon",
            radius = 0.165f,
            colorR = 0.60f, colorG = 0.58f, colorB = 0.55f,
            roughness = 0.95f, metallic = 0.0f,
            orbitRadius = 1.5f, orbitSpeedDeg = 40f,
            orbitTiltDeg = 5.1f, initialAngleDeg = 0f,
            selfRotSpeedDeg = 40f, axialTiltDeg = 1.5f,
            parentId = "earth",
            description = "Earth's only natural satellite. Distance: 384 400 km. Diameter: 3 474 km. Tidally locked."
        )

        val MARS = Planet(
            id = "mars", displayName = "Mars",
            radius = 0.34f,
            colorR = 0.78f, colorG = 0.30f, colorB = 0.14f,
            roughness = 0.88f, metallic = 0.0f,
            orbitRadius = 14.5f, orbitSpeedDeg = 6.4f,
            orbitTiltDeg = 1.85f, initialAngleDeg = 160f,
            selfRotSpeedDeg = 78f, axialTiltDeg = 25.19f,
            description = "The Red Planet. 1.52 AU from the Sun. Diameter: 6 779 km. Thin CO₂ atmosphere. Home to Olympus Mons."
        )

        val ALL = listOf(SUN, EARTH, MOON, MARS)
    }
}

package io.github.elaaps.respawnalarm

private val VENDOR_PAGES = mapOf(
    "huawei" to "huawei",
    "honor" to "huawei",
    "xiaomi" to "xiaomi",
    "redmi" to "xiaomi",
    "poco" to "xiaomi",
    "oneplus" to "oneplus",
    "samsung" to "samsung",
    "meizu" to "meizu",
    "asus" to "asus",
    "oppo" to "oppo",
    "vivo" to "vivo",
    "realme" to "realme",
    "wiko" to "wiko",
    "lenovo" to "lenovo",
    "motorola" to "motorola",
    "sony" to "sony",
    "ulefone" to "ulefone",
    "blackview" to "blackview",
    "tecno" to "tecno",
    "unihertz" to "unihertz",
)

fun backgroundGuideUrl(manufacturer: String): String? =
    VENDOR_PAGES[manufacturer.trim().lowercase()]?.let { "https://dontkillmyapp.com/$it" }

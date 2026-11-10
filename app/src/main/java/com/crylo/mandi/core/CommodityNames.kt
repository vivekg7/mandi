package com.crylo.mandi.core

/**
 * Hindi names for common commodities, shown next to the English Agmarknet name.
 *
 * Agmarknet names carry qualifiers in brackets ("Bhindi(Ladies Finger)",
 * "Arhar (Tur/Red Gram)(Whole)"), so lookups use the part before the first bracket.
 * Names not listed here simply show in English.
 */
object CommodityNames {
    fun baseName(commodity: String): String =
        commodity.substringBefore('(').trim().lowercase().replace(Regex("\\s+"), " ")

    fun hindi(commodity: String): String? = HINDI[baseName(commodity)]

    /** True when the query matches the English or Hindi name, ignoring case. */
    fun matches(commodity: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        return commodity.contains(q, ignoreCase = true) || hindi(commodity)?.contains(q) == true
    }

    private val HINDI = mapOf(
        "wheat" to "गेहूं",
        "paddy" to "धान",
        "rice" to "चावल",
        "maize" to "मक्का",
        "bajra" to "बाजरा",
        "jowar" to "ज्वार",
        "barley" to "जौ",
        "ragi" to "रागी",
        "onion" to "प्याज",
        "potato" to "आलू",
        "tomato" to "टमाटर",
        "brinjal" to "बैंगन",
        "cabbage" to "पत्ता गोभी",
        "cauliflower" to "फूल गोभी",
        "green chilli" to "हरी मिर्च",
        "dry chillies" to "सूखी मिर्च",
        "bhindi" to "भिंडी",
        "carrot" to "गाजर",
        "raddish" to "मूली",
        "radish" to "मूली",
        "spinach" to "पालक",
        "methi" to "मेथी",
        "coriander" to "धनिया",
        "garlic" to "लहसुन",
        "ginger" to "अदरक",
        "bottle gourd" to "लौकी",
        "bitter gourd" to "करेला",
        "ridgeguard" to "तोरई",
        "sponge gourd" to "तोरई",
        "pumpkin" to "कद्दू",
        "cucumbar" to "खीरा",
        "cucumber" to "खीरा",
        "capsicum" to "शिमला मिर्च",
        "peas wet" to "हरी मटर",
        "green peas" to "हरी मटर",
        "beans" to "बीन्स",
        "tinda" to "टिंडा",
        "colacasia" to "अरबी",
        "sweet potato" to "शकरकंद",
        "drumstick" to "सहजन",
        "lemon" to "नींबू",
        "banana" to "केला",
        "apple" to "सेब",
        "mango" to "आम",
        "papaya" to "पपीता",
        "guava" to "अमरूद",
        "grapes" to "अंगूर",
        "orange" to "संतरा",
        "pomegranate" to "अनार",
        "water melon" to "तरबूज",
        "pineapple" to "अनानास",
        "mousambi" to "मौसमी",
        "coconut" to "नारियल",
        "mustard" to "सरसों",
        "soyabean" to "सोयाबीन",
        "groundnut" to "मूंगफली",
        "sesamum" to "तिल",
        "castor seed" to "अरंडी",
        "cotton" to "कपास",
        "sugarcane" to "गन्ना",
        "jaggery" to "गुड़",
        "gur" to "गुड़",
        "turmeric" to "हल्दी",
        "jeera" to "जीरा",
        "bengal gram" to "चना",
        "gram raw" to "हरा चना",
        "kabuli chana" to "काबुली चना",
        "arhar" to "अरहर",
        "arhar dal" to "अरहर दाल",
        "green gram" to "मूंग",
        "black gram" to "उड़द",
        "lentil" to "मसूर",
        "moath dal" to "मोठ",
        "field pea" to "मटर",
        "guar" to "ग्वार",
        "isabgul" to "इसबगोल",
        "methi seeds" to "मेथी दाना",
    )
}

package com.thesis.bananaleaf

data class DiseaseDetails(
    val displayName: String,
    val overview: String,
    val symptoms: List<String>,
    val treatment: List<String>,
    val recommendations: List<String>
)

/**
 * Disease information shown after a banana leaf scan.
 * The scan is a screening aid; symptoms can overlap between diseases,
 * so uncertain cases should be confirmed by a qualified agricultural professional.
 */
object DiseaseInfo {

    private val DETAILS = mapOf(
        "Healthy" to DiseaseDetails(
            displayName = "Healthy Banana Leaf",
            overview = "The foliar scan indicates optimal plant vigor with no significant fungal pathogens detected. Leaf lamina, venation, and margins demonstrate healthy photosynthetic capacity, cellular integrity, and robust chlorophyll distribution.",
            symptoms = listOf(
                "Uniform Foliar Pigmentation: Deep, vibrant green coloration across the blade reflecting high chlorophyll density and vigorous photosynthesis.",
                "Intact Vascular Architecture: Distinct, firm midrib and parallel lateral veins free of internal vascular discoloration, browning, or streaking.",
                "Smooth Margin Contour: Continuous, well-hydrated leaf edges without marginal scorch, yellow haloing, or necrotic firing.",
                "Absence of Fungal Lesions: Clear lamina with zero characteristic streaks or spots associated with Black Sigatoka, Cordana Leaf Spot, or Pestalotiopsis."
            ),
            treatment = listOf(
                "Maintenance Nutrition: Apply balanced N-P-K fertilizer with ample potassium (K) to fortify cell wall thickness and foliar resistance against airborne spores.",
                "Canopy Hygiene: Periodically prune older, lower senescent leaves that touch soil to prevent splash-borne spore transmission and microclimate humidity traps.",
                "Soil-Level Irrigation: Direct irrigation to root basins via drip or furrow methods; avoid overhead watering that keeps leaf blades wet for prolonged hours.",
                "Sanitized Pruning Tools: Sterilize knives and machetes in 10% household bleach or rubbing alcohol solution when moving between banana mats."
            ),
            recommendations = listOf(
                "Optimal Planting Spacing: Maintain recommended spacing (2.5m × 2.5m to 3.0m × 3.0m) to promote natural sunlight penetration and canopy aeration.",
                "Effective Field Drainage: Keep drainage canals cleared and open to prevent soil waterlogging, which stresses roots and compromises disease defense.",
                "Systematic Mat Pruning (De-suckering): Maintain only the mother plant, one active daughter sucker, and one follower to focus nutrition into vigorous canopy development.",
                "Organic Soil Mulching: Apply organic mulch around root zones (15cm away from pseudostem) to conserve soil moisture and support beneficial soil microbial activity."
            )
        ),
        "Sigatoka" to DiseaseDetails(
            displayName = "Black Sigatoka",
            overview = "Black Sigatoka is a fungal leaf disease caused by Pseudocercospora fijiensis. It damages leaf tissue and can reduce photosynthesis and yield when severe.",
            symptoms = listOf(
                "Small light or reddish-brown streaks and spots on the leaf, which can grow into bigger dark spots.",
                "Spots may turn dark in the middle with a yellow ring around them.",
                "In bad cases, large parts of the leaf turn brown and die early."
            ),
            treatment = listOf(
                "Remove and destroy heavily infected leaves to reduce sources of fungal spores.",
                "Improve field drainage and air circulation to reduce prolonged leaf wetness.",
                "Ask your local agricultural extension office about an approved fungicide program and rotate products according to local guidance."
            ),
            recommendations = listOf(
                "Avoid overhead irrigation where possible because wet leaves can favor fungal infection.",
                "Monitor younger leaves and nearby plants regularly for new lesions.",
                "Keep the plantation clean and remove severely affected leaf material.",
                "Use only locally approved disease-management products and follow their label directions."
            )
        ),
        "Cordana" to DiseaseDetails(
            displayName = "Cordana Leaf Spot",
            overview = "Cordana Leaf Spot is associated with the fungal pathogen Neocordana musae. It commonly produces brown leaf lesions that can expand and cause defoliation when severe.",
            symptoms = listOf(
                "Light-brown to brown spots on the leaf.",
                "Spots may have a gray center with a darker edge.",
                "Many spots can join together, making bigger damaged or dead areas on the leaf."
            ),
            treatment = listOf(
                "Prune and dispose of severely spotted leaves away from the plantation.",
                "Reduce leaf wetness and improve air circulation through appropriate plant spacing.",
                "If lesions are spreading rapidly, consult your agricultural extension office about locally approved fungicide options."
            ),
            recommendations = listOf(
                "Avoid working through wet foliage when possible to reduce movement of fungal spores.",
                "Monitor nearby leaves regularly for expanding spots.",
                "Maintain good drainage and field sanitation.",
                "Use only locally approved disease-management products and follow their label directions."
            )
        ),
        "Pestalotiopsis" to DiseaseDetails(
            displayName = "Pestalotiopsis Leaf Blight",
            overview = "Pestalotiopsis Leaf Blight is associated with Pestalotiopsis species fungi. The infection damages leaf tissue and may progress to brown or necrotic areas under favorable conditions.",
            symptoms = listOf(
                "Brown or tan spots with uneven shapes that can grow bigger over time.",
                "Affected parts of the leaf can dry out and look dead.",
                "In bad cases, there is less healthy green leaf left."
            ),
            treatment = listOf(
                "Remove badly blighted leaf tissue promptly and dispose of it away from healthy plants.",
                "Reduce how long leaves stay wet by improving drainage and avoiding late-day watering.",
                "Consult your agricultural extension office for locally appropriate fungicide options when disease is spreading."
            ),
            recommendations = listOf(
                "Monitor nearby plants closely for new or expanding lesions.",
                "Improve airflow and drainage around the banana plants.",
                "Keep infected leaf material away from healthy foliage.",
                "Use only locally approved disease-management products and follow their label directions."
            )
        )
    )

    fun get(label: String): DiseaseDetails =
        DETAILS[label] ?: DiseaseDetails(
            displayName = label,
            overview = "The scanned leaf was associated with a result that has no additional information in this app.",
            symptoms = listOf("No symptom description is available for this result."),
            treatment = listOf("Consult your local agricultural extension office for guidance."),
            recommendations = listOf("Use a clear, well-lit leaf image and consider professional confirmation for uncertain results.")
        )

    fun mergedRecommendations(labels: List<String>): List<String> =
        labels.flatMap { get(it).recommendations }.distinct()

    /** Returns all disease/condition keys registered in this object. */
    fun getAllKeys(): List<String> = DETAILS.keys.toList()
}


/**
 * Tagalog text used ONLY for the read-aloud (text-to-speech) voice. Kept
 * separate from [DiseaseInfo] so the on-screen labels/cards stay in English
 * (matching the reference design) while the spoken result is in Tagalog.
 * Each tab (Disease / Treatment / Recommendations) speaks straight from
 * here -- no title or "Single Disease Detected"-style preamble is included,
 * so tapping a tab goes directly into that tab's Tagalog content.
 */
data class DiseaseSpeechTagalog(
    val overview: String,
    val treatment: String,
    val recommendation: String
)

object DiseaseInfoTagalog {

    private val SPEECH = mapOf(
        "Healthy" to DiseaseSpeechTagalog(
            overview = "Malusog ang dahon ng saging. Walang natukoy na senyales ng fungal pathogen tulad ng Sigatoka, Cordana, o Pestalotiopsis. Maganda ang kulay berde at maayos ang selyula ng dahon.",
            treatment = "Panatilihin ang balanseng pataba na mayaman sa potassium upang mapanatiling matibay ang dahon. Putulin ang mga lumang tuyong dahon sa ibaba upang maiwasan ang halumigmig.",
            recommendation = "Panatilihin ang maayos na agwat ng mga puno at malinis na daluyan ng tubig upang hindi maipon ang tubig sa ugat. Regular na suriin ang mga bagong sibol na dahon."
        ),
        "Sigatoka" to DiseaseSpeechTagalog(
            overview = "Itim na Sigatoka ang natukoy. Ito ay sakit na dulot ng fungus na Pseudocercospora fijiensis, na sumisira sa tisyu ng dahon at maaaring magpababa ng ani kapag lumala.",
            treatment = "Alisin at itapon ang mga dahong matindi ang impeksyon upang mabawasan ang pinagmumulan ng spora ng fungus. Pagbutihin din ang drainage at daloy ng hangin sa taniman.",
            recommendation = "Iwasan ang overhead irrigation kung maaari dahil ang basang dahon ay pabor sa impeksyon. Regular na subaybayan ang mga bagong dahon at kalapit na halaman."
        ),
        "Cordana" to DiseaseSpeechTagalog(
            overview = "Cordana Leaf Spot ang natukoy. Ito ay may kaugnayan sa fungus na Neocordana musae, na karaniwang gumagawa ng brown na mantsa sa dahon na maaaring lumaki kapag matindi.",
            treatment = "Putulin at itapon ang mga dahong may matinding mantsa nang malayo sa taniman. Bawasan ang kabasaan ng dahon at pagbutihin ang daloy ng hangin.",
            recommendation = "Iwasang gumalaw sa basang dahon kung maaari upang mabawasan ang paglipat ng spora. Panatilihin ang mabuting drainage at kalinisan ng taniman."
        ),
        "Pestalotiopsis" to DiseaseSpeechTagalog(
            overview = "Pestalotiopsis Leaf Blight ang natukoy. Ito ay may kaugnayan sa fungus na Pestalotiopsis, na sumisira sa tisyu ng dahon at maaaring maging tuyo o brown kapag paborable ang kondisyon.",
            treatment = "Alisin agad ang matinding apektadong bahagi ng dahon at itapon nang malayo sa malulusog na halaman. Bawasan ang oras na basa ang dahon sa pamamagitan ng mas mahusay na drainage.",
            recommendation = "Subaybayan nang mabuti ang mga kalapit na halaman para sa bago o lumalaking mantsa. Pagbutihin ang daloy ng hangin at drainage sa paligid ng mga puno ng saging."
        )
    )

    private val DEFAULT = DiseaseSpeechTagalog(
        overview = "Ang nai-scan na dahon ay may resulta na walang karagdagang impormasyon sa app na ito.",
        treatment = "Kumunsulta sa inyong lokal na tanggapan ng agrikultura para sa gabay.",
        recommendation = "Gumamit ng malinaw at maliwanag na larawan ng dahon at isaalang-alang ang kumpirmasyon mula sa isang propesyonal."
    )

    fun get(label: String): DiseaseSpeechTagalog = SPEECH[label] ?: DEFAULT
}

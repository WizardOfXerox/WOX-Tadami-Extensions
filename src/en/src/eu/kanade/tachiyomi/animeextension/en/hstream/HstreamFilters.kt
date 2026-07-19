package eu.kanade.tachiyomi.animeextension.en.hstream

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object HstreamFilters {
    open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray()
    ) {
        fun toUri() = vals[state].second
    }

    class OrderFilter : UriPartFilter("Order by", orderList)

    class Genre(name: String, val value: String) : AnimeFilter.TriState(name)
    class GenreFilter : AnimeFilter.Group<Genre>("Genres", genreList.map { Genre(it.first, it.second) })

    class Studio(name: String, val value: String) : AnimeFilter.CheckBox(name)
    class StudioFilter : AnimeFilter.Group<Studio>("Studios", studioList.map { Studio(it.first, it.second) })

    fun getFilters() = AnimeFilterList(
        OrderFilter(),
        GenreFilter(),
        StudioFilter()
    )

    private val orderList = arrayOf(
        Pair("View Count", "view-count"),
        Pair("A-Z", "az"),
        Pair("Z-A", "za"),
        Pair("Recently Uploaded", "recently-uploaded"),
        Pair("Recently Released", "recently-released"),
        Pair("Oldest Uploads", "oldest-uploads"),
        Pair("Oldest Releases", "oldest-releases")
    )

    private val genreList = arrayOf(
        Pair("3D", "3d"),
        Pair("4K", "4k"),
        Pair("Ahegao", "ahegao"),
        Pair("Anal", "anal"),
        Pair("Bdsm", "bdsm"),
        Pair("Big Boobs", "big-boobs"),
        Pair("Blow Job", "blow-job"),
        Pair("Bondage", "bondage"),
        Pair("Boob Job", "boob-job"),
        Pair("Censored", "censored"),
        Pair("Comedy", "comedy"),
        Pair("Cosplay", "cosplay"),
        Pair("Creampie", "creampie"),
        Pair("Dark Skin", "dark-skin"),
        Pair("Elf", "elf"),
        Pair("Facial", "facial"),
        Pair("Fantasy", "fantasy"),
        Pair("Filmed", "filmed"),
        Pair("Foot Job", "foot-job"),
        Pair("Futanari", "futanari"),
        Pair("Gangbang", "gangbang"),
        Pair("Glasses", "glasses"),
        Pair("Hand Job", "hand-job"),
        Pair("Harem", "harem"),
        Pair("Horror", "horror"),
        Pair("Incest", "incest"),
        Pair("Inflation", "inflation"),
        Pair("Lactation", "lactation"),
        Pair("Loli", "loli"),
        Pair("Maid", "maid"),
        Pair("Masturbation", "masturbation"),
        Pair("Milf", "milf"),
        Pair("Mind Break", "mind-break"),
        Pair("Mind Control", "mind-control"),
        Pair("Monster", "monster"),
        Pair("Nekomimi", "nekomimi"),
        Pair("Ntr", "ntr"),
        Pair("Nurse", "nurse"),
        Pair("Orgy", "orgy"),
        Pair("Pov", "pov"),
        Pair("Pregnant", "pregnant"),
        Pair("Public Sex", "public-sex"),
        Pair("Rape", "rape"),
        Pair("Reverse Rape", "reverse-rape"),
        Pair("Rimjob", "rimjob"),
        Pair("Scat", "scat"),
        Pair("School Girl", "school-girl"),
        Pair("Shota", "shota"),
        Pair("Small Boobs", "small-boobs"),
        Pair("Succubus", "succubus"),
        Pair("Swim Suit", "swim-suit"),
        Pair("Teacher", "teacher"),
        Pair("Tentacle", "tentacle"),
        Pair("Threesome", "threesome"),
        Pair("Toys", "toys"),
        Pair("Trap", "trap"),
        Pair("Tsundere", "tsundere"),
        Pair("Ugly Bastard", "ugly-bastard"), // Wait, value in n.java was "ugly-bastard"
        Pair("Uncensored", "uncensored"),
        Pair("Vanilla", "vanilla"),
        Pair("Virgin", "virgin"),
        Pair("X-Ray", "x-ray"),
        Pair("Yuri", "yuri")
    )

    private val studioList = arrayOf(
        Pair("BOMB! CUTE! BOMB!", "bomb-cute-bomb"),
        Pair("BreakBottle", "breakbottle"),
        Pair("ChiChinoya", "chichinoya"),
        Pair("ChuChu", "chuchu"),
        Pair("Circle Tribute", "circle-tribute"),
        Pair("Collaboration Works", "collaboration-works"),
        Pair("Digital Works", "digital-works"),
        Pair("Discovery", "discovery"),
        Pair("Edge", "edge"),
        Pair("Gold Bear", "gold-bear"),
        Pair("Green Bunny", "green-bunny"),
        Pair("Himajin Planning", "himajin-planning"),
        Pair("King Bee", "king-bee"),
        Pair("L.", "l"),
        Pair("Lune Pictures", "lune-pictures"),
        Pair("MS Pictures", "ms-pictures"),
        Pair("Majin", "majin"),
        Pair("Mary Jane", "mary-jane"),
        Pair("Mediabank", "mediabank"),
        Pair("Mousou Senka", "mousou-senka"),
        Pair("Natural High", "natural-high"),
        Pair("Nihikime no Dozeu", "nihikime-no-dozeu"),
        Pair("Nur", "nur"),
        Pair("Pashmina", "pashmina"),
        Pair("Peak Hunt", "peak-hunt"),
        Pair("Pink Pineapple", "pink-pineapple"),
        Pair("Pixy Soft", "pixy-soft"),
        Pair("Pixy", "pixy"),
        Pair("PoRO", "poro"),
        Pair("Queen Bee", "queen-bee"),
        Pair("Rabbit Gate", "rabbit-gate"),
        Pair("SELFISH", "selfish"),
        Pair("Seven", "seven"),
        Pair("Showten", "showten"),
        Pair("Studio 1st", "studio-1st"),
        Pair("Studio Eromatick", "studio-eromatick"),
        Pair("Studio Fantasia", "studio-fantasia"),
        Pair("Suiseisha", "suiseisha"),
        Pair("Suzuki Mirano", "suzuki-mirano"),
        Pair("T-Rex", "t-rex"),
        Pair("Toranoana", "toranoana"),
        Pair("Union Cho", "union-cho"),
        Pair("Valkyria", "valkyria"),
        Pair("White Bear", "white-bear"),
        Pair("ZIZ", "ziz")
    )
}

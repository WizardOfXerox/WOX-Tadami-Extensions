package eu.kanade.tachiyomi.animeextension.en.hentaiht

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

object HentaiHtFilters {
    class SortFilter : AnimeFilter.Select<String>(
        "Sort By",
        SORT_OPTIONS.map { it.first }.toTypedArray(),
    ) {
        val selected: String
            get() = SORT_OPTIONS[state].second
    }

    class TagFilter(name: String, private val tags: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(name, tags.map { it.first }.toTypedArray()) {
        val selected: String
            get() = tags[state].second
    }

    class StudioFilter(name: String, private val studios: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(name, studios.map { it.first }.toTypedArray()) {
        val selected: String
            get() = studios[state].second
    }

    class YearFilter(name: String, private val years: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(name, years.map { it.first }.toTypedArray()) {
        val selected: String
            get() = years[state].second
    }

    val SORT_OPTIONS = arrayOf(
        Pair("Default", ""),
        Pair("Recently Published", "recent"),
        Pair("AniList Popularity", "watched"),
        Pair("AniList Score", "score"),
        Pair("Episode Count", "episodes"),
        Pair("Year", "year"),
        Pair("Alphabetical", "alpha"),
        Pair("Relevance", "relevance"),
    )

    val TAGS = arrayOf(
        Pair("All Tags", ""),
        Pair("School", "School"),
        Pair("Male Protagonist", "Male Protagonist"),
        Pair("Female Protagonist", "Female Protagonist"),
        Pair("Heterosexual", "Heterosexual"),
        Pair("Teacher", "Teacher"),
        Pair("Primarily Female Cast", "Primarily Female Cast"),
        Pair("Age Gap", "Age Gap"),
        Pair("Maids", "Maids"),
        Pair("Gyaru", "Gyaru"),
        Pair("Cosplay", "Cosplay"),
        Pair("Primarily Adult Cast", "Primarily Adult Cast"),
        Pair("Demons", "Demons"),
        Pair("Tsundere", "Tsundere"),
        Pair("Episodic", "Episodic"),
        Pair("Magic", "Magic"),
        Pair("Elf", "Elf"),
        Pair("Succubus", "Succubus"),
        Pair("Monster Girl", "Monster Girl"),
        Pair("Office Lady", "Office Lady"),
        Pair("Anthology", "Anthology"),
        Pair("Idol", "Idol"),
        Pair("Kemonomimi", "Kemonomimi"),
        Pair("Ensemble Cast", "Ensemble Cast"),
        Pair("Rural", "Rural"),
        Pair("Swordplay", "Swordplay"),
        Pair("Twins", "Twins"),
        Pair("Isekai", "Isekai"),
        Pair("Ninja", "Ninja"),
        Pair("Love Triangle", "Love Triangle"),
        Pair("Work", "Work"),
        Pair("Marriage", "Marriage"),
        Pair("Nekomimi", "Nekomimi"),
        Pair("Photography", "Photography"),
        Pair("Vampire", "Vampire"),
        Pair("Yandere", "Yandere"),
        Pair("Cohabitation", "Cohabitation"),
        Pair("Gods", "Gods"),
        Pair("Seinen", "Seinen"),
        Pair("Shapeshifting", "Shapeshifting"),
        Pair("Angels", "Angels"),
        Pair("Dragons", "Dragons"),
        Pair("Space", "Space"),
        Pair("Time Manipulation", "Time Manipulation"),
        Pair("Urban Fantasy", "Urban Fantasy"),
        Pair("Band", "Band"),
        Pair("Body Swapping", "Body Swapping"),
        Pair("Crossdressing", "Crossdressing"),
        Pair("Delinquents", "Delinquents"),
        Pair("Full CGI", "Full CGI"),
        Pair("Ghost", "Ghost"),
        Pair("Josei", "Josei"),
        Pair("Kuudere", "Kuudere"),
        Pair("Pirates", "Pirates"),
        Pair("Travel", "Travel"),
        Pair("Video Games", "Video Games"),
        Pair("Virtual World", "Virtual World"),
        Pair("Witch", "Witch"),
        Pair("Writing", "Writing"),
    )

    val STUDIOS = arrayOf(
        Pair("All Studios", ""),
        Pair("AniMan", "AniMan"),
        Pair("Anime Antenna Iinkai", "Anime Antenna Iinkai"),
        Pair("BOOTLEG", "BOOTLEG"),
        Pair("BREAKBOTTLE", "BREAKBOTTLE"),
        Pair("Bunny Walker", "Bunny Walker"),
        Pair("Collaboration Works", "Collaboration Works"),
        Pair("Cotton Doll", "Cotton Doll"),
        Pair("EDGE", "EDGE"),
        Pair("Flavors Soft", "Flavors Soft"),
        Pair("G-lam", "G-lam"),
        Pair("GOLD BEAR", "GOLD BEAR"),
        Pair("HiLLS", "HiLLS"),
        Pair("Hoods Entertainment", "Hoods Entertainment"),
        Pair("Hot Bear", "Hot Bear"),
        Pair("Jumondou", "Jumondou"),
        Pair("Lune Pictures", "Lune Pictures"),
        Pair("Magic Bus", "Magic Bus"),
        Pair("Majin", "Majin"),
        Pair("Mary Jane", "Mary Jane"),
        Pair("Milky Animation Label", "Milky Animation Label"),
        Pair("Mitsu", "Mitsu"),
        Pair("Mousou Senka", "Mousou Senka"),
        Pair("NewGeneration", "NewGeneration"),
        Pair("Office 8-ban", "Office 8-ban"),
        Pair("Office Take Off", "Office Take Off"),
        Pair("Office Takeout", "Office Takeout"),
        Pair("Pashmina", "Pashmina"),
        Pair("Peak Hunt", "Peak Hunt"),
        Pair("Pink Pineapple", "Pink Pineapple"),
        Pair("PoRO", "PoRO"),
        Pair("PoRO petit", "PoRO petit"),
        Pair("Queen Bee", "Queen Bee"),
        Pair("Rabbit Gate", "Rabbit Gate"),
        Pair("Ryuu M's", "Ryuu M's"),
        Pair("Selfish", "Selfish"),
        Pair("Seven", "Seven"),
        Pair("Shion", "Shion"),
        Pair("Studio 1st", "Studio 1st"),
        Pair("Studio Houkiboshi", "Studio Houkiboshi"),
        Pair("studio LEO", "studio LEO"),
        Pair("Studio Silver", "Studio Silver"),
        Pair("Studio SUNHAN", "Studio SUNHAN"),
        Pair("T-REX", "T-REX"),
        Pair("TEATRO Nishi Tokyo Studio", "TEATRO Nishi Tokyo Studio"),
        Pair("WHITE BEAR", "WHITE BEAR"),
    )

    val YEARS = arrayOf(
        Pair("All Years", ""),
        Pair("2026", "2026"),
        Pair("2025", "2025"),
        Pair("2024", "2024"),
        Pair("2023", "2023"),
        Pair("2022", "2022"),
        Pair("2021", "2021"),
        Pair("2020", "2020"),
        Pair("2019", "2019"),
        Pair("2018", "2018"),
        Pair("2017", "2017"),
        Pair("2016", "2016"),
        Pair("2015", "2015"),
        Pair("2014", "2014"),
        Pair("2013", "2013"),
        Pair("2012", "2012"),
        Pair("2011", "2011"),
        Pair("2010", "2010"),
    )
}

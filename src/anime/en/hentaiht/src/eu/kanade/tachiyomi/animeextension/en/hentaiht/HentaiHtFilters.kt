package eu.kanade.tachiyomi.animeextension.en.hentaiht

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

object HentaiHtFilters {
    class TagFilter(name: String, private val tags: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(name, tags.map { it.first }.toTypedArray()) {
        val selected: String
            get() = tags[state].second
    }

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
        Pair("Writing", "Writing")
    )
}

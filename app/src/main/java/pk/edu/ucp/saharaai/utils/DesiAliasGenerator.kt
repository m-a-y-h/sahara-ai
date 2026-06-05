package pk.edu.ucp.saharaai.utils

import kotlin.random.Random

object DesiAliasGenerator {
    fun generate(): String {
        val masculine = Random.nextBoolean()
        val adjectives = if (masculine) {
            masculineAdjectives + neutralAdjectives
        } else {
            feminineAdjectives + neutralAdjectives
        }
        val nouns = if (masculine) masculineNouns else feminineNouns
        return "${adjectives.random()}${nouns.random()}"
    }

    private val masculineAdjectives = listOf(
        "Anokhā", "Barhā", "Bhookhā", "Bhunā", "Chamkeelā", "Chatpatā", "Chhotā", "Deewānā", 
        "Gorā", "Gungunā", "Halkā", "Kālā", "Karārā", "Khattā", "Kurkurā", "Lambā", "Mastānā", 
        "Mehangā", "Meethā", "Motā", "Nakhraylā", "Nayā", "Oonchā", "Pakkā", "Pehlā", "Purānā", 
        "Pyārā", "Rangeelā", "Sachā", "Sādā", "Sastā", "Seedhā", "Suhānā", "Sunehrā", "Tāzā", 
        "Thandā", "Ujlā"
    )

    private val feminineAdjectives = listOf(
        "Anokhi", "Barhi", "Bhookhi", "Bhuni", "Chamkeeli", "Chatpati", "Chhoti", "Deewāni", 
        "Gori", "Gunguni", "Halki", "Kāli", "Karāri", "Khatti", "Kurkuri", "Lambi", "Mastāni", 
        "Mehangi", "Meethi", "Moti", "Nakhreeli", "Nayi", "Oonchi", "Pakki", "Pehli", "Purāni", 
        "Pyāri", "Rangeeli", "Sachi", "Sādi", "Sasti", "Seedhi", "Suhāni", "Sunehri", "Tāzi", 
        "Thandi", "Ujli"
    )

    private val neutralAdjectives = listOf(
        "Aflātooni", "Afsānvi", "Ajeeb", "Ākhri", "Ālā", "Āam", "Asli", "Awesome", "Bādshāhi", 
        "Bāpardā", "Behtareen", "Beshak", "Bewafā", "Bindās", "Chalāk", "Chamakdār", "Chusst", 
        "Classic", "Crunchy", "Dabang", "Desi", "Dhamākedār", "Dilchasp", "Dildār", "Epic", 
        "Fattāfat", "Fikarmand", "Garam", "Ghamgheen", "Ghamandi", "Gharelu", "Ghazab", "Gulābi", 
        "Haseen", "Havāi", "Hoshiyār", "Jādooyi", "Jalāli", "Jangli", "Jawān", "Jazbāti", 
        "Jigri", "Jogāri", "Kāghzi", "Kam", "Kamāl", "Kāmyāb", "Karhak", "Khās", "Khatārnak", 
        "Khufiyā", "Khush", "Khushnumā", "Lājawāb", "Lālchi", "Lazeez", "Madhhosh", "Māsoom", 
        "Mast", "Mazedār", "Mehnati", "Mezbāni", "Muhib-e-watan", "Mukammal", "Namāzi", "Naram", 
        "Nawābi", "Nāyāb", "Nāzuk", "Ninja", "Pāk", "Pareshān", "Qābil", "Qābil-e-Tāreef", 
        "Qadeem", "Qareebi", "Roshan", "Royal", "Safayd", "Sahili", "Sahi", "Sakht", "Salāmat", 
        "Samajhdār", "Sanjeedā", "Sargaram", "Satrangi", "Sehatmand", "Shāhi", "Shāndār", "Spicy", 
        "Tandoori", "Tāqatwar", "Tāreekhi", "Tez", "Toofāni", "VVIP", "Wafādār", "Zabardast", 
        "Zāfrāni", "Zālim", "Zameeni", "Zaroori", "Ziddi", "Zinda", "ZindaDil", "Zordār",
    )

    private val masculineNouns = listOf(
        "Adrak", "Āam", "Akhrot", "Āloo", "Amrood", "Anār", "Andā", "Angoor", "Bādām", "Baingan", "Banana",
        "Beef", "Besan", "Bhejā", "Biscuit", "BongPaye", "Broast", "BunKabab", "Burger", "Butter", "Cake",
        "Cashew", "Chakna", "Chamomile", "ChanāMasāla", "Channā", "ChapliKabab", "ChapliMasāla", "Chargha", "ChātMasāla", "Chāwal",
        "CheeseCake", "Chicken", "ChickenTikka", "Chickpea", "Chilghoza", "Chowmein", "Cookie", "Crab", "Cutlas", "Dahi",
        "DahiBharhay", "DālChāwal", "Dampukht", "Donut", "Doodh", "DoodhSoda", "DoubleKaMeetha", "Egg", "Falooda", "Falsa",
        "FriedRice", "Gajrelā", "GannayKaJuice", "GaramMasālā", "Ghee", "GolāGandā", "GolGappa", "GulābJamun", "Gurh", "Haleem",
        "Halwā", "Jam", "JāmEShireen", "Jāmun", "Jhingā", "Juice", "Kabab", "Kachoomber", "Kaddu", "Kāju",
        "Karelā", "Keemā", "Kelā", "Khameer", "Kharbooza", "Kulcha", "Kulfa", "Laddoo", "Lassan", "LemonGrass",
        "LimuPāni", "Lobiā", "Maghaz", "Makhan", "Māltā", "Masālā", "Massar", "Motichoor", "Nāan", "Namak",
        "Naurus", "Nimbu", "NimbuPāni", "OliveOil", "Omelette", "Pakorā", "Pancake", "Paneer", "Pāni", "Paparh",
        "Parātha", "Pāstā", "Pathoorā", "Patisā", "Pāyā", "PeanutButter", "Perhā", "PhulMakhāna", "Pistā", "Pizza",
        "Prawn", "Pulāo", "Pyāz", "Qahwā", "Qormā", "Rasgulla", "Roll", "RoohAfzā", "Sāag", "Sālān",
        "Samosa", "Sandal", "Sandwich", "Santra", "Sattu", "Seb", "SeekhKabab", "SeekhMasālā", "ShāhiTukray", "Shāmi",
        "Sharbat", "Shawarma", "SheerKhormā", "Shehed", "Shrhambe", "Singhāra", "SiriPāye", "Sirkā", "Soda", "Squash",
        "Talbeena", "Tamātar", "Tarbooz", "Tel", "Tikkā", "Vinegar", "Waffle", "Wings", "Zardā", "Zeerā",
    )

    private val feminineNouns = listOf(
        "Arvi", "Balushahi", "Bāqarkhāni", "Barfi", "Bhindi", "Biryāni", "Coffee", "Bong",
        "Boondi", "Boti", "BreadPudding", "Brownie", "CardamomTea", "Chai", "ChanāChāt", "Chapāti", "Chāt", "ChātPāprhi",
        "Cheeni", "Chhalli", "Chikki", "Chutni", "DahiPhulkiān", "Dāl", "DālMāsh", "Dārcheeni", "DoodhPati", "DoubleRoti",
        "Batakh", "Firni", "FruitChāt", "Gājar", "Gobi", "GolGappi", "GreenTea", "Haldi", "Hāndi", "IceCream",
        "Ilaichi", "Imli", "Injeer", "Jalebi", "Kachori", "Kaleji", "Karāhi", "KashmiriChai", "Khashkhāsh", "Kheer",
        "Khichrhi", "Khujoor", "Khumāni", "Kishmish", "Kulfi", "Lassi", "Lauki", "Machli", "Makai", "Malāi",
        "Methi", "Mirch", "Mooli", "Moong", "Moongphalli", "Mosambi", "Nāshpāti", "Nihāri", "Nimko", "Pālak",
        "Panjeeri", "Pāpri", "PeshāwariChai", "Pheni", "Purhi", "Rabri", "RasMalāi", "Rewari", "Roti", "Sabzi",
        "Sajji", "Saunf", "Sevaiyān", "Sooji", "Strawberry", "Supāri", "TawāsheerLāachian", "TawayWāliIcecream", "Tikkiān",
        "Tori", "Tulsi", "Warhiān", "Yakhni",
    )
}

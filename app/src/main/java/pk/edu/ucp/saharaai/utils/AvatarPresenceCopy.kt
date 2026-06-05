package pk.edu.ucp.saharaai.utils

fun avatarPresenceLine(avatarId: String, isEnglish: Boolean): String {
    return when (avatarId) {
        "avatar_01" -> if (isEnglish) {
            "Markhor is found in Chitral, Hunza, Gilgit-Baltistan, and Kashmir."
        } else {
            "Markhor Chitral, Hunza, Gilgit-Baltistan aur Kashmir ke paharon mein pāya jata hai."
        }
        "avatar_02" -> if (isEnglish) {
            "The Indus River dolphin is found in the Indus near Guddu, Sukkur, and Taunsa."
        } else {
            "Daryā-e-Sindh ki dolphin Guddu, Sukkur aur Taunsa ke paas pāyi jati hai."
        }
        "avatar_03" -> if (isEnglish) {
            "Snow leopard is found in Gilgit-Baltistan, Chitral, Karakoram, and Hindu Kush."
        } else {
            "Snow leopard Gilgit-Baltistan, Chitral, Karakoram aur Hindu Kush mein pāya jata hai."
        }
        "avatar_04" -> if (isEnglish) {
            "The Harappan humped bull appears on Indus seals; zebu cattle are common across Pakistan."
        } else {
            "Harappan bull Wadi-e-Sindh ki Tehzeeb ki mohron par tha; zebu nasalain Pakistan bhar mein pāyi jati hain."
        }
        "avatar_05" -> if (isEnglish) {
            "Himalayan ibex is found in Gilgit-Baltistan, Chitral, and northern high valleys."
        } else {
            "Himalayan ibex Gilgit-Baltistan, Chitral aur shimali buland waadiyon mein pāya jata hai."
        }
        "avatar_06" -> if (isEnglish) {
            "Mugger crocodile is found around the Indus, Haleji, and Balochistan water bodies."
        } else {
            "Daryā-e-Sindh magar machh, Haleji aur Balochistan ke pani walay ilaqon mein pāya jata hai."
        }
        "avatar_07" -> if (isEnglish) {
            "Sindhi pangolin is found in Sindh, Punjab, Balochistan, Thar, and Cholistan."
        } else {
            "Sindhi pangolin Sindh, Punjab, Balochistan, Thar aur Cholistan mein pāya jata hai."
        }
        "avatar_08" -> if (isEnglish) {
            "Chinkara is found in Cholistan, Thar, Nara, and Pakistan's dry plains."
        } else {
            "Chinkara Cholistan, Thar, Nara registan aur sookhe maidano mein pāyi jati hai."
        }
        "avatar_09" -> if (isEnglish) {
            "Sivatherium is prehistoric, with fossils from the Upper Siwaliks of western Punjab."
        } else {
            "Sivatherium qadeem tha; is ke fossils western Punjab ke Upper Siwaliks se milay hain."
        }
        "avatar_10" -> if (isEnglish) {
            "Indohyus is prehistoric, with fossils linked to the Kashmir region."
        } else {
            "Indohyus qadeem tha; is ke fossils Kashmir region se mansub hain."
        }
        "avatar_11" -> if (isEnglish) {
            "Stegodon is prehistoric, known from Siwalik and Potwar fossil beds."
        } else {
            "Stegodon qadeem tha; is ke fossils Siwalik aur Potwar deposits se milay hain."
        }
        "avatar_12" -> if (isEnglish) {
            "Aurochs once lived around the Indus plains before domestic cattle took over."
        } else {
            "Aurochs kabhi Daryā-e-Sindh ke maidano mein pāya jata tha, domestic cattle se pehle."
        }
        "avatar_13" -> if (isEnglish) {
            "Asiatic cheetahs once roamed Balochistan, Sindh, and Punjab; none remain in Pakistan."
        } else {
            "Asiatic cheetah kabhi Balochistan, Sindh aur Punjab mein pāya jata tha; ab Pakistan mein nahi pāya jata."
        }
        "avatar_14" -> if (isEnglish) {
            "Pallas's cat is found in the Hindu Kush and Hindu Raj ranges."
        } else {
            "Pallas's cat Hindu Kush aur Hindu Raj pahari silsilay mein pāyi jati hai."
        }
        "avatar_15" -> if (isEnglish) {
            "The Kashmir flying squirrel is found around Kashmir and northern mountain forests."
        } else {
            "Kashmir flying squirrel Kashmir aur shimali pahari junglat mein pāyi jati hai."
        }
        "avatar_16" -> if (isEnglish) {
            "Honey badger is found in Sindh, south Punjab, Balochistan, and desert belts."
        } else {
            "Bijju Sindh, junubi Punjab, Balochistan aur registani ilaqon mein pāya jata hai."
        }
        "avatar_17" -> if (isEnglish) {
            "Gharial once lived in the Indus River; it is now gone from Pakistan."
        } else {
            "Gharial kabhi Daryā-e-Sindh ke nizaam mein pāya jata tha; ab Pakistan mein nahi pāya jata."
        }
        "avatar_18" -> if (isEnglish) {
            "Argali is found around Khunjerab and the Pamir-edge ranges."
        } else {
            "Argali Khunjerab aur Pamir ke qareebi paharon mein pāyi jati hai."
        }
        "avatar_19" -> if (isEnglish) {
            "Red pandas belong to the eastern Himalayas; no Pakistan population is known."
        } else {
            "Red panda mashriqi Himalaya ka janwar hai; Pakistan mein nahi pāya jata."
        }
        "avatar_20" -> if (isEnglish) {
            "Fishing cat is linked to Sindh's Indus Delta and river areas."
        } else {
            "Fishing cat Daryā-e-Sindh ke delta, Sindh ke daryai ilaqon aur sar-kanday wali jagon mein pāyi jati hai."
        }
        "avatar_21" -> if (isEnglish) {
            "Blackbuck were historically native to Cholistan and Thar."
        } else {
            "Blackbuck tareekhi tor par Cholistan aur Thar mein pāya jane wala hiran tha."
        }
        "avatar_22" -> if (isEnglish) {
            "Barasingha once lived in Punjab river areas; it is now gone from Pakistan."
        } else {
            "Barasingha kabhi Punjab ke daryai ilaqon mein pāya jata tha; ab Pakistan mein nahi pāya jata."
        }
        "avatar_23" -> if (isEnglish) {
            "Himalayan monal is found in Neelum Valley, Kohistan, and Gilgit-Baltistan forests."
        } else {
            "Himalayan monal Neelum Valley, Kohistan aur Gilgit-Baltistan ke junglat mein pāya jata hai."
        }
        "avatar_24" -> if (isEnglish) {
            "Lammergeier is found in Gilgit-Baltistan, Chitral, Karakoram, and Hindu Kush."
        } else {
            "Lammergeier Gilgit-Baltistan, Chitral, Karakoram aur Hindu Kush mein pāya jata hai."
        }
        "avatar_25" -> if (isEnglish) {
            "Golden mahseer is found in Kashmir, KP, and Gilgit-Baltistan's cold rivers."
        } else {
            "Golden mahseer Kashmir, KP aur Gilgit-Baltistan ke thande daryaon mein pāyi jati hai."
        }
        "avatar_26" -> if (isEnglish) {
            "The Harappan unicorn was an Indus symbol found on seals from Pakistan."
        } else {
            "Harappan unicorn Wadi-e-Sindh ki Tehzeeb ka nishan tha, Pakistan se milne wali mohron par nazar aata hai."
        }
        "avatar_27" -> if (isEnglish) {
            "Harappan Tiger links Indus imagery with old river forests of Punjab and Sindh."
        } else {
            "Harappan Tiger Wadi-e-Sindh ki Tehzeeb ke nishanon se juda hai; Punjab aur Sindh ke purane daryai junglat ka bagh."
        }
        "avatar_28" -> if (isEnglish) {
            "Babbar sher once ranged around the Indus region; no lions remain in Pakistan's wild."
        } else {
            "Babbar sher kabhi Daryā-e-Sindh ke wasee ilaqay mein pāya jata tha; ab Pakistan mein jangli sher nahi pāye jate."
        }
        "avatar_29" -> if (isEnglish) {
            "Bully Kutta is a regional dog breed from Punjab and Sindh."
        } else {
            "Bully Kutta Punjab aur Sindh se judi maqami kutte ki nasal hai."
        }
        "avatar_30" -> if (isEnglish) {
            "Indus water buffalo is common around the Indus plains of Punjab and Sindh."
        } else {
            "Daryā-e-Sindh ki bhains Punjab aur Sindh ke daryai maidano mein aam pāyi jati hai."
        }
        else -> if (isEnglish) {
            "Choose an avatar connected to Pakistan's wildlife and history."
        } else {
            "Pakistan ki wildlife aur history se juda avatar chunein."
        }
    }
}

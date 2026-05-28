"""Configuration: deny-lists, keyword patterns, severity thresholds.

The deny-lists are intentionally additive and easy to tune. Severity is a
small enum so the dashboard popup and the activity log can render different
visuals for low/medium/high-risk weeks without re-classifying anything.

The deny-list genres lean on the **Spotify Web API** convention that artist
genres come back as lowercase slug strings — ``black metal``,
``depressive black metal``, ``post-rock``, etc. — so a simple substring check
catches families ("doom metal", "funeral doom", "depressive doom"). For
title / track-name keywords we use both English and Roman-Urdu spellings
since the user demographic is Pakistani youth.
"""

from __future__ import annotations

from dataclasses import dataclass







LISTENING_DATA_NAMESPACE: dict[str, str] = {
    "flagged_tracks": "activity_log_flags",
    "weekly_reports": "weekly_reports",
    "user_dismissals": "weekly_report_dismissals",  
}

















DENY_LIST_GENRES: tuple[str, ...] = (
    
    "depressive black metal",
    "depressive suicidal black metal",
    "dsbm",
    "blackgaze",
    "raw black metal",
    "atmospheric black metal",
    
    "funeral doom",
    "death doom",
    "sludge metal",
    "stoner doom",
    "drone metal",
    
    "brutal death metal",
    "slam death metal",
    "goregrind",
    
    
    "darkwave",
    "witch house",
    "post-black",
    "screamo",
    "emo violence",
    "depressive rock",
    
    "psychedelic doom",
    "stoner rock",  
)














DENY_LIST_KEYWORDS_EN: tuple[str, ...] = (
    
    r"\bsuicide\b",
    r"\bkill myself\b",
    r"\bkill yourself\b",
    r"\bend my life\b",
    r"\bhang myself\b",
    r"\bself[\s-]?harm\b",
    r"\bcutting myself\b",
    r"\bblade(?:s)?\s+(?:to|on)\s+(?:my )?(?:wrist|arm|skin)\b",
    r"\boverdose\b",
    r"\bod(?:'d| myself)\b",
    
    r"\bheroin\b",
    r"\bcocaine\b",
    r"\bcrystal meth\b",
    r"\bmethamphetamine\b",
    r"\bmolly\b(?!\s+ringwald)",   
    r"\bxanax\b",
    r"\bpercocet\b",
    r"\boxy(?:contin)?\b",
    r"\bsizzurp\b",
    r"\bcodeine\b",
    r"\bfent(?:anyl)?\b",
    r"\b(?:fix(?:in|ing)?|score|cop)\s+(?:dope|smack|gear|drugs?)\b",
    
    r"\bworthless\b",
    r"\bcan'?t go on\b",
    r"\bgive up on (?:life|living)\b",
    r"\beverything is dark\b",
    r"\bnothing matters\b",
)


DENY_LIST_KEYWORDS_UR: tuple[str, ...] = (
    r"\bkhudkushi\b",
    r"\bjaan\s+dena\b",
    r"\bmarna\s+chahta\b",
    r"\bchitta\b",
    r"\bcharas\b",
    r"\bnasha\b",
    r"\bafeem\b",
    r"\baiis\b",
    r"\bayis\b",
    r"\bbarbaad\b",
    r"\bgham\b",
    r"\bjudai\b",
    r"خودکشی",
    r"چٹا",
    r"چرس",
    r"افیون",
    r"نشہ",
)







@dataclass(frozen=True)
class SeverityWeights:
    """Per-signal contribution to the track-level severity score."""

    deny_list_genre: float = 0.45
    title_keyword_self_harm: float = 0.65
    title_keyword_drug: float = 0.50
    title_keyword_depression: float = 0.30
    
    
    
    very_low_valence: float = 0.20
    very_low_energy_and_valence: float = 0.30


DEFAULT_SEVERITY_WEIGHTS = SeverityWeights()




FLAG_THRESHOLD: float = 0.40


WEEKLY_MEDIUM_THRESHOLD: float = 0.15
WEEKLY_HIGH_THRESHOLD: float = 0.30

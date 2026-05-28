"""Sahara Risk — per-user unsupervised weekly risk-score adjustment.

The model:

  * Activates **one week after the user completes the DAST-10/20 assessment**
    and runs **weekly for 26 weeks** (6 months).
  * Maintains a per-user latent risk score in ``[0, 1]`` initialised from
    the DAST score and updated each week from this week's activity logs.
  * Is **unsupervised**: no global labels; each user is their own baseline.
    Weekly evidence is normalised against the user's own running mean and
    std for each feature, so a heavy charas listener is judged against
    their own baseline, not the population's.
  * Cannot push the risk **below** the running floor too fast — recovery
    points from the gamified tracker can push it back up if the user
    games the inputs, and a calibration period (weeks 1–4) limits how
    much a single week of evidence can move the score.

Public surface — pure-Python pieces only. Modal-deploy / Firestore-reader
modules import lazily so the package stays testable without those deps.
"""

from .config import (
    DEFAULT_RISK_FROM_DAST,
    MONITORING_WINDOW_WEEKS,
    EWMA_ALPHA_SCHEDULE,
    FeatureWeights,
    DEFAULT_FEATURE_WEIGHTS,
)
from .features import (
    WeeklyActivitySnapshot,
    WeeklyFeatureVector,
    DEFAULT_SLEEP_HOURS,
)
from .model import (
    UserRiskProfile,
    WeeklyRiskUpdate,
    initialise_profile_from_dast,
    update_weekly,
)
from .monitoring import (
    MonitoringPeriod,
    build_monitoring_period,
    is_inside_monitoring,
    weeks_remaining,
)

__all__ = [
    "DEFAULT_RISK_FROM_DAST",
    "MONITORING_WINDOW_WEEKS",
    "EWMA_ALPHA_SCHEDULE",
    "FeatureWeights",
    "DEFAULT_FEATURE_WEIGHTS",
    "WeeklyActivitySnapshot",
    "WeeklyFeatureVector",
    "DEFAULT_SLEEP_HOURS",
    "UserRiskProfile",
    "WeeklyRiskUpdate",
    "initialise_profile_from_dast",
    "update_weekly",
    "MonitoringPeriod",
    "build_monitoring_period",
    "is_inside_monitoring",
    "weeks_remaining",
]

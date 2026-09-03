import logging
import os

from celery.schedules import crontab
from flask_appbuilder.security.manager import AUTH_DB, AUTH_OAUTH
from flask_caching.backends.filesystemcache import FileSystemCache

from keycloak_security_manager import KeycloakSecurityManager


def get_env_variable(name: str, default: str | None = None) -> str:
    value = os.getenv(name, default)
    if value is None:
        raise RuntimeError(f"Required environment variable {name} is not set")
    return value


def get_env_boolean(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.lower() in {"1", "true", "yes", "on"}


DATABASE_DIALECT = get_env_variable("DATABASE_DIALECT")
DATABASE_USER = get_env_variable("DATABASE_USER")
DATABASE_PASSWORD = get_env_variable("DATABASE_PASSWORD")
DATABASE_HOST = get_env_variable("DATABASE_HOST")
DATABASE_PORT = get_env_variable("DATABASE_PORT")
DATABASE_DB = get_env_variable("DATABASE_DB")

SQLALCHEMY_DATABASE_URI = (
    f"{DATABASE_DIALECT}://{DATABASE_USER}:{DATABASE_PASSWORD}"
    f"@{DATABASE_HOST}:{DATABASE_PORT}/{DATABASE_DB}"
)

REDIS_HOST = get_env_variable("REDIS_HOST", "redis")
REDIS_PORT = get_env_variable("REDIS_PORT", "6379")
REDIS_CELERY_DB = get_env_variable("REDIS_CELERY_DB", "0")
REDIS_RESULTS_DB = get_env_variable("REDIS_RESULTS_DB", "1")

RESULTS_BACKEND = FileSystemCache("/app/superset_home/sqllab")
CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 300,
    "CACHE_KEY_PREFIX": "superset_",
    "CACHE_REDIS_HOST": REDIS_HOST,
    "CACHE_REDIS_PORT": REDIS_PORT,
    "CACHE_REDIS_DB": REDIS_RESULTS_DB,
}
DATA_CACHE_CONFIG = CACHE_CONFIG
THUMBNAIL_CACHE_CONFIG = CACHE_CONFIG


class CeleryConfig:
    broker_url = f"redis://{REDIS_HOST}:{REDIS_PORT}/{REDIS_CELERY_DB}"
    imports = (
        "superset.sql_lab",
        "superset.tasks.scheduler",
        "superset.tasks.thumbnails",
        "superset.tasks.cache",
    )
    result_backend = f"redis://{REDIS_HOST}:{REDIS_PORT}/{REDIS_RESULTS_DB}"
    worker_prefetch_multiplier = 1
    task_acks_late = False
    beat_schedule = {
        "reports.scheduler": {
            "task": "reports.scheduler",
            "schedule": crontab(minute="*", hour="*"),
        },
        "reports.prune_log": {
            "task": "reports.prune_log",
            "schedule": crontab(minute=10, hour=0),
        },
    }


CELERY_CONFIG = CeleryConfig
SECRET_KEY = get_env_variable("SECRET_KEY")
BABEL_DEFAULT_LOCALE = get_env_variable("BABEL_DEFAULT_LOCALE", "en")
LANGUAGES = {
    "en": {"flag": "us", "name": "English"},
    # Browsers commonly send en-US, which Flask-Babel normalizes to en_US.
    # Superset 6.1's navbar expects every negotiated locale in this mapping.
    "en_US": {"flag": "us", "name": "English"},
}
PUBLIC_ROLE_LIKE = get_env_variable("PUBLIC_ROLE_LIKE", "Gamma")
SQLLAB_CTAS_NO_LIMIT = True
RATELIMIT_STORAGE_URI = f"redis://{REDIS_HOST}:{REDIS_PORT}/2"

FEATURE_FLAGS = {
    "ALERT_REPORTS": True,
    "EMBEDDED_SUPERSET": get_env_boolean("SUPERSET_FEATURE_EMBEDDED_SUPERSET"),
}
ALERT_REPORTS_NOTIFICATION_DRY_RUN = True
WEBDRIVER_BASEURL = "http://superset:8088/"
WEBDRIVER_BASEURL_USER_FRIENDLY = "http://localhost:8088/"

log_level_text = get_env_variable("SUPERSET_LOG_LEVEL", "INFO")
LOG_LEVEL = getattr(logging, log_level_text.upper(), logging.INFO)

SUPERSET_AUTH_MODE = get_env_variable("SUPERSET_AUTH_MODE", "oauth").lower()
if SUPERSET_AUTH_MODE == "db":
    AUTH_TYPE = AUTH_DB
elif SUPERSET_AUTH_MODE == "oauth":
    AUTH_TYPE = AUTH_OAUTH
    OAUTH_PROVIDERS = [
        {
            "name": "keycloak",
            "token_key": "access_token",
            "icon": "fa-key",
            "remote_app": {
                "client_id": get_env_variable("OAUTH_CLIENT_ID"),
                "client_secret": get_env_variable("OAUTH_CLIENT_SECRET"),
                "client_kwargs": {"scope": "openid profile email"},
                "server_metadata_url": get_env_variable("OAUTH_METADATA_URL"),
            },
        }
    ]
    CUSTOM_SECURITY_MANAGER = KeycloakSecurityManager
    AUTH_USER_REGISTRATION = True
    AUTH_USER_REGISTRATION_ROLE = "Gamma"
    AUTH_ROLES_SYNC_AT_LOGIN = True
    AUTH_ROLES_MAPPING = {
        "Admin": ["Admin"],
        "Alpha": ["Alpha"],
        "Gamma": ["Gamma"],
    }
else:
    raise RuntimeError(
        "SUPERSET_AUTH_MODE must be either 'db' or 'oauth', "
        f"not {SUPERSET_AUTH_MODE!r}"
    )

import logging
import os

from superset.security import SupersetSecurityManager


logger = logging.getLogger(__name__)


class KeycloakSecurityManager(SupersetSecurityManager):
    def oauth_user_info(self, provider, response=None):
        if provider != "keycloak":
            return {}

        remote = self.appbuilder.sm.oauth_remotes[provider]
        userinfo_response = remote.get(os.environ["OAUTH_USERINFO_URL"])
        userinfo_response.raise_for_status()
        userinfo = userinfo_response.json()
        logger.debug("Received Keycloak user information for OAuth login")

        return {
            "username": userinfo.get("preferred_username", ""),
            "first_name": userinfo.get("given_name", ""),
            "last_name": userinfo.get("family_name", ""),
            "email": userinfo.get("email", ""),
            "role_keys": userinfo.get("roles", []),
        }

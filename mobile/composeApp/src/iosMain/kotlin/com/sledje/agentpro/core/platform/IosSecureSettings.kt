package com.sledje.agentpro.core.platform

/** iOS phase: the Keychain (DECISIONS #44). Until then the seam isn't implemented. */
class IosSecureSettings : SecureSettings {
    override suspend fun get(key: String): String? = throw NotImplementedError("iOS phase: Keychain")

    override suspend fun put(key: String, value: String) = throw NotImplementedError("iOS phase: Keychain")

    override suspend fun remove(key: String) = throw NotImplementedError("iOS phase: Keychain")
}

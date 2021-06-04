package io.beatmaps.common.beatsaver

interface IUserVerify {
    suspend fun validateUser(toCheck: List<String>, userHash: String): String?
}

interface IUserVerifyProvider {
    fun create(): IUserVerify
}

object UserNotVerified : IUserVerify {
    override suspend fun validateUser(toCheck: List<String>, userHash: String): String? = null
}
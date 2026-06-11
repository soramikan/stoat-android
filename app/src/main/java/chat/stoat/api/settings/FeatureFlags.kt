package chat.stoat.api.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

annotation class FeatureFlag(val name: String)
annotation class Treatment(val description: String)

@FeatureFlag("UserCards")
sealed class UserCardsVariates {
    @Treatment(
        "Enable user cards for all users"
    )
    object Enabled : UserCardsVariates()

    @Treatment(
        "Enable user cards for users that meet certain or all criteria (implementation-specific)"
    )
    data class Restricted(val predicate: () -> Boolean) : UserCardsVariates()
}

object FeatureFlags {
    @FeatureFlag("UserCards")
    var userCards by mutableStateOf<UserCardsVariates>(
        UserCardsVariates.Enabled
    )

    val userCardsGranted: Boolean
        get() = when (userCards) {
            is UserCardsVariates.Enabled -> true
            is UserCardsVariates.Restricted -> (userCards as UserCardsVariates.Restricted).predicate()
        }

}

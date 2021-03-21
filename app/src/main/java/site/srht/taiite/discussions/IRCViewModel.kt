package site.srht.taiite.discussions

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import site.srht.taiite.discussions.irc.IRCSessionParams
import site.srht.taiite.discussions.irc.IRCState
import site.srht.taiite.discussions.irc.SASLPlain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Preferences
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(context: Context) {
    companion object {
        private val ONBOARDED = booleanPreferencesKey("onboarded")
        private val SERVER_ADDRESS = stringPreferencesKey("server_address")
        private val SERVER_PORT = intPreferencesKey("server_port")
        private val SERVER_INSECURE = booleanPreferencesKey("server_insecure")
        private val NICKNAME = stringPreferencesKey("nickname")
        private val PASSWORD = stringPreferencesKey("password")
    }

    private val dataStore = context.dataStore
    val onboarded: Flow<Boolean> = this.dataStore.data.map { it[ONBOARDED] ?: false }
    val clientParams: Flow<IRCClientParams?> = this.dataStore.data.map {
        if (it[ONBOARDED] == true) {
            val nickname = it[NICKNAME]!!
            val password = it[PASSWORD]!!
            val auth = if (password.isEmpty()) null else SASLPlain(nickname, password)
            IRCClientParams(
                serverAddress = it[SERVER_ADDRESS]!!,
                serverPort = it[SERVER_PORT]!!,
                serverInsecure = it[SERVER_INSECURE]!!,
                sessionParams = IRCSessionParams(
                    nickname = nickname,
                    username = nickname,
                    realName = nickname,
                    auth = auth,
                ),
            )
        } else {
            null
        }
    }

    suspend fun onboard(
        serverAddress: String,
        serverPort: Int,
        nickname: String,
        password: String
    ) {
        this.dataStore.edit { preferences ->
            preferences[ONBOARDED] = true
            preferences[SERVER_ADDRESS] = serverAddress
            preferences[SERVER_PORT] = serverPort
            preferences[SERVER_INSECURE] = false
            preferences[NICKNAME] = nickname
            preferences[PASSWORD] = password
        }
    }
}

sealed class Screen {
    object Home : Screen()
    class Channel(val name: String) : Screen()
}

class IRCViewModel : ViewModel() {
    // To be accessible only from the main thread.
    val ircState: MutableLiveData<IRCState?> = MutableLiveData(null)

    // UI State
    private val _currentScreen: MutableLiveData<Screen> = MutableLiveData(Screen.Home)
    val currentScreen: LiveData<Screen> = _currentScreen

    fun openChannel(name: String) {
        val channel = this.ircState.value?.getChannel(name)
        if (channel != null) {
            this._currentScreen.value = Screen.Channel(name)
        }
    }

    fun closeChannel() {
        this._currentScreen.value = Screen.Home
    }
}
package com.example.ytclient.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ytclient.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GoogleUser(val name: String, val email: String, val photoUrl: String?)

sealed class LoginError : Exception() {
    data object NotConfigured : LoginError()
    data object NoAccount : LoginError()
    data object Cancelled : LoginError()
    data object Failed : LoginError()
}

private val Context.accountStore: DataStore<Preferences> by preferencesDataStore("ytmaterial_account")

/** "Sign in with Google" through Credential Manager; only the public profile is stored locally. */
object AccountManager {
    private val KEY_NAME = stringPreferencesKey("name")
    private val KEY_EMAIL = stringPreferencesKey("email")
    private val KEY_PHOTO = stringPreferencesKey("photo")

    private lateinit var store: DataStore<Preferences>

    fun init(context: Context) {
        store = context.applicationContext.accountStore
    }

    val user: Flow<GoogleUser?> by lazy {
        store.data.map { p ->
            val email = p[KEY_EMAIL]
            if (email.isNullOrBlank()) null
            else GoogleUser(p[KEY_NAME].orEmpty(), email, p[KEY_PHOTO])
        }
    }

    /** [activityContext] must be an Activity context so the account picker can be shown. */
    suspend fun signIn(activityContext: Context): Result<GoogleUser> {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            return Result.failure(LoginError.NotConfigured)
        }
        return try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val g = GoogleIdTokenCredential.createFrom(credential.data)
                val user = GoogleUser(
                    name = g.displayName ?: g.givenName ?: g.id,
                    email = g.id,
                    photoUrl = g.profilePictureUri?.toString()
                )
                store.edit {
                    it[KEY_NAME] = user.name
                    it[KEY_EMAIL] = user.email
                    if (user.photoUrl != null) it[KEY_PHOTO] = user.photoUrl else it.remove(KEY_PHOTO)
                }
                Result.success(user)
            } else {
                Result.failure(LoginError.Failed)
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(LoginError.Cancelled)
        } catch (e: NoCredentialException) {
            Result.failure(LoginError.NoAccount)
        } catch (e: GetCredentialException) {
            Result.failure(LoginError.Failed)
        } catch (e: Exception) {
            Result.failure(LoginError.Failed)
        }
    }

    suspend fun signOut(context: Context) {
        store.edit { it.clear() }
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}

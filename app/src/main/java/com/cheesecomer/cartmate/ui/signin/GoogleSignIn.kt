package com.cheesecomer.cartmate.ui.signin

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.cheesecomer.cartmate.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Credential Manager による Google ログイン（docs/design/architecture.md「認証」） */
object GoogleSignIn {

    /**
     * Google のアカウント選択を表示し、ID トークンを返す。利用者が閉じた場合は [Result.Cancelled]。
     * [context] は Activity（アカウント選択の画面を出すため）
     */
    suspend fun requestIdToken(context: Context): Result {
        val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Result.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.Failed(IllegalStateException("Google の ID トークンではない認証情報: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            // 端末に Google アカウントがない
            Result.Failed(e)
        } catch (e: GetCredentialException) {
            Result.Failed(e)
        }
    }

    /** ログアウトのときに、Credential Manager に残っているログインの状態も消す */
    suspend fun clear(context: Context) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    sealed interface Result {
        data class Success(val idToken: String) : Result
        data object Cancelled : Result
        data class Failed(val cause: Throwable) : Result
    }
}

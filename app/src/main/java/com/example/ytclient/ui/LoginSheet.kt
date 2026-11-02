package com.example.ytclient.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytclient.R
import com.example.ytclient.auth.AuthManager
import com.example.ytclient.auth.DeviceCode
import com.example.ytclient.auth.DeviceCodeAuth
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

private sealed interface LoginState {
    data object Loading : LoginState
    data class ShowCode(val code: DeviceCode) : LoginState
    data object Success : LoginState
    data class Failed(val message: String) : LoginState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<LoginState>(LoginState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        state = LoginState.Loading
        try {
            val code = DeviceCodeAuth.requestCode()
            state = LoginState.ShowCode(code)
            val tokens = DeviceCodeAuth.awaitTokens(code)
            AuthManager.save(tokens)
            state = LoginState.Success
            delay(1300)
            onDismiss()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = LoginState.Failed(e.message ?: "Error")
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut()
            },
            contentKey = { it::class },
            label = "login"
        ) { s ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    stringResource(R.string.login_title),
                    style = MaterialTheme.typography.titleLarge
                )
                when (s) {
                    LoginState.Loading -> Box(Modifier.padding(24.dp)) { LoadingIndicator() }

                    is LoginState.ShowCode -> {
                        Text(
                            stringResource(R.string.login_step),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                s.code.userCode,
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                letterSpacing = 3.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(s.code.verificationUrl))
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null)
                            Text(
                                "  " + stringResource(R.string.login_open),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("code", s.code.userCode))
                                Toast.makeText(
                                    context, R.string.login_copied, Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Text(
                                "  " + stringResource(R.string.login_copy),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Text(
                            stringResource(R.string.login_waiting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LoginState.Success -> {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                        Text(stringResource(R.string.login_success))
                    }

                    is LoginState.Failed -> {
                        Text(
                            stringResource(R.string.login_failed),
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
        }
    }
}

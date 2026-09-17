package com.amurcanov.tgwsproxy.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.Send
import android.content.ActivityNotFoundException
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amurcanov.tgwsproxy.BuildConfig
import com.amurcanov.tgwsproxy.ProxyController
import com.amurcanov.tgwsproxy.ProxyService
import com.amurcanov.tgwsproxy.SettingsStore
import com.amurcanov.tgwsproxy.R
import kotlinx.coroutines.launch

@Composable
fun ConnectionTab(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val isRunning by ProxyService.isRunning.collectAsStateWithLifecycle()
    val isVerifiedRunning by ProxyService.isVerifiedRunning.collectAsStateWithLifecycle()

    val isReady by settingsStore.isReady.collectAsStateWithLifecycle(initialValue = false)

    // Settings
    val savedPort by settingsStore.port.collectAsStateWithLifecycle(initialValue = "1443")
    val savedBindIp by settingsStore.bindIp.collectAsStateWithLifecycle(initialValue = "127.0.0.1")
    val savedCfEnabled by settingsStore.cfproxyEnabled.collectAsStateWithLifecycle(initialValue = true)
    val savedPoolSize by settingsStore.poolSize.collectAsStateWithLifecycle(initialValue = 4)
    val savedSecretKey by settingsStore.secretKey.collectAsStateWithLifecycle(initialValue = "LOADING")

    val scope = rememberCoroutineScope()
    val currentVersion = remember { "v${BuildConfig.VERSION_NAME.removePrefix("v")}" }

    if (!isReady || savedSecretKey == "LOADING") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
        return
    }

    // Auto-generate secret if empty
    LaunchedEffect(savedSecretKey) {
        if (savedSecretKey == "") {
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            val generated = bytes.joinToString("") { "%02x".format(it) }
            scope.launch { settingsStore.saveSecretKey(generated) }
        }
    }

    var isStarting by remember { mutableStateOf(false) }
    val statusText = when {
        isVerifiedRunning -> stringResource(R.string.status_connected)
        isStarting || isRunning -> stringResource(R.string.status_connecting)
        else -> stringResource(R.string.status_disconnected)
    }

    LaunchedEffect(isRunning, isVerifiedRunning) {
        if (isVerifiedRunning || !isRunning) {
            isStarting = false
        }
    }

    val port = savedPort.toIntOrNull() ?: 1443
    val secretForUrl = remember(savedSecretKey) {
        val raw = savedSecretKey.trim()
        if (raw.isNotEmpty() && raw != "LOADING") raw else "00000000000000000000000000000000"
    }
    val bindIp = savedBindIp.trim().takeIf { it.isNotEmpty() } ?: "127.0.0.1"
    val proxyUrl = "https://t.me/proxy?server=$bindIp&port=$port&secret=dd$secretForUrl"
    

    val connectAction = {
        if (!isRunning && !isStarting) {
            isStarting = true
            scope.launch {
                val started = ProxyController.startFromSavedSettings(
                    context = context,
                    showInvalidPortToast = true
                )
                if (!started) {
                    isStarting = false
                }
            }
        }
    }

    val disconnectAction = {
        if (isRunning || isStarting) {
            ProxyController.stop(context)
        }
    }

    val isActiveVisual = isRunning || isStarting
    val logoScale by animateFloatAsState(
        targetValue = if (isActiveVisual) 1.12f else 0.94f,
        animationSpec = tween(durationMillis = 650, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "logo_scale"
    )
    val verifiedReveal by animateFloatAsState(
        targetValue = if (isVerifiedRunning) 1f else 0f,
        animationSpec = if (isVerifiedRunning) {
            tween(durationMillis = 620, easing = FastOutSlowInEasing)
        } else {
            snap()
        },
        label = "verified_logo_reveal"
    )
    val logoInteractionSource = remember { MutableInteractionSource() }
    val statusColor by animateColorAsState(
        targetValue = if (isVerifiedRunning) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "connection_status_color"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 0.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.section_launch),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        ChannelBanner()

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AppSectionCard(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(40.dp))
                            .clickable(
                                interactionSource = logoInteractionSource,
                                indication = null,
                                onClick = if (isActiveVisual) disconnectAction else connectAction
                            )
                            .scale(logoScale)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_telegram_logo),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                            alpha = 0.52f
                        )
                        Image(
                            painter = painterResource(id = R.drawable.ic_telegram_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    if (verifiedReveal > 0f) {
                                        val radius = maxOf(size.width, size.height) * verifiedReveal
                                        val revealPath = Path().apply {
                                            addOval(
                                                Rect(
                                                    left = center.x - radius,
                                                    top = center.y - radius,
                                                    right = center.x + radius,
                                                    bottom = center.y + radius
                                                )
                                            )
                                        }
                                        clipPath(revealPath) { this@drawWithContent.drawContent() }
                                    }
                                }
                        )
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                openProxyInTelegram(context, bindIp, port, "dd$secretForUrl", proxyUrl)
                            },
                            enabled = isRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.apply_in_telegram),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        ProxyStatusPanel(
                            cfEnabled = savedCfEnabled,
                            poolSize = savedPoolSize,
                            port = savedPort,
                            version = currentVersion
                        )

                        Surface(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cb.setPrimaryClip(android.content.ClipData.newPlainText("Proxy", proxyUrl))
                                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = proxyUrl,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.copy),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyStatusPanel(
    cfEnabled: Boolean,
    poolSize: Int,
    port: String,
    version: String
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProxyStatusItem(
                text = if (cfEnabled) "CF" else stringResource(R.string.direct_mode),
                modifier = Modifier
                    .weight(0.9f)
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            )
            ProxyStatusDivider()
            ProxyStatusItem(
                text = stringResource(R.string.pool_short, poolSize),
                modifier = Modifier
                    .weight(1.05f)
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            )
            ProxyStatusDivider()
            ProxyStatusItem(
                text = stringResource(R.string.port_short, port),
                modifier = Modifier
                    .weight(1.35f)
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            )
            ProxyStatusDivider()
            ProxyStatusItem(
                text = version,
                modifier = Modifier
                    .weight(1.1f)
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ProxyStatusItem(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProxyStatusDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    )
}

private const val CHANNEL_USERNAME = "parsv2r"

/**
 * Hands the proxy to whatever Telegram client is installed. A plain tg:// intent
 * reaches every client (including org.telegram.messenger.web, the build from
 * telegram.org that most people in Iran have), and Android shows its own picker
 * when there is more than one. The old version only looked for a fixed list of
 * package names and found nothing on phones with the website build.
 */
private fun openProxyInTelegram(context: Context, server: String, port: Int, secret: String, httpsUrl: String) {
    val tgUri = Uri.parse("tg://proxy?server=$server&port=$port&secret=$secret")
    val direct = Intent(Intent.ACTION_VIEW, tgUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(direct)
        return
    } catch (_: ActivityNotFoundException) {
    } catch (_: Exception) {
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(httpsUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.clients_not_found), Toast.LENGTH_SHORT).show()
    }
}

private fun openChannel(context: Context) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$CHANNEL_USERNAME"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(app)
        return
    } catch (_: Exception) {
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$CHANNEL_USERNAME"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.clients_not_found), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ChannelBanner() {
    val context = LocalContext.current
    Surface(
        onClick = { openChannel(context) },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_telegram_logo),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.channel_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.channel_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { openChannel(context) },
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.channel_join), fontWeight = FontWeight.Bold)
            }
        }
    }
}

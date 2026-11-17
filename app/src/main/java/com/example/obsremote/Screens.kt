package com.example.obsremote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val LiveRed = Color(0xFFFF5A4F)
private val OnlineGreen = Color(0xFF4CD08A)
private val CardShape = RoundedCornerShape(20.dp)
private val FullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

@Composable
fun ObsRemoteApp(vm: ObsViewModel = viewModel()) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        if (connection is ConnectionState.Connected) {
            ControlScreen(vm)
        } else {
            ConnectScreen(vm, connection)
        }
    }
}

@Composable
fun ConnectScreen(vm: ObsViewModel, state: ConnectionState) {
    var host by rememberSaveable { mutableStateOf(vm.saved.host) }
    var port by rememberSaveable { mutableStateOf(vm.saved.port) }
    var password by rememberSaveable { mutableStateOf(vm.saved.password) }
    val connecting = state is ConnectionState.Connecting
    val scheme = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Logo()
        Spacer(Modifier.height(20.dp))
        Text(
            "OBS Remote",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Пульт для управления трансляцией",
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(scheme.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("IP-адрес компьютера") },
                placeholder = { Text("192.168.1.5") },
                singleLine = true,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("Порт") },
                singleLine = true,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль (если задан)") },
                singleLine = true,
                shape = fieldShape,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state is ConnectionState.Failed) {
                Text(
                    state.message,
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = { vm.connect(host, port, password) },
                enabled = !connecting && host.isNotBlank(),
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    if (connecting) "Подключение…" else "Подключиться",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "В OBS откройте Сервис → Настройки WebSocket-сервера и включите сервер",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ControlScreen(vm: ObsViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    Scaffold(
        containerColor = scheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            item(span = FullWidth) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "OBS Remote",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(OnlineGreen, 8.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Подключено",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = vm::disconnect) { Text("Отключиться") }
                }
            }

            item(span = FullWidth) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(
                        title = "Трансляция",
                        status = if (ui.streaming) "В ЭФИРЕ" else "ОСТАНОВЛЕНА",
                        hint = if (ui.streaming) "Нажмите, чтобы остановить" else "Нажмите, чтобы начать",
                        active = ui.streaming,
                        onClick = vm::toggleStream,
                        modifier = Modifier.weight(1f),
                    )
                    ActionCard(
                        title = "Запись",
                        status = if (ui.recording) "ИДЁТ ЗАПИСЬ" else "ВЫКЛЮЧЕНА",
                        hint = if (ui.recording) "Нажмите, чтобы остановить" else "Нажмите, чтобы начать",
                        active = ui.recording,
                        onClick = vm::toggleRecord,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(span = FullWidth) { SectionTitle("Сцены") }
            items(ui.scenes) { scene ->
                SceneTile(
                    name = scene,
                    active = scene == ui.currentScene,
                    onClick = { vm.selectScene(scene) },
                )
            }

            if (ui.inputs.isNotEmpty()) {
                item(span = FullWidth) { SectionTitle("Звук") }
                items(ui.inputs, span = { GridItemSpan(maxLineSpan) }) { input ->
                    AudioRow(input = input, onToggle = { vm.toggleMute(input.name) })
                }
            }
        }
    }
}

@Composable
private fun Logo() {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(LiveRed),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(LiveRed),
            )
        }
    }
}

@Composable
private fun Dot(color: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ActionCard(
    title: String,
    status: String,
    hint: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (active) LiveRed else scheme.onSurfaceVariant

    Column(
        modifier = modifier
            .clip(CardShape)
            .background(if (active) LiveRed.copy(alpha = 0.16f) else scheme.surface)
            .border(1.dp, if (active) LiveRed else Color.Transparent, CardShape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(accent, 10.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SceneTile(name: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(CardShape)
            .background(if (active) scheme.primary else scheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            color = if (active) scheme.onPrimary else scheme.onSurface,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AudioRow(input: AudioInput, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                input.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (input.muted) "Звук выключен" else "Звук включён",
                style = MaterialTheme.typography.bodySmall,
                color = if (input.muted) scheme.error else scheme.onSurfaceVariant,
            )
        }
        Switch(checked = !input.muted, onCheckedChange = { onToggle() })
    }
}

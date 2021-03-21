package site.srht.taiite.discussions.ui

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chrisbanes.accompanist.insets.navigationBarsWithImePadding
import dev.chrisbanes.accompanist.insets.statusBarsPadding
import kotlin.random.Random

@Composable
fun OnboardingScreen(
    onboard: (String, Int, String, String) -> Unit,
) {
    val serverAddress = rememberSaveable { mutableStateOf("example.com") }
    val isServerAddressValid = // NB: IP_ADDRESS doesn't match IPv6 addresses.
        Patterns.IP_ADDRESS.matcher(serverAddress.value).matches() ||
                Patterns.DOMAIN_NAME.matcher(serverAddress.value).matches()
    val serverPort = rememberSaveable { mutableStateOf("6697") }
    val isServerPortValid = serverPort.value.toIntOrNull()?.let { it in 1..65535 } ?: false
    val nickname = rememberSaveable { mutableStateOf("Guest" + Random.nextInt(9999)) }
    val isNicknameValid = nickname.value.isNotBlank()
    val password = rememberSaveable { mutableStateOf("") }
    val isPasswordValid = true
    val isFormValid =
        isServerAddressValid && isServerPortValid && isNicknameValid && isPasswordValid

    Surface(
        color = MaterialTheme.colors.primarySurface.copy(alpha = 0.3f, green = 0.5f), // TODO fix color
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Text(
                text = "Log in to IRC",
                style = MaterialTheme.typography.h3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 32.dp),
            )
            // TODO focus next field on keyboard action "Done".
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                OutlinedTextField(
                    value = serverAddress.value,
                    onValueChange = { serverAddress.value = it },
                    label = { Text("Server address") },
                    isError = !isServerAddressValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = serverPort.value,
                    onValueChange = { serverPort.value = it },
                    label = { Text("Port") },
                    isError = !isServerPortValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .width(86.dp)
                        .padding(start = 4.dp),
                )
            }
            OutlinedTextField(
                value = nickname.value,
                onValueChange = { nickname.value = it },
                label = { Text("Nickname") },
                isError = !isNicknameValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            OutlinedTextField(
                value = password.value,
                onValueChange = { password.value = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            OutlinedButton(
                onClick = {
                    onboard(
                        serverAddress.value,
                        serverPort.value.toInt(),
                        nickname.value,
                        password.value
                    )
                },
                enabled = isFormValid,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsWithImePadding(),
            ) {
                Text("Connect")
            }
        }
    }
}

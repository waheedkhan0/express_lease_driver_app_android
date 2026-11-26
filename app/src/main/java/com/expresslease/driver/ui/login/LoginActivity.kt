package com.expresslease.driver.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expresslease.driver.ui.home.HomeActivity
import com.expresslease.driver.ui.theme.ExpressLeaseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExpressLeaseTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(uiState.navigateToHome) {
                    if (uiState.navigateToHome) {
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    }
                }

                LoginScreen(
                    uiState = uiState,
                    onLogin = { server, db, user, pass ->
                        viewModel.login(server, db, user, pass)
                    },
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onLogin: (String, String, String, String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf("https://expresslease.quicksol.ca") }
    var dbName by remember { mutableStateOf("db2") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF020617))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // Brand
            Text(
                "EXPRESS LEASE",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
            )
            Text(
                "MOTORCYCLES RENTAL LLC",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Driver Companion",
                color = Color(0xFF38BDF8),
                fontSize = 15.sp,
            )

            Spacer(Modifier.height(40.dp))

            // Error banner
            if (uiState.errorMessage != null) {
                Surface(
                    color = Color(0x26EF4444),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        uiState.errorMessage,
                        color = Color(0xFFFCA5A5),
                        modifier = Modifier.padding(14.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Form fields
            LeaseTextField(
                value = serverUrl, onValueChange = { serverUrl = it },
                label = "Odoo Server URL",
                hint = "https://expresslease.ae",
                icon = Icons.Default.Dns,
                keyboardType = KeyboardType.Uri,
            )
            Spacer(Modifier.height(14.dp))
            LeaseTextField(
                value = dbName, onValueChange = { dbName = it },
                label = "Database Name",
                hint = "express_lease_db",
                icon = Icons.Default.Storage,
            )
            Spacer(Modifier.height(14.dp))
            LeaseTextField(
                value = username, onValueChange = { username = it },
                label = "Username / Email",
                hint = "driver@expresslease.ae",
                icon = Icons.Default.Person,
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(14.dp))

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = Color(0xFF94A3B8)) },
                placeholder = { Text("Enter your password", color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF38BDF8)) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = Color(0xFF64748B),
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = leaseTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            Spacer(Modifier.height(32.dp))

            // Login button
            Button(
                onClick = { onLogin(serverUrl, dbName, username, password) },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        "AUTHENTICATE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun LeaseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color(0xFF94A3B8)) },
        placeholder = { Text(hint, color = Color(0xFF64748B)) },
        leadingIcon = { Icon(icon, null, tint = Color(0xFF38BDF8)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        colors = leaseTextFieldColors(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
    )
}

@Composable
fun leaseTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF38BDF8),
    unfocusedBorderColor = Color(0xFF334155),
    focusedContainerColor = Color(0xFF1E293B),
    unfocusedContainerColor = Color(0xFF1E293B),
    cursorColor = Color(0xFF38BDF8),
)

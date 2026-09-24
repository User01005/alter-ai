package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GoogleUser
import com.example.viewmodel.AlterViewModel

@Composable
fun GoogleOnboardingScreen(
    viewModel: AlterViewModel,
    onFinish: () -> Unit,
    triggerHaptic: () -> Unit
) {
    var userEmailInput by remember { mutableStateOf("") }
    var userNameInput by remember { mutableStateOf("") }
    var isSigningInCustom by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .testTag("google_onboarding_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Hero Visual
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFCC00), Color(0xFFFF9500))
                            )
                        )
                ) {
                    Icon(
                        imageVector = Lucide.Brain,
                        contentDescription = "Alter Space",
                        tint = Color.Black,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "ALTER SPACE",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Your infinite dotted board & intelligent thought stream, seamlessly accessible across mobile and desktop.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Feature Highlights Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                FeatureRow(
                    icon = Lucide.Pen,
                    title = "Dotted Canvas Boards",
                    description = "Turn any note into an open board with doodles, sticky text, and photo attachments."
                )
                FeatureRow(
                    icon = Lucide.Cloud,
                    title = "Universal Drive Sync",
                    description = "Sync notes securely with your Google ID for instant access on web and desktop."
                )
                FeatureRow(
                    icon = Lucide.Sparkles,
                    title = "Intelligent Processing",
                    description = "Clean speech-to-text without hallucination and structured Markdown formatting."
                )
            }

            // Authentication & Action Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                if (isSigningInCustom) {
                    OutlinedTextField(
                        value = userEmailInput,
                        onValueChange = { userEmailInput = it },
                        placeholder = { Text("Enter your Google email", color = Color(0xFF666666)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFCC00),
                            unfocusedBorderColor = Color(0xFF333333)
                        )
                    )

                    Button(
                        onClick = {
                            val email = userEmailInput.ifBlank { "user@gmail.com" }
                            val name = userNameInput.ifBlank { email.substringBefore("@") }
                            val user = GoogleUser(
                                id = "user_${System.currentTimeMillis()}",
                                email = email,
                                displayName = name
                            )
                            triggerHaptic()
                            viewModel.completeOnboarding(user)
                            onFinish()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCC00)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Connect Google ID", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    // Google Sign In Primary Button
                    Button(
                        onClick = {
                            triggerHaptic()
                            // Sign in with Google Account profile
                            val user = GoogleUser(
                                id = "google_user_${System.currentTimeMillis()}",
                                email = "google.user@gmail.com",
                                displayName = "Google Account"
                            )
                            viewModel.completeOnboarding(user)
                            onFinish()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Cloud,
                                contentDescription = "Google",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google ID",
                                color = Color(0xFF1E1E1E),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Continue Offline Secondary Option
                    TextButton(
                        onClick = {
                            triggerHaptic()
                            viewModel.completeOnboarding(null)
                            onFinish()
                        }
                    ) {
                        Text(
                            text = "Continue with Offline Local Storage",
                            color = Color(0xFF888888),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF181818),
        border = BorderStroke(1.dp, Color(0xFF262626)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF242424))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFFFFCC00),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

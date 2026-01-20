package com.soomi.baby.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soomi.baby.ui.LocalizedStrings
import com.soomi.baby.ui.LocalizedStrings.Language
import com.soomi.baby.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    initialLanguage: String = "",
    onComplete: (String) -> Unit  // Returns selected language code
) {
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var showLanguageSelection by remember { mutableStateOf(initialLanguage.isEmpty()) }
    
    val strings = LocalizedStrings.getStrings(selectedLanguage)
    
    if (showLanguageSelection) {
        LanguageSelectionScreen(
            onLanguageSelected = { language ->
                selectedLanguage = language.code
                showLanguageSelection = false
            }
        )
    } else {
        OnboardingPagerScreen(
            strings = strings,
            onComplete = { onComplete(selectedLanguage) }
        )
    }
}

@Composable
private fun LanguageSelectionScreen(
    onLanguageSelected: (Language) -> Unit
) {
    var selectedLanguage by remember { mutableStateOf<Language?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Icon/Logo
        Surface(
            color = SoomiPrimary.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Brightness3,
                    contentDescription = null,
                    tint = SoomiPrimary,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "SOOMI",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Language selection title
        Text(
            text = "Select Language / Sprache wählen",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Language options
        Language.entries.forEach { language ->
            LanguageOption(
                language = language,
                isSelected = selectedLanguage == language,
                onClick = { selectedLanguage = language }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Continue button
        Button(
            onClick = { selectedLanguage?.let { onLanguageSelected(it) } },
            enabled = selectedLanguage != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = when (selectedLanguage) {
                    Language.GERMAN -> "Weiter"
                    else -> "Continue"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null)
        }
    }
}

@Composable
private fun LanguageOption(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.flag,
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = language.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingPagerScreen(
    strings: LocalizedStrings.Strings,
    onComplete: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            Icons.Default.Brightness3,
            strings.welcomeTitle,
            strings.welcomeDesc,
            SoomiPrimary
        ),
        OnboardingPage(
            Icons.Default.Hearing,
            strings.smartDetectionTitle,
            strings.smartDetectionDesc,
            SoomiSecondary
        ),
        OnboardingPage(
            Icons.Default.Lock,
            strings.privacyTitle,
            strings.privacyDesc,
            SoomiCalm
        ),
        OnboardingPage(
            Icons.Default.Bedtime,
            strings.readyTitle,
            strings.readyDesc,
            SoomiPrimary
        )
    )
    
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        // Skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (pagerState.currentPage < pages.size - 1) {
                TextButton(onClick = onComplete) {
                    Text(strings.skip)
                }
            }
        }
        
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            OnboardingPageContent(pages[page])
        }
        
        // Page indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isSelected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
        
        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (pagerState.currentPage > 0) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.back)
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            
            Button(
                onClick = {
                    if (pagerState.currentPage == pages.size - 1) {
                        onComplete()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (pagerState.currentPage == pages.size - 1) strings.getStarted
                    else strings.next
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (pagerState.currentPage == pages.size - 1) Icons.Default.Check
                    else Icons.Default.ArrowForward,
                    null
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = page.color.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    page.icon,
                    null,
                    tint = page.color,
                    modifier = Modifier.size(60.dp)
                )
            }
        }
        Spacer(Modifier.height(48.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: androidx.compose.ui.graphics.Color
)

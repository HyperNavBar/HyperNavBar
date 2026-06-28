package com.ianzb.hypernavbar.ui.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ianzb.hypernavbar.R
import com.ianzb.hypernavbar.ui.component.effect.BgEffectBackground
import com.ianzb.hypernavbar.ui.util.BlurredBar
import com.ianzb.hypernavbar.ui.util.ColorBlendToken
import com.ianzb.hypernavbar.ui.util.isInDarkTheme
import com.ianzb.hypernavbar.ui.util.pageContentPadding
import com.ianzb.hypernavbar.ui.util.pageScrollModifiers
import com.ianzb.hypernavbar.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun AboutPageContent(
    openLicensePage: () -> Unit,
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    val scrollProgress by remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }

    val backdrop = rememberBlurBackdrop()
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(backdrop) { derivedStateOf { backdrop != null && scrollProgress == 1f } }

    Scaffold(
        topBar = {
            val barColor = if (blurActive) {
                Color.Transparent
            } else {
                if (collapsed) colorScheme.surface else Color.Transparent
            }
            val titleColor = colorScheme.onSurface.copy(
                alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
            )
            BlurredBar(backdrop, blurActive) {
                SmallTopAppBar(
                    title = stringResource(R.string.about),
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    titleColor = titleColor,
                    defaultWindowInsetsPadding = false,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            AboutContent(
                padding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 0.dp,
                ),
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                lazyListState = lazyListState,
                scrollProgressProvider = { scrollProgress },
                openLicensePage = openLicensePage,
            )
        }
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgressProvider: () -> Float,
    openLicensePage: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val contentBackdrop = rememberBlurBackdrop()
    var blurRadius by remember { mutableFloatStateOf(60f) }
    var noiseCoefficient by remember { mutableFloatStateOf(BlurDefaults.NoiseCoefficient) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }

    val scrollPadding = pageContentPadding(
        padding,
        padding,
        false,
        extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
        extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
    )
    val logoPadding = pageContentPadding(
        padding,
        padding,
        false,
        extraTop = 40.dp,
        extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
        extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
    )

    val isInDark = isInDarkTheme()

    val cardBlend = if (isInDark) ColorBlendToken.Overlay_Thin_Light else ColorBlendToken.Pured_Regular_Light
    val logoBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    val appName = stringResource(R.string.app_name)
    val ctx = LocalContext.current
    val versionName = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
    } catch (_: Exception) { "1.0" }

    BgEffectBackground(
        dynamicBackground = true,
        isFullSize = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier = if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier,
        alpha = { 1f - scrollProgressProvider() },
    ) {
        // Logo area — floating overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = logoPadding.calculateRightPadding(LayoutDirection.Ltr),
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        val iconProgress = ((scrollProgressProvider() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                        clip = true
                        alpha = 1 - iconProgress
                        scaleX = 1 - (iconProgress * 0.05f)
                        scaleY = 1 - (iconProgress * 0.05f)
                    }
                    .squircleClip(cornerRadius = 28.dp),
            ) {
                Image(
                    modifier = Modifier.size(100.dp),
                    painter = painterResource(R.drawable.ic_about_logo),
                    contentDescription = null,
                )
            }
            MiuixText(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        val projectNameProgress = ((scrollProgressProvider() - 0.20f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
                    }
                    .then(
                        if (contentBackdrop != null) {
                            Modifier.textureBlur(
                                backdrop = contentBackdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 150f,
                                noiseCoefficient = noiseCoefficient,
                                colors = BlurDefaults.blurColors(
                                    blendColors = logoBlend,
                                ),
                                contentBlendMode = ComposeBlendMode.DstIn,
                            )
                        } else {
                            Modifier
                        },
                    ),
                text = appName,
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            MiuixText(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val versionCodeProgress = ((scrollProgressProvider() - 0.05f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1 - versionCodeProgress
                        scaleX = 1 - (versionCodeProgress * 0.05f)
                        scaleY = 1 - (versionCodeProgress * 0.05f)
                    },
                color = colorScheme.onSurfaceVariantSummary,
                text = versionName,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Scrollable content
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                ),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateLeftPadding(LayoutDirection.Ltr),
                end = scrollPadding.calculateRightPadding(LayoutDirection.Ltr),
            ),
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() - scrollPadding.calculateTopPadding() + 126.dp,
                        ),
                )
            }

            item(key = "about") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = scrollPadding.calculateBottomPadding()),
                ) {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .then(
                                if (contentBackdrop != null) {
                                    Modifier.textureBlur(
                                        backdrop = contentBackdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = blurRadius,
                                        noiseCoefficient = noiseCoefficient,
                                        colors = BlurDefaults.blurColors(
                                            blendColors = cardBlend,
                                            brightness = brightness,
                                            contrast = contrast,
                                            saturation = saturation,
                                        ),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.defaultColors(
                            if (contentBackdrop != null) Color.Transparent else colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.about_source_code),
                            summary = stringResource(R.string.about_source_code_summary),
                            onClick = { uriHandler.openUri("https://github.com/Ianzb/HyperNavBar") },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.about_telegram),
                            summary = stringResource(R.string.about_telegram_summary),
                            onClick = { uriHandler.openUri("https://t.me/HyperNavBar") },
                        )
                    }
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp)
                            .then(
                                if (contentBackdrop != null) {
                                    Modifier.textureBlur(
                                        backdrop = contentBackdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = blurRadius,
                                        noiseCoefficient = noiseCoefficient,
                                        colors = BlurDefaults.blurColors(
                                            blendColors = cardBlend,
                                            brightness = brightness,
                                            contrast = contrast,
                                            saturation = saturation,
                                        ),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.defaultColors(
                            if (contentBackdrop != null) Color.Transparent else colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.license_apache),
                            summary = stringResource(R.string.license_apache_summary),
                            onClick = { uriHandler.openUri("https://www.apache.org/licenses/LICENSE-2.0.txt") },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.about_dependencies),
                            onClick = openLicensePage,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

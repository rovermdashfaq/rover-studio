package com.example.ui.screens

import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NameplatePreset
import com.example.data.model.UiState
import com.example.media.CreditRenderer
import com.example.media.NameplateRenderer
import com.example.ui.components.CheckerboardBackground
import com.example.ui.components.ColorPickerDialog
import com.example.ui.theme.HindSiliguriFamily
import com.example.ui.viewmodel.StudioViewModel
import kotlin.math.roundToInt

@Composable
fun NameplateScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val editor by viewModel.nameplateEditor.collectAsStateWithLifecycle()
    val badgeLogo by viewModel.nameplateLogoBitmap.collectAsStateWithLifecycle()
    val savedNameplates by viewModel.nameplatePresetsState.collectAsStateWithLifecycle()

    val renderer = remember(context) { NameplateRenderer(context) }
    var showColorPicker by remember { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.pickNameplateLogo(context, uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadDefaultNameplateLogo(context)
    }

    val accentColorInt = remember(editor.accentColorHex) {
        CreditRenderer.parseColorSafe(editor.accentColorHex, AndroidColor.rgb(255, 43, 74))
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 880.dp

        val previewCard: @Composable () -> Unit = {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "লাইভ প্রিভিউ (16:9 Transparent)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${1920 * editor.exportScale}×${1080 * editor.exportScale} PNG",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    CheckerboardBackground(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawIntoCanvas { canvas ->
                                renderer.draw(
                                    canvas = canvas.nativeCanvas,
                                    width = size.width.roundToInt().coerceAtLeast(16),
                                    height = size.height.roundToInt().coerceAtLeast(9),
                                    nameText = editor.nameText,
                                    unitText = editor.unitText,
                                    topBarColorInt = accentColorInt,
                                    logoBitmap = badgeLogo,
                                    showLogo = editor.showLogo
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.exportNameplatePng(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("export_nameplate_png_button"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Transparent Nameplate.PNG")
                    }
                }
            }
        }

        val controlsColumn: @Composable () -> Unit = {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("তথ্য পূরণ করুন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = viewModel::saveNameplatePreset,
                            modifier = Modifier.testTag("save_nameplate_button")
                        ) {
                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }
                    }

                    OutlinedTextField(
                        value = editor.nameText,
                        onValueChange = { v -> viewModel.updateNameplateEditor { it.copy(nameText = v) } },
                        label = { Text("নাম ও পদবি (প্রথম লাইন)", fontFamily = HindSiliguriFamily) },
                        placeholder = { Text("নাম লিখুন", fontFamily = HindSiliguriFamily) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = HindSiliguriFamily),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nameplate_name_input")
                    )

                    OutlinedTextField(
                        value = editor.unitText,
                        onValueChange = { v -> viewModel.updateNameplateEditor { it.copy(unitText = v) } },
                        label = { Text("ইউনিট / গ্রুপের নাম (দ্বিতীয় লাইন)", fontFamily = HindSiliguriFamily) },
                        placeholder = { Text("ইউনিট / গ্রুপের নাম লিখুন", fontFamily = HindSiliguriFamily) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = HindSiliguriFamily),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nameplate_unit_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Nameplate Logo", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = if (editor.showLogo) "লোগো চালু আছে (On)" else "লোগো বন্ধ আছে (Off)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = editor.showLogo,
                            onCheckedChange = { enabled ->
                                viewModel.updateNameplateEditor { it.copy(showLogo = enabled) }
                            },
                            modifier = Modifier.testTag("nameplate_logo_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Top Bar Color", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(accentColorInt))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { showColorPicker = true }
                            )
                            AnimatedVisibility(visible = editor.showLogo) {
                                OutlinedButton(
                                    onClick = {
                                        logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.testTag("nameplate_pick_logo_button")
                                ) {
                                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Badge Logo")
                                }
                            }
                        }
                    }

                    Text("Export Resolution", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editor.exportScale == 2,
                            onClick = { viewModel.updateNameplateEditor { it.copy(exportScale = 2) } },
                            label = { Text("4K (3840×2160)") }
                        )
                        FilterChip(
                            selected = editor.exportScale == 3,
                            onClick = { viewModel.updateNameplateEditor { it.copy(exportScale = 3) } },
                            label = { Text("6K (5760×3240)") }
                        )
                    }
                }
            }

            if (savedNameplates is UiState.Success && (savedNameplates as UiState.Success<List<NameplatePreset>>).data.isNotEmpty()) {
                val list = (savedNameplates as UiState.Success<List<NameplatePreset>>).data
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Saved Nameplates (Firestore)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(list, key = { it.id }) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.clickable { viewModel.applyNameplatePreset(item) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(item.nameText, style = MaterialTheme.typography.labelLarge)
                                            Text(item.unitText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { viewModel.deleteNameplatePreset(item.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete nameplate", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1.2f).verticalScroll(rememberScrollState())) { previewCard() }
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { controlsColumn() }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                previewCard()
                controlsColumn()
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialHex = editor.accentColorHex,
            title = "Nameplate Accent Color",
            onDismiss = { showColorPicker = false },
            onColorSelected = { hex ->
                viewModel.updateNameplateEditor { it.copy(accentColorHex = hex) }
                showColorPicker = false
            }
        )
    }
}

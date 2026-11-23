package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CreditProject
import com.example.data.model.CreditSection
import com.example.data.model.UiState
import com.example.media.CreditRenderer
import com.example.ui.components.CheckerboardBackground
import com.example.ui.components.ColorPickerDialog
import com.example.ui.viewmodel.StudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sections by viewModel.creditSections.collectAsStateWithLifecycle()
    val savedProjects by viewModel.creditProjectsState.collectAsStateWithLifecycle()

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewTransparent by remember { mutableStateOf(false) }

    var batchTitleColor by remember { mutableStateOf("#DC2626") }
    var batchTitleSize by remember { mutableLongStateOf(48L) }
    var batchNameColor by remember { mutableStateOf("#111827") }
    var batchNameSize by remember { mutableLongStateOf(40L) }

    var colorPickerTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val renderer = remember(context) { CreditRenderer(context) }

    LaunchedEffect(sections, previewTransparent) {
        val bmp = withContext(Dispatchers.Default) {
            renderer.renderToBitmap(
                sections = sections,
                baseWidth = 960,
                scale = 1,
                transparentBackground = previewTransparent
            )
        }
        previewBitmap = bmp
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 900.dp

        val previewPane: @Composable () -> Unit = {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ক্রেডিট ইমেজ প্রিভিউ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { previewTransparent = !previewTransparent }) {
                                Text(if (previewTransparent) "Show White BG" else "Show Transparent")
                            }
                        }
                    }

                    CheckerboardBackground(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 360.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        val bmp = previewBitmap
                        if (bmp != null && !bmp.isRecycled) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Credit Image Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportCreditPng(context, transparent = false, scale = 2) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_credit_white_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সাদা PNG (4K)")
                        }
                        Button(
                            onClick = { viewModel.exportCreditPng(context, transparent = true, scale = 2) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_credit_transparent_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ট্রান্সপারেন্ট PNG")
                        }
                    }
                }
            }
        }

        val editorPane: @Composable () -> Unit = {
            // Batch Style Controls
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "⚡ সকল টাইটেল ও নাম একসাথে পরিবর্তন",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("টাইটেল:", style = MaterialTheme.typography.labelLarge)
                            ColorDot(hex = batchTitleColor) {
                                colorPickerTarget = ColorTarget.BatchTitle
                            }
                            SizeDropdown(selected = batchTitleSize, sizes = listOf(32L, 36L, 40L, 44L, 48L, 52L, 56L, 64L)) {
                                batchTitleSize = it
                            }
                            Button(
                                onClick = { viewModel.applyBatchTitleStyle(batchTitleColor, batchTitleSize) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("প্রয়োগ") }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("নাম:", style = MaterialTheme.typography.labelLarge)
                            ColorDot(hex = batchNameColor) {
                                colorPickerTarget = ColorTarget.BatchName
                            }
                            SizeDropdown(selected = batchNameSize, sizes = listOf(28L, 32L, 36L, 40L, 44L, 48L, 56L, 64L)) {
                                batchNameSize = it
                            }
                            Button(
                                onClick = { viewModel.applyBatchNameStyle(batchNameColor, batchNameSize) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("প্রয়োগ") }
                        }
                    }
                }
            }

            // Toolbar: Add section + Save Project
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ক্রেডিট লিস্ট (${sections.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.testTag("save_credit_project_button")
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                    Button(
                        onClick = viewModel::addCreditSection,
                        modifier = Modifier.testTag("add_credit_section_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সেকশন")
                    }
                }
            }

            sections.forEachIndexed { idx, section ->
                CreditSectionCard(
                    index = idx,
                    totalCount = sections.size,
                    section = section,
                    onUpdate = { viewModel.updateCreditSection(idx, it) },
                    onMoveUp = { viewModel.moveCreditSection(idx, idx - 1) },
                    onMoveDown = { viewModel.moveCreditSection(idx, idx + 1) },
                    onRemove = { viewModel.removeCreditSection(idx) },
                    onPickTitleColor = { colorPickerTarget = ColorTarget.SectionTitle(idx, section.titleColorHex) },
                    onPickNameColor = { colorPickerTarget = ColorTarget.SectionName(idx, section.nameColorHex) }
                )
            }

            if (savedProjects is UiState.Success && (savedProjects as UiState.Success<List<CreditProject>>).data.isNotEmpty()) {
                val projects = (savedProjects as UiState.Success<List<CreditProject>>).data
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Saved Credit Projects (Firestore)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(projects, key = { it.id }) { proj ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.clickable { viewModel.loadCreditProject(proj) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(proj.projectTitle, style = MaterialTheme.typography.labelLarge)
                                            Text("${proj.sections.size} sections", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { viewModel.deleteCreditProject(proj.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete project", modifier = Modifier.size(16.dp))
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
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    editorPane()
                }
                Column(modifier = Modifier.weight(1.1f).verticalScroll(rememberScrollState())) {
                    previewPane()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                previewPane()
                editorPane()
            }
        }
    }

    colorPickerTarget?.let { target ->
        val initHex = when (target) {
            ColorTarget.BatchTitle -> batchTitleColor
            ColorTarget.BatchName -> batchNameColor
            is ColorTarget.SectionTitle -> target.currentHex
            is ColorTarget.SectionName -> target.currentHex
        }
        ColorPickerDialog(
            initialHex = initHex,
            title = "Select Text Color",
            onDismiss = { colorPickerTarget = null },
            onColorSelected = { hex ->
                when (target) {
                    ColorTarget.BatchTitle -> batchTitleColor = hex
                    ColorTarget.BatchName -> batchNameColor = hex
                    is ColorTarget.SectionTitle -> {
                        sections.getOrNull(target.index)?.let {
                            viewModel.updateCreditSection(target.index, it.copy(titleColorHex = hex))
                        }
                    }
                    is ColorTarget.SectionName -> {
                        sections.getOrNull(target.index)?.let {
                            viewModel.updateCreditSection(target.index, it.copy(nameColorHex = hex))
                        }
                    }
                }
                colorPickerTarget = null
            }
        )
    }

    if (showSaveDialog) {
        var projName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Credit Project") },
            text = {
                OutlinedTextField(
                    value = projName,
                    onValueChange = { projName = it.take(100) },
                    label = { Text("Project Title (e.g. Bulletin Ep 42)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCreditProject(projName)
                        showSaveDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private sealed interface ColorTarget {
    data object BatchTitle : ColorTarget
    data object BatchName : ColorTarget
    data class SectionTitle(val index: Int, val currentHex: String) : ColorTarget
    data class SectionName(val index: Int, val currentHex: String) : ColorTarget
}

@Composable
private fun CreditSectionCard(
    index: Int,
    totalCount: Int,
    section: CreditSection,
    onUpdate: (CreditSection) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPickTitleColor: () -> Unit,
    onPickNameColor: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "সেকশন ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = index < totalCount - 1, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRemove, enabled = totalCount > 1, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove section", modifier = Modifier.size(18.dp))
                    }
                }
            }

            OutlinedTextField(
                value = section.title,
                onValueChange = { onUpdate(section.copy(title = it)) },
                label = { Text("টাইটেল") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Title Style:", style = MaterialTheme.typography.bodySmall)
                ColorDot(hex = section.titleColorHex, onClick = onPickTitleColor)
                SizeDropdown(selected = section.titleSize, sizes = listOf(32L, 36L, 40L, 44L, 48L, 52L, 56L, 64L)) {
                    onUpdate(section.copy(titleSize = it))
                }
            }

            OutlinedTextField(
                value = section.namesText,
                onValueChange = { onUpdate(section.copy(namesText = it)) },
                label = { Text("নামসমূহ (কমা বা Enter দিয়ে পৃথক করুন)") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Name Style:", style = MaterialTheme.typography.bodySmall)
                ColorDot(hex = section.nameColorHex, onClick = onPickNameColor)
                SizeDropdown(selected = section.nameSize, sizes = listOf(28L, 32L, 36L, 40L, 44L, 48L, 56L, 64L)) {
                    onUpdate(section.copy(nameSize = it))
                }
            }
        }
    }
}

@Composable
private fun ColorDot(hex: String, onClick: () -> Unit) {
    val colorInt = remember(hex) { CreditRenderer.parseColorSafe(hex, AndroidColor.RED) }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(colorInt))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun SizeDropdown(
    selected: Long,
    sizes: List<Long>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Text(
                text = "${selected}px",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sizes.forEach { sz ->
                DropdownMenuItem(
                    text = { Text("${sz}px") },
                    onClick = {
                        onSelect(sz)
                        expanded = false
                    }
                )
            }
        }
    }
}

package pt.bombeiropt.listainteligente

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

data class WorkProject(val id: Long, val name: String, val items: List<MaterialItem>)

data class MaterialItem(val id: Long, val name: String, val quantity: Double, val unit: String, val price: Double, val category: String, val bought: Boolean = false, val kind: String = "material") {
    val total get() = quantity * price
}

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale("pt", "PT") }
        setContent { ListaApp(onSpeak = { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "lista") }) }
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaApp(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lista_inteligente", 0) }
    var works by remember { mutableStateOf(loadWorks(prefs.getString("works", "") ?: "", prefs.getString("list_name", "Obra") ?: "Obra", prefs.getString("items", "[]") ?: "[]")) }
    var activeWorkId by remember { mutableStateOf(works.first().id) }
    val activeWork = works.firstOrNull { it.id == activeWorkId } ?: works.first()
    var listName by remember(activeWorkId) { mutableStateOf(activeWork.name) }
    var materials by remember(activeWorkId) { mutableStateOf(activeWork.items) }
    var showWorksMenu by remember { mutableStateOf(false) }
    var showNewWork by remember { mutableStateOf(false) }
    var newWorkName by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MaterialItem?>(null) }
    var filter by remember { mutableStateOf("Todos") }
    var section by remember { mutableStateOf(0) }
    val sectionItems = materials.filter { if (section == 0) it.kind == "material" else it.kind == "tool" }
    val visibleItems = sectionItems.filter {
        val statusMatches = when (filter) {
            "Por comprar", "Por levar" -> !it.bought
            "Comprados", "Já levadas" -> it.bought
            else -> true
        }
        statusMatches
    }
    val sectionTitle = if (section == 0) "Materiais a comprar" else "Ferramentas e acessórios"
    val currentKind = if (section == 0) "material" else "tool"
    val filterOptions = if (section == 0) listOf("Todos", "Por comprar", "Comprados") else listOf("Todos", "Por levar", "Já levadas")
    var voiceMessage by remember { mutableStateOf("") }
    val money = remember { NumberFormat.getCurrencyInstance(Locale("pt", "PT")) }
    fun persistWorks(newWorks: List<WorkProject>) {
        works = newWorks
        prefs.edit().putString("works", saveWorks(newWorks)).apply()
    }
    fun persist(newItems: List<MaterialItem>) {
        materials = newItems
        persistWorks(works.map { if (it.id == activeWorkId) it.copy(name = listName, items = newItems) else it })
        prefs.edit().putString("items", saveItems(newItems)).putString("list_name", listName).apply()
    }
    fun handleVoice(text: String) {
        voiceMessage = text
        val cleaned = text.trim()
        if (cleaned.isNotBlank()) {
            val match = Regex("(?i)(?:adiciona|adicionar|junta|regista)\\s+(.+?)(?:\\s+(?:quantidade|qtd)\\s+(\\d+(?:[,.]\\d+)?))?$").find(cleaned)
            val name = match?.groupValues?.get(1)?.trim() ?: cleaned
            val qty = match?.groupValues?.get(2)?.replace(",", ".")?.toDoubleOrNull() ?: 1.0
            if (materials.none { it.kind == currentKind && it.name.equals(name, true) }) persist(materials + MaterialItem(System.currentTimeMillis(), name.replaceFirstChar { it.uppercase() }, qty, "un", 0.0, "Geral", kind = currentKind))
            onSpeak("Registado: $name. Pode indicar o preço mais tarde.")
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) handleVoice(spoken)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-PT")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga o material a adicionar")
            }
            speechLauncher.launch(intent)
        } else voiceMessage = "É necessária autorização do microfone para usar a voz."
    }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF28634D), onPrimary = Color.White,
        secondary = Color(0xFFD28B45), background = Color(0xFFF3F6F3),
        surface = Color.White, onSurface = Color(0xFF20352B)
    )) {
        Scaffold(containerColor = Color(0xFFF3F6F3), topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF3F6F3)),
                title = { Column { Text("Lista Inteligente", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text("Gestão simples da sua obra", style = MaterialTheme.typography.labelSmall, color = Color(0xFF718078)) } },
                actions = {
                    IconButton(onClick = { showNewWork = true; newWorkName = "" }) { Icon(Icons.Default.Add, "Criar obra") }
                    IconButton(onClick = {
                    val exportItems = sectionItems
                    val body = buildString { appendLine("$sectionTitle - $listName"); if (section == 0) appendLine("Material | Quantidade | Unidade | Preço/un. | Total") else appendLine("Ferramenta/acessório | Quantidade | Estado"); exportItems.forEach { appendLine(if (section == 0) "${it.name} | ${it.quantity} | ${it.unit} | ${money.format(it.price)} | ${money.format(it.total)}" else "${it.name} | ${it.quantity} ${it.unit} | ${if (it.bought) "Já levado" else "Falta levar"}") }; if (section == 0) appendLine("TOTAL: ${money.format(exportItems.sumOf { it.total })}") }
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "$sectionTitle - $listName"); putExtra(Intent.EXTRA_TEXT, body) }
                    context.startActivity(Intent.createChooser(send, "Partilhar lista"))
                }) { Icon(Icons.Default.Share, "Partilhar") } }
            )
        }, floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-PT"); putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga: adicionar cimento, quantidade 5") }
                        speechLauncher.launch(intent)
                    } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }, containerColor = Color(0xFFD18B43), contentColor = Color.White) { Icon(Icons.Default.Mic, "Adicionar por voz") }
                FloatingActionButton(onClick = { editing = null; showAdd = true }, containerColor = Color(0xFF315B46), contentColor = Color.White) { Icon(Icons.Default.Add, if (section == 0) "Adicionar material" else "Adicionar ferramenta") }
            }
        }) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(2.dp))
                Box {
                    Row(
                        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))
                            .clickable { showWorksMenu = true }.padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("OBRA SELECIONADA", style = MaterialTheme.typography.labelSmall, color = Color(0xFF718078))
                            Text(listName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF20352B))
                            Text("${works.size} obra(s) registada(s)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF718078))
                        }
                        Icon(Icons.Default.Build, "Escolher obra", tint = Color(0xFF28634D))
                    }
                    DropdownMenu(expanded = showWorksMenu, onDismissRequest = { showWorksMenu = false }) {
                        works.forEach { work ->
                            DropdownMenuItem(
                                text = { Text(work.name) },
                                onClick = { activeWorkId = work.id; showWorksMenu = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("+ Criar nova obra", color = Color(0xFF28634D), fontWeight = FontWeight.Bold) },
                            onClick = { showWorksMenu = false; newWorkName = ""; showNewWork = true }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Summary(if (section == 0) "ARTIGOS" else "EQUIPAMENTO", sectionItems.size.toString(), Modifier.weight(1f))
                    Summary(if (section == 0) "POR COMPRAR" else "POR LEVAR", sectionItems.count { !it.bought }.toString(), Modifier.weight(1f))
                    Summary(if (section == 0) "TOTAL" else "LEVADOS", if (section == 0) money.format(sectionItems.sumOf { it.total }) else sectionItems.count { it.bought }.toString(), Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFE5EEE8), RoundedCornerShape(16.dp)).padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf("Materiais", "Ferramentas").forEachIndexed { index, label ->
                        val selected = section == index
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color.White else Color.Transparent)
                                .clickable { section = index; filter = "Todos" }
                                .padding(horizontal = 6.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (index == 0) Icons.Default.ShoppingCart else Icons.Default.Build, null, tint = Color(0xFF28634D), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = Color(0xFF254C3A), maxLines = 1)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filterOptions.forEach { option ->
                        FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option, maxLines = 1) })
                    }
                }
                if (voiceMessage.isNotBlank()) Text("Ouvido: $voiceMessage", style = MaterialTheme.typography.bodySmall, color = Color(0xFF315B46))
                if (sectionItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (section == 0) "Ainda não há materiais" else "Ainda não há ferramentas ou acessórios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (section == 0) "Adicione artigos que precisa de comprar." else "Registe o equipamento que precisa de levar para a obra.") } }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                        items(visibleItems, key = { it.id }) { item ->
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth().clickable { editing = item; showAdd = true }) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = item.bought, onCheckedChange = { checked -> persist(materials.map { if (it.id == item.id) it.copy(bought = checked) else it }) })
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.SemiBold, color = if (item.bought) Color.Gray else Color(0xFF26352D))
                                        Text("${item.quantity} ${item.unit} · ${if (section == 0) item.category else if (item.bought) "Já levado" else "Falta levar"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (section == 0) { Text(money.format(item.total), fontWeight = FontWeight.Bold); Text("Un. ${money.format(item.price)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray) } else { Text(if (item.bought) "Levado" else "Por levar", fontWeight = FontWeight.Bold, color = if (item.bought) Color(0xFF315B46) else Color(0xFFD18B43)) }
                                    }
                                    IconButton(onClick = { persist(materials.filterNot { it.id == item.id }) }) { Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFB34B42)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showNewWork) {
            AlertDialog(
                onDismissRequest = { showNewWork = false },
                title = { Text("Criar nova obra") },
                text = {
                    OutlinedTextField(
                        value = newWorkName,
                        onValueChange = { newWorkName = it },
                        label = { Text("Nome da obra") },
                        placeholder = { Text("Ex.: Remodelação da cozinha") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = newWorkName.trim()
                        if (name.isNotBlank()) {
                            val created = WorkProject(System.currentTimeMillis(), name, emptyList())
                            persistWorks(works + created)
                            activeWorkId = created.id
                            showNewWork = false
                            section = 0
                            filter = "Todos"
                        }
                    }) { Text("Criar obra") }
                },
                dismissButton = { TextButton(onClick = { showNewWork = false }) { Text("Cancelar") } }
            )
        }
        if (showAdd) MaterialDialog(item = editing, kind = if (section == 0) "material" else "tool", onDismiss = { showAdd = false }, onSave = { saved ->
            val updated = if (editing == null) materials + saved else materials.map { if (it.id == editing?.id) saved.copy(id = it.id, bought = it.bought) else it }
            persist(updated); showAdd = false
        })
    }
}

@Composable
fun Summary(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 11.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF718078), maxLines = 1)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, color = Color(0xFF20352B))
        }
    }
}
@Composable
fun MaterialDialog(item: MaterialItem?, kind: String, onDismiss: () -> Unit, onSave: (MaterialItem) -> Unit) {
    var name by remember(item) { mutableStateOf(item?.name ?: "") }
    var quantity by remember(item) { mutableStateOf(item?.quantity?.toString() ?: "1") }
    var unit by remember(item) { mutableStateOf(item?.unit ?: "un") }
    var price by remember(item) { mutableStateOf(item?.price?.toString() ?: "0") }
    var category by remember(item) { mutableStateOf(item?.category ?: "Geral") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (kind == "material") { if (item == null) "Novo material" else "Editar material" } else { if (item == null) "Nova ferramenta/acessório" else "Editar ferramenta/acessório" }) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(if (kind == "material") "Material (ex.: cimento)" else "Ferramenta ou acessório (ex.: berbequim)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantidade") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("Unidade") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(price, { price = it }, label = { Text(if (kind == "material") "Preço por unidade (€)" else "Preço (opcional, €)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(category, { category = it }, label = { Text("Categoria") }, singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(MaterialItem(item?.id ?: System.currentTimeMillis(), name.trim(), quantity.replace(",", ".").toDoubleOrNull() ?: 1.0, unit.ifBlank { "un" }, price.replace(",", ".").toDoubleOrNull() ?: 0.0, category.ifBlank { "Geral" }, kind = kind)) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

fun inferItemKind(name: String, storedKind: String): String {
    if (storedKind == "tool") return "tool"
    val toolTerms = listOf(
        "lixadora", "berbequim", "furadeira", "martelo", "chave de fendas",
        "chave inglesa", "alicate", "serra", "rebarbadora", "aparafusadora",
        "escadote", "extensão elétrica", "nivel", "nível", "fita métrica",
        "trena", "plaina", "formão", "broca", "disco de corte",
        "carrinho de mão", "pá", "enxada", "colher de pedreiro",
        "talocha", "espátula", "pistola de silicone", "rolo de pintura",
        "pincel", "esquadro", "torquês", "grampeador"
    )
    return if (toolTerms.any { name.contains(it, ignoreCase = true) }) "tool" else storedKind
}

fun loadWorks(raw: String, legacyName: String, legacyItems: String): List<WorkProject> = try {
    if (raw.isBlank()) listOf(WorkProject(1L, legacyName.ifBlank { "Obra" }, loadItems(legacyItems)))
    else {
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            WorkProject(obj.optLong("id", System.currentTimeMillis() + i), obj.optString("name", "Obra ${i + 1}"), loadItems(obj.optString("items", "[]")))
        }.ifEmpty { listOf(WorkProject(1L, "Obra", emptyList())) }
    }
} catch (_: Exception) {
    listOf(WorkProject(1L, legacyName.ifBlank { "Obra" }, loadItems(legacyItems)))
}

fun saveWorks(works: List<WorkProject>): String {
    val array = JSONArray()
    works.forEach { work ->
        array.put(JSONObject().put("id", work.id).put("name", work.name).put("items", JSONArray(saveItems(work.items))))
    }
    return array.toString()
}

fun loadItems(raw: String): List<MaterialItem> = try {
    val array = JSONArray(raw)
    (0 until array.length()).map { i -> array.getJSONObject(i).let { MaterialItem(it.optLong("id"), it.optString("name"), it.optDouble("quantity", 1.0), it.optString("unit", "un"), it.optDouble("price", 0.0), it.optString("category", "Geral"), it.optBoolean("bought"), inferItemKind(it.optString("name"), it.optString("kind", "material"))) } }
} catch (_: Exception) { emptyList() }

fun saveItems(materials: List<MaterialItem>): String {
    val array = JSONArray()
    materials.forEach { item -> array.put(JSONObject().put("id", item.id).put("name", item.name).put("quantity", item.quantity).put("unit", item.unit).put("price", item.price).put("category", item.category).put("bought", item.bought).put("kind", item.kind)) }
    return array.toString()
}

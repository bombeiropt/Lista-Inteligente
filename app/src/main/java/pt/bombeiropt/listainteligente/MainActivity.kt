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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
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
    var listName by remember { mutableStateOf(prefs.getString("list_name", "Obra") ?: "Obra") }
    var materials by remember { mutableStateOf(loadItems(prefs.getString("items", "[]") ?: "[]")) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MaterialItem?>(null) }
    var filter by remember { mutableStateOf("Todos") }
    var section by remember { mutableStateOf(0) }
    val sectionItems = materials.filter { if (section == 0) it.kind == "material" else it.kind == "tool" }
    val sectionTitle = if (section == 0) "Materiais a comprar" else "Ferramentas e acessórios"
    var query by remember { mutableStateOf("") }
    var voiceMessage by remember { mutableStateOf("") }
    val money = remember { NumberFormat.getCurrencyInstance(Locale("pt", "PT")) }
    fun persist(newItems: List<MaterialItem>) { materials = newItems; prefs.edit().putString("items", saveItems(newItems)).apply() }
    fun handleVoice(text: String) {
        voiceMessage = text
        val cleaned = text.trim()
        if (cleaned.isNotBlank()) {
            val match = Regex("(?i)(?:adiciona|adicionar|junta|regista)\\s+(.+?)(?:\\s+(?:quantidade|qtd)\\s+(\\d+(?:[,.]\\d+)?))?$").find(cleaned)
            val name = match?.groupValues?.get(1)?.trim() ?: cleaned
            val qty = match?.groupValues?.get(2)?.replace(",", ".")?.toDoubleOrNull() ?: 1.0
            if (materials.none { it.name.equals(name, true) }) persist(materials + MaterialItem(System.currentTimeMillis(), name.replaceFirstChar { it.uppercase() }, qty, "un", 0.0, "Geral"))
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
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF315B46), secondary = Color(0xFFD18B43), background = Color(0xFFF5F3ED))) {
        Scaffold(containerColor = Color(0xFFF5F3ED), topBar = {
            TopAppBar(title = { Text("Lista Inteligente", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = {
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
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(3.dp))
                OutlinedTextField(value = listName, onValueChange = { listName = it; prefs.edit().putString("list_name", it).apply() }, label = { Text("Obra / lista") }, singleLine = true, modifier = Modifier.fillMaxWidth().height(56.dp))
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Summary(if (section == 0) "MATERIAIS" else "EQUIPAMENTO", sectionItems.size.toString())
                    Summary(if (section == 0) "POR COMPRAR" else "POR LEVAR", sectionItems.count { !it.bought }.toString())
                    Summary(if (section == 0) "TOTAL" else "LEVADOS", if (section == 0) money.format(sectionItems.sumOf { it.total }) else sectionItems.count { it.bought }.toString())
                }
                Spacer(Modifier.height(5.dp))
                TabRow(selectedTabIndex = section) {
                    Tab(selected = section == 0, onClick = { section = 0; query = ""; filter = "Todos" }, text = { Text("Materiais a comprar") })
                    Tab(selected = section == 1, onClick = { section = 1; query = ""; filter = "Todos" }, text = { Text("Ferramentas e acessórios") })
                }
                Spacer(Modifier.height(5.dp))
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().height(54.dp), singleLine = true, label = { Text(if (section == 0) "Pesquisar material" else "Pesquisar ferramenta") })
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todos", "Por comprar", "Comprados").forEach { option ->
                        FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                    }
                }
                if (voiceMessage.isNotBlank()) Text("Ouvido: $voiceMessage", style = MaterialTheme.typography.bodySmall, color = Color(0xFF315B46))
                if (sectionItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (section == 0) "Ainda não há materiais" else "Ainda não há ferramentas ou acessórios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (section == 0) "Adicione artigos que precisa de comprar." else "Registe o equipamento que precisa de levar para a obra.") } }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                        items(sectionItems.filter { (filter == "Todos" || (filter == "Comprados") == it.bought) && it.name.contains(query, true) }, key = { it.id }) { item ->
                            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth().clickable { editing = item; showAdd = true }) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
        if (showAdd) MaterialDialog(item = editing, kind = if (section == 0) "material" else "tool", onDismiss = { showAdd = false }, onSave = { saved ->
            val updated = if (editing == null) materials + saved else materials.map { if (it.id == editing?.id) saved.copy(id = it.id, bought = it.bought) else it }
            persist(updated); showAdd = false
        })
    }
}

@Composable
fun Summary(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF77766F), maxLines = 1)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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

fun loadItems(raw: String): List<MaterialItem> = try {
    val array = JSONArray(raw)
    (0 until array.length()).map { i -> array.getJSONObject(i).let { MaterialItem(it.optLong("id"), it.optString("name"), it.optDouble("quantity", 1.0), it.optString("unit", "un"), it.optDouble("price", 0.0), it.optString("category", "Geral"), it.optBoolean("bought"), it.optString("kind", "material")) } }
} catch (_: Exception) { emptyList() }

fun saveItems(materials: List<MaterialItem>): String {
    val array = JSONArray()
    materials.forEach { item -> array.put(JSONObject().put("id", item.id).put("name", item.name).put("quantity", item.quantity).put("unit", item.unit).put("price", item.price).put("category", item.category).put("bought", item.bought).put("kind", item.kind)) }
    return array.toString()
}

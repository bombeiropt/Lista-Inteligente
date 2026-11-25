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

data class MaterialItem(val id: Long, val name: String, val quantity: Double, val unit: String, val price: Double, val category: String, val bought: Boolean = false) {
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
    var items by remember { mutableStateOf(loadItems(prefs.getString("items", "[]") ?: "[]")) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MaterialItem?>(null) }
    var filter by remember { mutableStateOf("Todos") }
    var query by remember { mutableStateOf("") }
    var voiceMessage by remember { mutableStateOf("") }
    val money = remember { NumberFormat.getCurrencyInstance(Locale("pt", "PT")) }
    fun persist(newItems: List<MaterialItem>) { items = newItems; prefs.edit().putString("items", saveItems(newItems)).apply() }
    fun handleVoice(text: String) {
        voiceMessage = text
        val cleaned = text.trim()
        if (cleaned.isNotBlank()) {
            val match = Regex("(?i)(?:adiciona|adicionar|junta|regista)\\s+(.+?)(?:\\s+(?:quantidade|qtd)\\s+(\\d+(?:[,.]\\d+)?))?$").find(cleaned)
            val name = match?.groupValues?.get(1)?.trim() ?: cleaned
            val qty = match?.groupValues?.get(2)?.replace(",", ".")?.toDoubleOrNull() ?: 1.0
            if (items.none { it.name.equals(name, true) }) persist(items + MaterialItem(System.currentTimeMillis(), name.replaceFirstChar { it.uppercase() }, qty, "un", 0.0, "Geral"))
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
            TopAppBar(title = { Column { Text("Lista Inteligente", fontWeight = FontWeight.Bold); Text("Materiais de construção", style = MaterialTheme.typography.labelSmall) } },
                actions = { IconButton(onClick = {
                    val body = buildString { appendLine("LISTA: $listName"); appendLine("Material | Quantidade | Unidade | Preço/un. | Total"); items.forEach { appendLine("${it.name} | ${it.quantity} | ${it.unit} | ${money.format(it.price)} | ${money.format(it.total)}") }; appendLine("TOTAL: ${money.format(items.sumOf { it.total })}") }
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Lista de materiais - $listName"); putExtra(Intent.EXTRA_TEXT, body) }
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
                FloatingActionButton(onClick = { editing = null; showAdd = true }, containerColor = Color(0xFF315B46), contentColor = Color.White) { Icon(Icons.Default.Add, "Adicionar material") }
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = listName, onValueChange = { listName = it; prefs.edit().putString("list_name", it).apply() }, label = { Text("Nome da obra / lista") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Summary("ARTIGOS", items.size.toString())
                    Summary("POR COMPRAR", items.count { !it.bought }.toString())
                    Summary("TOTAL", money.format(items.sumOf { it.total }))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Pesquisar material") })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todos", "Por comprar", "Comprados").forEach { option ->
                        FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                    }
                }
                if (voiceMessage.isNotBlank()) Text("Ouvido: $voiceMessage", style = MaterialTheme.typography.bodySmall, color = Color(0xFF315B46))
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("A sua lista está vazia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Adicione materiais manualmente ou através do microfone.") } }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                        items(items.filter { (filter == "Todos" || (filter == "Comprados") == it.bought) && it.name.contains(query, true) }, key = { it.id }) { item ->
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().clickable { editing = item; showAdd = true }) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = item.bought, onCheckedChange = { checked -> persist(items.map { if (it.id == item.id) it.copy(bought = checked) else it }) })
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.SemiBold, color = if (item.bought) Color.Gray else Color(0xFF26352D))
                                        Text("${item.quantity} ${item.unit} · ${item.category}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(money.format(item.total), fontWeight = FontWeight.Bold)
                                        Text("Un. ${money.format(item.price)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                    IconButton(onClick = { persist(items.filterNot { it.id == item.id }) }) { Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFB34B42)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showAdd) MaterialDialog(item = editing, onDismiss = { showAdd = false }, onSave = { saved ->
            val updated = if (editing == null) items + saved else items.map { if (it.id == editing?.id) saved.copy(id = it.id, bought = it.bought) else it }
            persist(updated); showAdd = false
        })
    }
}

@Composable
fun Summary(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun MaterialDialog(item: MaterialItem?, onDismiss: () -> Unit, onSave: (MaterialItem) -> Unit) {
    var name by remember(item) { mutableStateOf(item?.name ?: "") }
    var quantity by remember(item) { mutableStateOf(item?.quantity?.toString() ?: "1") }
    var unit by remember(item) { mutableStateOf(item?.unit ?: "un") }
    var price by remember(item) { mutableStateOf(item?.price?.toString() ?: "0") }
    var category by remember(item) { mutableStateOf(item?.category ?: "Geral") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item == null) "Novo material" else "Editar material") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Material (ex.: cimento)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantidade") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("Unidade") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(price, { price = it }, label = { Text("Preço por unidade (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(category, { category = it }, label = { Text("Categoria") }, singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(MaterialItem(item?.id ?: System.currentTimeMillis(), name.trim(), quantity.replace(",", ".").toDoubleOrNull() ?: 1.0, unit.ifBlank { "un" }, price.replace(",", ".").toDoubleOrNull() ?: 0.0, category.ifBlank { "Geral" })) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

fun loadItems(raw: String): List<MaterialItem> = try {
    val array = JSONArray(raw)
    (0 until array.length()).map { i -> array.getJSONObject(i).let { MaterialItem(it.optLong("id"), it.optString("name"), it.optDouble("quantity", 1.0), it.optString("unit", "un"), it.optDouble("price", 0.0), it.optString("category", "Geral"), it.optBoolean("bought")) } }
} catch (_: Exception) { emptyList() }

fun saveItems(items: List<MaterialItem>): String {
    val array = JSONArray()
    items.forEach { item -> array.put(JSONObject().put("id", item.id).put("name", item.name).put("quantity", item.quantity).put("unit", item.unit).put("price", item.price).put("category", item.category).put("bought", item.bought)) }
    return array.toString()
}

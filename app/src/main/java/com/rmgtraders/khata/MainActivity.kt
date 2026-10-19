package com.rmgtraders.khata

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Bill(val id: Long, val customer: String, val detail: String, val total: Double, val advance: Double, val date: String) {
    val balance get() = (total - advance).coerceAtLeast(0.0)
}

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("rmg_khata", MODE_PRIVATE) }
    private val bt: BluetoothAdapter? get() = BluetoothAdapter.getDefaultAdapter()
    private var printer by mutableStateOf<BluetoothDevice?>(null)
    private var message by mutableStateOf("آف لائن کھاتہ تیار ہے")
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) pickPrinter() else message = "Bluetooth اجازت درکار ہے"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { KhataUi(::loadBills, ::saveBill, ::printBill, ::requestPrinter, printer?.name ?: "منتخب نہیں", message) } }
    }

    private fun loadBills(): List<Bill> {
        val a = JSONArray(prefs.getString("bills", "[]") ?: "[]")
        return (0 until a.length()).mapNotNull { i -> runCatching {
            val o = a.getJSONObject(i)
            Bill(o.getLong("id"), o.getString("customer"), o.getString("detail"), o.getDouble("total"), o.getDouble("advance"), o.getString("date"))
        }.getOrNull() }.reversed()
    }

    private fun saveBill(customer: String, detail: String, total: Double, advance: Double): Bill {
        val b = Bill(System.currentTimeMillis(), customer, detail, total, advance,
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
        val arr = JSONArray()
        (loadBills().reversed() + b).forEach {
            arr.put(JSONObject().put("id", it.id).put("customer", it.customer).put("detail", it.detail)
                .put("total", it.total).put("advance", it.advance).put("date", it.date))
        }
        prefs.edit().putString("bills", arr.toString()).apply()
        message = "بل محفوظ ہوگیا"
        return b
    }

    private fun requestPrinter() {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        else pickPrinter()
    }

    @Suppress("MissingPermission")
    private fun pickPrinter() {
        val a = bt
        if (a == null || !a.isEnabled) { message = "Bluetooth آن کریں"; return }
        val paired = a.bondedDevices.orEmpty().toList()
        if (paired.isEmpty()) { message = "Settings میں پہلے پرنٹر pair کریں"; return }
        printer = paired.firstOrNull { it.name.orEmpty().contains("print", true) || it.name.orEmpty().contains("pos", true) } ?: paired.first()
        message = "پرنٹر منتخب: ${printer?.name}"
    }

    @Suppress("MissingPermission")
    private fun printBill(b: Bill) {
        val d = printer
        if (d == null) { requestPrinter(); message = "پہلے پرنٹر منتخب کریں"; return }
        Thread {
            var s: BluetoothSocket? = null
            try {
                s = d.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                bt?.cancelDiscovery()
                s.connect()
                val receipt = "RMG TRADERS\n------------------------------\nBill: ${b.id}\nDate: ${b.date}\nCustomer: ${b.customer}\nDetail: ${b.detail}\n------------------------------\nTotal: Rs ${fmt(b.total)}\nAdvance: Rs ${fmt(b.advance)}\nBalance: Rs ${fmt(b.balance)}\n\nThank you!\n\n\n"
                s.outputStream.use { it.write(byteArrayOf(0x1B, 0x40)); it.write(receipt.toByteArray(Charsets.UTF_8)); it.flush() }
                runOnUiThread { message = "رسید بھیج دی گئی" }
            } catch (e: Exception) {
                runOnUiThread { message = "پرنٹ ناکام: ${e.localizedMessage ?: "پرنٹر چیک کریں"}" }
            } finally { runCatching { s?.close() } }
        }.start()
    }
}

@Composable
private fun KhataUi(load: () -> List<Bill>, save: (String, String, Double, Double) -> Bill, print: (Bill) -> Unit,
                    choosePrinter: () -> Unit, printerName: String, message: String) {
    var customer by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var advanceText by remember { mutableStateOf("0") }
    var bills by remember { mutableStateOf(load()) }
    var selected by remember { mutableStateOf<Bill?>(null) }
    val total = totalText.toDoubleOrNull() ?: 0.0
    val advance = advanceText.toDoubleOrNull() ?: 0.0
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold { p ->
            LazyColumn(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("RMG Khata", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("RMG Traders • درزی کھاتہ")
                    Text(message, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = choosePrinter, modifier = Modifier.fillMaxWidth()) { Text("Bluetooth پرنٹر: $printerName") }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("نیا بل", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(customer, { customer = it }, label = { Text("گاہک کا نام") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(detail, { detail = it }, label = { Text("کپڑا / تفصیل") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(totalText, { totalText = it }, label = { Text("کل رقم (روپے)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(advanceText, { advanceText = it }, label = { Text("ایڈوانس (روپے)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            Text("بقایا: Rs ${fmt((total - advance).coerceAtLeast(0.0))}", fontWeight = FontWeight.Bold)
                            Button(onClick = {
                                if (customer.isNotBlank() && total > 0 && advance >= 0) {
                                    val b = save(customer.trim(), detail.trim(), total, advance.coerceAtMost(total))
                                    bills = load(); selected = b
                                    customer = ""; detail = ""; totalText = ""; advanceText = "0"
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("بل محفوظ کریں") }
                            selected?.let { b -> OutlinedButton(onClick = { print(b) }, modifier = Modifier.fillMaxWidth()) { Text("رسید پرنٹ کریں") } }
                        }
                    }
                }
                item { Text("محفوظ شدہ بل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (bills.isEmpty()) item { Text("ابھی کوئی بل محفوظ نہیں") }
                items(bills, key = { it.id }) { b ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(b.customer, fontWeight = FontWeight.Bold)
                            Text(b.detail.ifBlank { "تفصیل موجود نہیں" })
                            Text("کل: Rs ${fmt(b.total)} • بقایا: Rs ${fmt(b.balance)}")
                            Text(b.date, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { selected = b }) { Text("رسید منتخب کریں") }
                        }
                    }
                }
            }
        }
    }
}
private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)

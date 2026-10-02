package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.trimNum
import com.example.ui.AppViewModel
import com.example.ui.components.AccentButton
import com.example.ui.components.ChoiceChip
import com.example.ui.components.FitTextField
import com.example.ui.components.GhostButton
import com.example.ui.components.OverlineText
import com.example.ui.theme.fit
import com.example.ui.theme.mono

/**
 * 2.39: İlk açılış kurulumu — 3 adım: profil, salon ekipmanı, haftalık hedef.
 * Güç seviyesi, VKİ, kalori ve ağırlık önerileri bu bilgilerle doğru hesaplanır.
 */
@Composable
fun OnboardingFlow(vm: AppViewModel, onDone: () -> Unit) {
    val s = vm.settings
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(s.userName.value.takeIf { it != "Sporcu" } ?: "") }
    var male by remember { mutableStateOf(s.isMale.value) }
    var age by remember { mutableStateOf(s.age.value.toString()) }
    var height by remember { mutableStateOf(s.heightCm.value.trimNum()) }
    var weight by remember { mutableStateOf(s.weightKg.value.trimNum()) }
    var bar by remember { mutableStateOf(s.barWeight.value) }
    var plateSet by remember { mutableIntStateOf(0) }
    var dbStep by remember { mutableStateOf(s.dumbbellStep.value) }
    var goal by remember { mutableIntStateOf(s.weeklyGoal.value) }
    var remind by remember { mutableStateOf(true) }

    val plateSets = listOf(
        "Standart salon" to listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f),
        "Ev / küçük salon" to listOf(20f, 10f, 5f, 2.5f, 1.25f),
        "Hassas (0,5'li)" to listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f, 0.5f)
    )

    fun finish() {
        val h = height.replace(',', '.').toFloatOrNull() ?: s.heightCm.value
        val w = weight.replace(',', '.').toFloatOrNull() ?: s.weightKg.value
        s.setProfile(name.trim().ifBlank { "Sporcu" }, h, w, age.toIntOrNull() ?: s.age.value, male)
        s.setBarWeight(bar)
        s.setPlates(plateSets[plateSet].second)
        s.setDumbbellStep(dbStep)
        s.setWeeklyGoal(goal)
        s.setReminderOn(remind)
        if (w > 0f) vm.saveBodyMetric(com.example.data.BodyMetricEntity(weightKg = w))
        s.onboardingDone = true
        onDone()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 22.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                (0..2).forEach { i ->
                    Box(
                        Modifier.weight(1f).height(4.dp).padding(horizontal = 2.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i <= step) MaterialTheme.fit.accent else MaterialTheme.fit.elevated)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("Atla", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.muted,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { s.onboardingDone = true; onDone() }.padding(8.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (step) {
                    0 -> {
                        Text("Hoş geldin 👋", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Güç seviyesi, vücut analizi ve ağırlık önerileri bu bilgilerle hesaplanır. İstediğin zaman Profil'den değiştirebilirsin.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)
                        FitTextField(name, { name = it }, "Adın", placeholder = "Örn: Nesimi")
                        OverlineText("Cinsiyet")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("Erkek", male, { male = true }); ChoiceChip("Kadın", !male, { male = false })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FitTextField(age, { age = it.filter(Char::isDigit).take(2) }, "Yaş", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                            FitTextField(height, { height = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) }, "Boy (cm)", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                            FitTextField(weight, { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) }, "Kilo (kg)", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                        }
                    }
                    1 -> {
                        Text("Salonun", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Ağırlık önerileri yalnızca salonunda gerçekten kurulabilecek ağırlıklara yuvarlanır.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)
                        OverlineText("Bar ağırlığı")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(20f, 15f, 10f, 7.5f).forEach { b -> ChoiceChip("${b.trimNum()} kg", bar == b, { bar = b }) }
                        }
                        OverlineText("Plakalar")
                        plateSets.forEachIndexed { i, (l, p) ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(if (plateSet == i) MaterialTheme.fit.accent.copy(alpha = 0.12f) else MaterialTheme.fit.elevated)
                                    .clickable { plateSet = i }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(if (plateSet == i) MaterialTheme.fit.accent else MaterialTheme.fit.muted.copy(alpha = 0.4f)))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(l, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(p.joinToString(" · ") { it.trimNum() } + " kg", style = MaterialTheme.typography.labelSmall.mono(), color = MaterialTheme.fit.muted)
                                }
                            }
                        }
                        Text("Plakaları ve sabit dambılları sonra Ayarlar › Ekipman'dan tek tek düzenleyebilirsin.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                        OverlineText("Dambıl artışı")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1f, 2f, 2.5f).forEach { d -> ChoiceChip("${d.trimNum()} kg", dbStep == d, { dbStep = d }) }
                        }
                    }
                    else -> {
                        Text("Haftalık hedef", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Haftada kaç gün antrenman yapmayı planlıyorsun? Program günlerine haftanın günü atarsan hedef otomatik oradan alınır.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (2..6).forEach { g -> ChoiceChip("$g gün", goal == g, { goal = g }) }
                        }
                        Row(Modifier.fillMaxWidth().clickable { remind = !remind }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = remind, onCheckedChange = { remind = it })
                            Text("Antrenman günlerinde hatırlat", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Hazır bir başlangıç programı eklendi. Program sekmesinden günleri ve hareketleri dilediğin gibi düzenle; " +
                            "ağırlık ve tekrar önerileri ilk seanslarından sonra otomatik oluşur.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) GhostButton("Geri", { step-- }, Modifier.weight(1f))
                AccentButton(if (step < 2) "İleri" else "Başlayalım", { if (step < 2) step++ else finish() }, Modifier.weight(1.5f))
            }
        }
    }
}

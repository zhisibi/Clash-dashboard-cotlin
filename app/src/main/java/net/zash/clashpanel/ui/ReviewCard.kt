package net.zash.clashpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zash.clashpanel.MainViewModel
import net.zash.clashpanel.i18n.t

private const val FEEDBACK_GROUP = "1127250124"

/**
 * Gentle review soft card (1.3.0, same as the HarmonyOS app's components/ReviewCard.ets): floats above the
 * bottom bar when MainViewModel's rate-limit logic decides to show it. Feedback comes first (copies the
 * support group number); "Rate" / "Don't remind me" stop automatic prompts for good, "Later" snoozes.
 */
@Composable
fun ReviewCard(vm: MainViewModel) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    Card0(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(p.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Star, null, Modifier.size(22.dp), tint = p.accentUi)
                }
                Spacer(Modifier.width(10.dp))
                Text(t("review_title"), Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = p.text)
                IconButton({ vm.reviewLater() }, Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(18.dp), tint = p.subtle)
                }
            }
            Text(t("review_body"), Modifier.padding(top = 8.dp), fontSize = 14.sp, lineHeight = 21.sp, color = p.subtle)
            Row(
                Modifier.padding(top = 10.dp).clip(CircleShape).clickable {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("feedback", FEEDBACK_GROUP))
                    vm.toast(t("copied"))
                    vm.reviewFeedback()
                }.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Forum, null, Modifier.size(18.dp), tint = p.accentUi)
                Spacer(Modifier.width(6.dp))
                Text(t("review_feedback"), fontSize = 14.sp, color = p.accent)
            }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ vm.reviewNever() }) { Text(t("review_never"), color = p.subtle, maxLines = 1) }
                Spacer(Modifier.weight(1f))
                Button({ vm.reviewLater() }, colors = ButtonDefaults.buttonColors(containerColor = p.chip, contentColor = p.text)) {
                    Text(t("review_later"), maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                Button({ vm.reviewRate() }, colors = primaryButtonColors()) { Text(t("review_rate"), maxLines = 1) }
            }
        }
    }
}

package de.vitalcoach.core.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import java.time.Duration

/** Auswählbare Claude-Modelle. Standard ist das leistungsstärkste Opus-Modell. */
enum class ClaudeModel(val id: String, val label: String, val description: String, val serverFallback: Boolean) {
    OPUS("claude-opus-5-5", "Claude Opus 5.5", "Beste Analysequalität (Standard)", true),
    SONNET("claude-sonnet-5-5", "Claude Sonnet 5.5", "Schneller und günstiger", true),
    HAIKU("claude-haiku-5-5", "Claude Haiku 5.5", "Am günstigsten, kürzere Analysen", false),
}

sealed class AiAnswer {
    data class Success(val text: String, val model: String, val truncated: Boolean) : AiAnswer()
    data class Failure(val message: String) : AiAnswer()
}

/**
 * KI-Coach über die Claude API (offizielles Anthropic Java SDK).
 * Benötigt einen eigenen API-Schlüssel von console.anthropic.com.
 */
class ClaudeCoach(private val apiKey: String) {

    fun analyze(report: String, question: String?, model: ClaudeModel = ClaudeModel.OPUS): AiAnswer {
        if (apiKey.isBlank()) return AiAnswer.Failure("Kein API-Schlüssel hinterlegt. Trage ihn unter „Mehr → KI-Coach“ ein.")
        val q = question?.trim().orEmpty()
        val userText = buildString {
            appendLine("Hier sind meine aktuellen Gesundheits- und Trainingsdaten:")
            appendLine()
            appendLine("<daten>")
            appendLine(report)
            appendLine("</daten>")
            appendLine()
            if (q.isNotEmpty()) {
                appendLine("Meine Frage: $q")
            } else {
                appendLine("Bitte analysiere meine Daten und gib mir konkrete Empfehlungen.")
            }
        }
        var client: AnthropicClient? = null
        return try {
            client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey.trim())
                .timeout(Duration.ofMinutes(4))
                .maxRetries(2)
                .build()
            val builder = MessageCreateParams.builder()
                .model(model.id)
                .maxTokens(16000L)
                .system(if (q.isNotEmpty()) SYSTEM_PROMPT + QUESTION_HINT else SYSTEM_PROMPT)
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
                .addUserMessage(userText)
            if (model.serverFallback) {
                // Server-seitiger Fallback, falls ein Sicherheitsfilter eine Anfrage fälschlich ablehnt.
                builder.addBeta("server-side-fallback-2026-07-01")
                builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }
            val msg = client.beta().messages().create(builder.build())
            val stop = msg.stopReason().orElse(null)
            if (stop == BetaStopReason.REFUSAL) {
                return AiAnswer.Failure("Die KI hat diese Anfrage abgelehnt. Formuliere deine Frage bitte anders.")
            }
            val text = msg.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("\n").trim()
            if (text.isEmpty()) {
                AiAnswer.Failure("Die KI hat keine Antwort geliefert. Bitte versuche es erneut.")
            } else {
                AiAnswer.Success(text, msg.model().toString(), stop == BetaStopReason.MAX_TOKENS)
            }
        } catch (e: UnauthorizedException) {
            AiAnswer.Failure("Der API-Schlüssel ist ungültig. Bitte prüfe ihn unter „Mehr → KI-Coach“.")
        } catch (e: PermissionDeniedException) {
            AiAnswer.Failure("Der API-Schlüssel hat keinen Zugriff auf dieses Modell. Wähle ein anderes Modell oder prüfe dein Anthropic-Konto.")
        } catch (e: RateLimitException) {
            AiAnswer.Failure("Zu viele Anfragen oder kein Guthaben mehr. Warte kurz oder prüfe dein Guthaben auf console.anthropic.com.")
        } catch (e: BadRequestException) {
            AiAnswer.Failure("Die Anfrage wurde abgelehnt (${e.message}).")
        } catch (e: InternalServerException) {
            AiAnswer.Failure("Der KI-Dienst ist gerade überlastet. Bitte versuche es in ein paar Minuten erneut.")
        } catch (e: AnthropicServiceException) {
            AiAnswer.Failure("Fehler vom KI-Dienst (Code ${e.statusCode()}). Bitte später erneut versuchen.")
        } catch (e: AnthropicIoException) {
            AiAnswer.Failure("Keine Verbindung zum KI-Dienst. Prüfe deine Internetverbindung.")
        } catch (e: Throwable) {
            AiAnswer.Failure("Unerwarteter Fehler: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            runCatching { client?.close() }
        }
    }

    companion object {
        val SYSTEM_PROMPT = """
            Du bist ein erfahrener Sportwissenschaftler, Schlaf- und Regenerationscoach. Du bekommst Kennzahlen, die die App „VitalCoach“ aus den Daten einer Samsung Galaxy Watch (über Health Connect) berechnet hat. Erstelle daraus eine persönliche, ehrliche und konkrete Analyse auf Deutsch.

            Regeln:
            - Nutze ausschließlich die gelieferten Daten und erfinde keine Werte. Fehlen Daten, sag das kurz.
            - Ordne die Werte fachlich ein (Normbereiche, persönliche Abweichungen) und erkläre Zusammenhänge zwischen Schlaf, HRV, Ruhepuls, Atemfrequenz und Trainingsbelastung.
            - Erkenne Muster im 14-Tage-Verlauf (z. B. Wochentage mit wenig Schlaf, Belastungsspitzen, steigender Ruhepuls).
            - Keine medizinischen Diagnosen. Bei auffälligen Werten (z. B. SpO2 wiederholt unter 92 %, mehrere Tage deutlich erhöhter Ruhepuls) empfiehl eine ärztliche Abklärung.
            - Sprich den Nutzer mit „du“ an. Schreibe klar, motivierend und auf den Punkt, ohne Floskeln.
            - Formatierung: Überschriften mit „## “, Aufzählungen mit „- “, Hervorhebungen sparsam mit **fett**. Keine Tabellen.

            Aufbau:
            ## Kurzfazit
            (2–3 Sätze)
            ## Erholung & Schlaf
            ## Training & Belastung
            ## Fitness & Langzeittrend
            ## Heute konkret
            (3 konkrete Punkte für heute)
            ## Langfristig
            (3 Gewohnheiten mit dem größten Hebel)

            Halte die gesamte Antwort bei etwa 350–500 Wörtern.
        """.trimIndent()

        private const val QUESTION_HINT =
            "\n\nDer Nutzer hat eine konkrete Frage gestellt: Beantworte zuerst diese Frage direkt und fundiert (eigene Überschrift „## Deine Frage“), danach nur die dafür relevanten Teile des Aufbaus."
    }
}

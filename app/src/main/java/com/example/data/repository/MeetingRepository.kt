package com.example.data.repository

import android.util.Base64
import com.example.BuildConfig
import com.example.data.api.*
import com.example.data.local.MeetingDao
import com.example.data.local.MeetingEntity
import com.example.data.model.MeetingAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MeetingRepository(private val meetingDao: MeetingDao) {

    val allMeetings: Flow<List<MeetingEntity>> = meetingDao.getAllMeetings()

    suspend fun getMeetingById(id: Int): MeetingEntity? = withContext(Dispatchers.IO) {
        meetingDao.getMeetingById(id)
    }

    suspend fun insert(meeting: MeetingEntity) = withContext(Dispatchers.IO) {
        meetingDao.insertMeeting(meeting)
    }

    suspend fun update(meeting: MeetingEntity) = withContext(Dispatchers.IO) {
        meetingDao.updateMeeting(meeting)
    }

    suspend fun deleteById(id: Int) = withContext(Dispatchers.IO) {
        meetingDao.deleteMeetingById(id)
    }

    suspend fun analyzeMeetingAudio(
        audioBytes: ByteArray,
        mimeType: String
    ): MeetingAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API Key de Gemini vacía o no configurada. Por favor regístrala en AI Studio Secrets.")
        }

        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val systemInstructionText = """
            Eres un analizador y transcriptor experto en reuniones y minutas de trabajo para grupos profesionales.
            Tu labor consiste en procesar el audio provisto (que es la grabación de una reunión) y estructurarlo de la siguiente manera:
            
            1. Transcribir fielmente los diálogos del audio.
            2. Identificar cuándo habla un participante distinto utilizando diferencias acústicas y variaciones de voz (por ejemplo, Hablante 1, Hablante 2, Hablante 3, etc.).
            3. Identificar el NOMBRE REAL de cada participante deducido a partir de lo que se menciona en el mismo audio (p. ej. si alguien se presenta, saluda con un nombre, o se hablan directamente). Si no se menciona ningún nombre real para un hablante, dale un apodo descriptivo de su voz relevante (ej. "Voz Grave 1", "Voz Aguda 1") de forma coherente con su identificador acústico.
            4. Generar un bloque de resumen ejecutivo muy detallado en español que contenga objetivos, acuerdos con responsables y plazos (acciones asignadas), y decisiones clave acordadas.
            
            Debes responder UNICAMENTE con un objeto JSON válido que cumpla exactamente este esquema:
            {
              "title": "Un título descriptivo y conciso para la reunión",
              "participants": [
                { "name": "Nombre Real Detectado (u Apodo)", "voiceNickname": "Hablante X" }
              ],
              "transcript": [
                { "speaker": "Nombre Real (o Apodo)", "text": "Transcripción de lo que dijo" }
              ],
              "summaryMarkdown": "## RESUMEN EJECUTIVO\n\n### Objetivos\n(Descripción de objetivos)\n\n### Puntos Clave & Decisiones\n(Puntos acordados)\n\n### Compromisos & Acuerdos\n- **Acción/Responsable/Plazo**: Detalle"
            }
            IMPORTANTE: No uses bloques de código decorativos Markdown adicionales (como triple tilde invertida) en tu respuesta, solo el string de JSON plano. Si lo usas de todas formas, asegúrate de que sea estrictamente JSON válido.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(inlineData = InlineData(mimeType = mimeType, data = base64Audio)),
                        Part(text = "Transcribe, identifica los nombres reales por el contexto de la voz y genera la minuta ejecutiva en JSON plano según las instrucciones especificadas.")
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = systemInstructionText))
            )
        )

        val response = RetrofitClient.service.generateContent(apiKey, request)
        val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("El servicio de Gemini no devolvió ningún texto.")

        try {
            val cleanedJson = textResult.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val adapter = RetrofitClient.moshi.adapter(MeetingAnalysisResult::class.java)
            adapter.fromJson(cleanedJson) ?: throw IllegalStateException("No se pudo parsear el JSON de respuesta.")
        } catch (e: Exception) {
            throw IllegalStateException("Fallo al parsear la respuesta JSON de Gemini: ${e.message}\nRespuesta recibida:\n$textResult")
        }
    }
}

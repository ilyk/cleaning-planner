package com.ilyk.cleaningplanner.feature.clara.ui.welcome

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.core.model.Avatar3DAsset
import com.ilyk.cleaningplanner.core.model.Avatar3DPrefs
import com.ilyk.cleaningplanner.core.model.AvatarPrefs
import com.ilyk.cleaningplanner.core.model.VoiceStyle
import com.ilyk.cleaningplanner.feature.clara.avatar.SceneViewAvatarProvider
import com.ilyk.cleaningplanner.core.model.OpenAIMessage
import com.ilyk.cleaningplanner.core.model.OpenAIRequestGPT5
import com.ilyk.cleaningplanner.data.network.api.OpenAIApi
import com.ilyk.cleaningplanner.feature.clara.data.Avatar3DPrefsDataStore
import com.ilyk.cleaningplanner.feature.clara.data.LanguagePrefsDataStore
import com.ilyk.cleaningplanner.feature.clara.data.OpenAIConfigDataStore
import com.ilyk.cleaningplanner.feature.clara.lipsync.VisemeEngine
import com.ilyk.cleaningplanner.feature.clara.repository.AvatarRepository
import com.ilyk.cleaningplanner.feature.clara.repository.ClaraRepository
import com.ilyk.cleaningplanner.feature.clara.repository.ClaraResult
import com.ilyk.cleaningplanner.feature.clara.service.StreamingTTSService
import com.ilyk.cleaningplanner.feature.clara.service.TTSService
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeUiState(
    val avatarPrefs: Avatar3DPrefs = Avatar3DPrefs(),
    val currentAvatar: Avatar3DAsset? = null,
    val currentSubtitle: String = "",
    val isWelcomeSpeaking: Boolean = false,
    val followUpMessage: String = "",
    val isLoadingFollowUp: Boolean = false,
    val isGeneratingWelcome: Boolean = false
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val claraRepository: ClaraRepository,
    private val ttsService: TTSService,
    private val streamingTTSService: StreamingTTSService,
    private val avatar3DPrefsDataStore: Avatar3DPrefsDataStore,
    private val avatarRepository: AvatarRepository,
    private val languagePrefsDataStore: LanguagePrefsDataStore,
    private val openAIConfigDataStore: OpenAIConfigDataStore,
    private val openAIApi: OpenAIApi,
    val avatarProvider: SceneViewAvatarProvider,
    private val visemeEngine: VisemeEngine
) : ViewModel() {

    val ttsState = ttsService.state
    val languageCode = languagePrefsDataStore.languageCode
    
    companion object {
        fun getWelcomeText(lang: String): String = when (lang) {
            "es" -> """¡Hola! Soy Clara, tu asistente de planificación de limpieza.
Bienvenido al mundo de la Planificación de Limpieza, donde hacemos que cuidar tu hogar sea simple, compartido e incluso un poco disfrutable.

Para empezar, solo cuéntame sobre tu hogar, lo que se te ocurra.
Puedes hablar conmigo, escribirlo, o si prefieres paso a paso, podemos usar algunos formularios y preguntas rápidas.

Mientras hablamos, organizaré tranquilamente lo que me digas en tu plan: habitaciones, personas y rutinas, para que no tengas que pensar en los detalles.
Puedes continuar todo el tiempo que quieras, agregar cosas más tarde o parar cuando sientas que es suficiente.

Me verás aquí abajo como un avatar amigable con subtítulos para que puedas leer.
Toca el botón de avatar AI en la esquina inferior derecha para cambiar cómo me veo o sueno, o para apagarme si prefieres solo texto.
Siempre estaré aquí cuando me necesites de nuevo."""
            
            "fr" -> """Bonjour ! Je suis Clara, votre assistante de planification de nettoyage.
Bienvenue dans le monde de la Planification de Nettoyage, où nous rendons l'entretien de votre maison simple, partagé et même un peu agréable.

Pour commencer, parlez-moi simplement de votre foyer — tout ce qui vous vient à l'esprit.
Vous pouvez discuter avec moi, taper votre texte, ou si vous préférez étape par étape, nous pouvons utiliser quelques formulaires et questions rapides.

Pendant que nous parlons, j'organiserai tranquillement ce que vous me dites dans votre plan — des choses comme les pièces, les personnes et les routines — pour que vous n'ayez pas à penser aux détails.
Vous pouvez continuer aussi longtemps que vous le souhaitez, ajouter des choses plus tard ou arrêter quand vous le voulez.

Vous me verrez ici en bas comme un avatar amical avec des sous-titres pour que vous puissiez lire.
Appuyez sur le bouton d'avatar IA dans le coin inférieur droit pour changer mon apparence ou mon son, ou pour m'éteindre si vous préférez juste du texte.
Je serai toujours là quand vous voudrez me retrouver."""
            
            "de" -> """Hallo! Ich bin Clara — deine Assistentin für Reinigungsplanung.
Willkommen in der Welt der Reinigungsplanung, wo wir die Pflege deines Zuhauses einfach, geteilt und sogar ein bisschen angenehm machen.

Um zu beginnen, erzähl mir einfach von deinem Haushalt — was dir in den Sinn kommt.
Du kannst mit mir chatten, es eintippen, oder wenn du es Schritt für Schritt bevorzugst, können wir stattdessen ein paar schnelle Formulare und Fragen verwenden.

Während wir sprechen, werde ich leise organisieren, was du mir sagst, in deinen Plan — Dinge wie Räume, Personen und Routinen — damit du nicht an die Details denken musst.
Du kannst so lange weitermachen, wie du möchtest, später Dinge hinzufügen oder jederzeit aufhören, wenn du das Gefühl hast, es reicht.

Du wirst mich hier unten als freundlichen Avatar mit Untertiteln sehen, damit du mitlesen kannst.
Tippe auf den AI-Avatar-Button in der unteren rechten Ecke, um zu ändern, wie ich aussehe oder klinge, oder um mich auszuschalten, wenn du nur Text bevorzugst.
Ich werde immer da sein, wenn du mich wieder haben möchtest."""
            
            "uk" -> """Привіт! Я Клара — твій асистент з планування прибирання.
Ласкаво просимо до світу Планування Прибирання, де ми робимо догляд за твоїм домом простим, спільним і навіть трохи приємним.

Щоб почати, просто розкажи мені про свій будинок — що спаде на думку.
Ти можеш поспілкуватися зі мною, надрукувати це, або, якщо бажаєш, крок за кроком, ми можемо використовувати кілька швидких форм і питань.

Поки ми розмовляємо, я тихо організую те, що ти мені скажеш, у твій план — такі речі, як кімнати, люди та рутини — щоб тобі не доводилося думати про деталі.
Ти можеш продовжувати стільки, скільки хочеш, додавати речі пізніше або зупинитися, коли відчуєш, що цього достатньо.

Ти побачиш мене тут внизу як дружній аватар із субтитрами, щоб ти міг читати разом.
Натисни кнопку AI аватара в правому нижньому куті, щоб змінити, як я виглядаю або звучу, або вимкнути мене, якщо хочеш просто текст.
Я завжди буду тут, коли ти захочеш мене повернути."""
            
            else -> """Hi! I'm Clara — your cleaning planning assistant.
Welcome to the world of Cleaning Planning, where we make looking after your home simple, shared, and even a bit enjoyable.

To get started, just tell me about your household — whatever comes to mind.
You can chat with me, type it out, or, if you'd rather go step by step, we can use a few quick forms and questions instead.

As we talk, I'll quietly organize what you tell me into your plan — things like rooms, people, and routines — so you don't have to think about the details.
You can keep going for as long as you like, add things later, or stop whenever you feel it's enough.

You'll see me down here as a friendly avatar with subtitles so you can read along.
Tap the AI avatar button in the bottom-right corner to change how I look or sound, or to turn me off if you prefer just text.
I'll always be there when you want me back."""
        }
    }

    private val _followUpState = MutableStateFlow<Pair<String, Boolean>>("" to false)
    private val _currentLanguage = MutableStateFlow("en")
    private val _isGeneratingWelcome = MutableStateFlow(false)
    private val _streamingCaption = MutableStateFlow("")
    
    val uiState: StateFlow<WelcomeUiState> = combine(
        avatar3DPrefsDataStore.avatar3DPrefs,
        avatarRepository.allAvatars,
        _followUpState,
        _currentLanguage,
        _isGeneratingWelcome,
        _streamingCaption
    ) { flows: Array<Any?> ->
        val prefs = flows[0] as Avatar3DPrefs
        val avatars = flows[1] as List<Avatar3DAsset>
        val followUpPair = flows[2] as Pair<String, Boolean>
        val lang = flows[3] as String
        val generating = flows[4] as Boolean
        val caption = flows[5] as String
        
        val followUp = followUpPair.first
        val isLoading = followUpPair.second
        
        val displayText = when {
            caption.isNotEmpty() -> caption // Streaming caption
            followUp.isNotEmpty() -> followUp // Follow-up message
            else -> "" // No text while generating
        }
        
        WelcomeUiState(
            avatarPrefs = prefs,
            currentAvatar = avatars.find { it.id == prefs.appearanceId },
            currentSubtitle = displayText,
            followUpMessage = followUp,
            isLoadingFollowUp = isLoading,
            isGeneratingWelcome = generating
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WelcomeUiState()
    )

    fun startWelcome(language: String = "en") {
        viewModelScope.launch {
            // Stop any existing playback first
            Log.d("WelcomeViewModel", "Stopping previous TTS before starting lang=$language")
            ttsService.stop()
            streamingTTSService.stop()
            visemeEngine.stop()
            
            // Save language preference
            languagePrefsDataStore.setLanguage(language)
            _currentLanguage.value = language
            
            val prefs = uiState.value.avatarPrefs
            Log.d("WelcomeViewModel", "startWelcome: lang=$language, prefs=$prefs")
            Log.d("WelcomeViewModel", "muteVoice=${prefs.muteVoice}, showAvatar=${prefs.showAvatar}")

            if (!prefs.muteVoice && prefs.showAvatar) {
                // Generate dynamic welcome using GPT-5
                generateAndSpeakWelcome(language, prefs)
            } else {
                // Show static text without speaking
                Log.d("WelcomeViewModel", "TTS skipped - muteVoice=${prefs.muteVoice}, showAvatar=${prefs.showAvatar}")
                val staticText = getWelcomeText(language)
                _followUpState.value = staticText to false
            }
        }
    }
    
    private suspend fun generateAndSpeakWelcome(language: String, prefs: Avatar3DPrefs) {
        val langName = when (language) {
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "uk" -> "Ukrainian"
            else -> "English"
        }
        
        val prompt = """Please welcome a new user to our cleaning planning app IN $langName. Tell them:
- You're Clara, their cleaning planning assistant
- They can share household info by voice chat, typing, or guided wizard
- You'll organize rooms, people, and routines as they talk
- They can customize or disable you via the avatar button (bottom-right)

Be warm and encouraging. Use 2-3 friendly paragraphs IN $langName. Vary your wording each time."""

        Log.d("WelcomeViewModel", "Generating dynamic welcome with GPT-4o")
        
        // Show loading state
        _isGeneratingWelcome.value = true
        _streamingCaption.value = ""
        
        try {
            val config = openAIConfigDataStore.openAIConfig.first()
            
            // Custom system prompt for welcome (not the short 1-2 sentence one)
            val welcomeSystemPrompt = "You are Clara, a warm and friendly cleaning planning assistant. You respond in $langName. Generate natural, conversational welcome messages. Be encouraging and helpful."
            
            // Use gpt-4o for welcome (GPT-5 has issues with longer responses)
            val request = com.ilyk.cleaningplanner.core.model.OpenAIRequestLegacy(
                model = "gpt-4o",
                messages = listOf(
                    OpenAIMessage(role = "system", content = welcomeSystemPrompt),
                    OpenAIMessage(role = "user", content = prompt)
                ),
                temperature = 0.8,
                topP = 0.9,
                maxTokens = 300
            )
            
            val response = openAIApi.createChatCompletion(
                authorization = "Bearer ${config.apiKey}",
                request = request
            )
            
            val welcomeText = response.choices.firstOrNull()?.message?.content ?: ""
            
            if (welcomeText.isNotBlank()) {
                Log.d("WelcomeViewModel", "Got dynamic welcome, length=${welcomeText.length}")
                
                _isGeneratingWelcome.value = false
                
                // Stream the welcome text and audio with caption
                val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
                
                streamingTTSService.speakStreaming(
                    text = welcomeText,
                    onTextChunk = { chunk ->
                        // Update streaming caption (auto-scrolls)
                        _streamingCaption.value = _streamingCaption.value + chunk
                    },
                    onComplete = {
                        Log.d("WelcomeViewModel", "Welcome complete")
                        // Keep final text visible
                        _followUpState.value = _streamingCaption.value to false
                        _streamingCaption.value = ""
                    },
                    onError = { error ->
                        Log.e("WelcomeViewModel", "Error in welcome: $error")
                        _isGeneratingWelcome.value = false
                        // Fallback to static text
                        _followUpState.value = getWelcomeText(language) to false
                        _streamingCaption.value = ""
                    }
                )
            } else {
                // Empty response, use fallback
                Log.w("WelcomeViewModel", "GPT returned empty welcome, using fallback")
                _isGeneratingWelcome.value = false
                val welcomeText = getWelcomeText(language)
                val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
                _followUpState.value = welcomeText to false
                ttsService.speak(welcomeText, voiceStyle)
            }
        } catch (e: Exception) {
            Log.e("WelcomeViewModel", "Failed to generate welcome", e)
            _isGeneratingWelcome.value = false
            // Fallback to static text
            val welcomeText = getWelcomeText(language)
            val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
            _followUpState.value = welcomeText to false
            ttsService.speak(welcomeText, voiceStyle)
        }
    }
    
    private fun estimateDuration(text: String): Long {
        // Rough estimate: ~150 words per minute = ~2.5 words per second
        val words = text.split(" ").size
        return (words / 2.5 * 1000).toLong()
    }

    fun onOptionSelected(option: String) {
        viewModelScope.launch {
            _followUpState.value = "" to true
            
            when (val result = claraRepository.getClaraResponse(option)) {
                is ClaraResult.Success -> {
                    val message = result.message
                    _followUpState.value = message to false
                    
                    val prefs = uiState.value.avatarPrefs
                    if (!prefs.muteVoice && prefs.showAvatar) {
                        val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
                        ttsService.speak(message, voiceStyle)
                        
                        // Generate and play visemes
                        val visemes = visemeEngine.phonemesToVisemes(message, estimateDuration(message))
                        visemeEngine.playVisemes(visemes, avatarProvider)
                    }
                }
                is ClaraResult.Error -> {
                    val message = result.fallback
                    _followUpState.value = message to false
                    
                    val prefs = uiState.value.avatarPrefs
                    if (!prefs.muteVoice && prefs.showAvatar) {
                        val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
                        ttsService.speak(message, voiceStyle)
                    }
                }
            }
        }
    }

    fun stopSpeaking() {
        viewModelScope.launch {
            Log.d("WelcomeViewModel", "Stopping all speech services")
            ttsService.stop()
            streamingTTSService.stop()
            visemeEngine.stop()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopSpeaking()
    }
}


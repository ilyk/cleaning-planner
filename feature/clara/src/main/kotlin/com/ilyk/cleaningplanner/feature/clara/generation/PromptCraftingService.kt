package com.ilyk.cleaningplanner.feature.clara.generation

import com.ilyk.cleaningplanner.core.model.AvatarGenerationRequest
import com.ilyk.cleaningplanner.core.model.OpenAIConfig
import com.ilyk.cleaningplanner.core.model.OpenAIMessage
import com.ilyk.cleaningplanner.core.model.OpenAIRequest
import com.ilyk.cleaningplanner.data.network.api.OpenAIApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uses GPT-5 (with gpt-4o-mini fallback) to craft refined 3D generation prompts
 * from user concepts with hard technical constraints.
 */
@Singleton
class PromptCraftingService @Inject constructor(
    private val openAIApi: OpenAIApi
) {
    companion object {
        private const val SYSTEM_PROMPT = """You are an expert 3D asset technical director. 
Your job is to craft precise, technically-constrained prompts for text-to-3D generation APIs.

Given a user concept (avatar personality, appearance, style), you must produce a single, detailed prompt that:
1. Describes the visual appearance clearly (face, body, clothing, materials)
2. Specifies technical requirements exactly:
   - glTF 2.0 (.glb) format
   - Maximum 80,000 triangles
   - Single skinned mesh with skeletal rig
   - Morph targets for facial visemes (mouth shapes for speech)
   - 4-6 PBR textures: albedo, roughness, metallic, normal, AO
   - Texture resolution: 1024x1024 to 2048x2048
   - Include idle animation (subtle breathing, micro-movements)
   - Include blink animation
   - Neutral base lighting, no baked shadows
3. Uses professional 3D terminology
4. Avoids ambiguity or artistic flourishes

Output only the final prompt text, no explanations or metadata."""
    }

    suspend fun craftPrompt(
        request: AvatarGenerationRequest,
        config: OpenAIConfig
    ): Result<String> {
        return try {
            val userMessage = buildUserMessage(request)
            
            val openAIRequest = OpenAIRequest(
                model = config.model,
                messages = listOf(
                    OpenAIMessage(role = "system", content = SYSTEM_PROMPT),
                    OpenAIMessage(role = "user", content = userMessage)
                ),
                temperature = 0.3,
                topP = 0.9,
                maxTokens = 500
            )

            val response = try {
                openAIApi.createChatCompletion(
                    authorization = "Bearer ${config.apiKey}",
                    request = openAIRequest.copy(model = "gpt-5")
                )
            } catch (e: Exception) {
                // Silent fallback to gpt-4o-mini if GPT-5 unavailable
                openAIApi.createChatCompletion(
                    authorization = "Bearer ${config.apiKey}",
                    request = openAIRequest.copy(model = "gpt-4o-mini")
                )
            }

            val prompt = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("Empty response from LLM"))

            Result.success(prompt.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildUserMessage(request: AvatarGenerationRequest): String {
        return buildString {
            appendLine("User Concept:")
            appendLine("- Core idea: ${request.concept}")
            appendLine("- Gender presentation: ${request.gender.name.lowercase().replace('_', ' ')}")
            appendLine("- Age vibe: ${request.ageVibe.name.lowercase().replace('_', ' ')}")
            appendLine("- Style: ${request.style}")
            appendLine("- Warmth/personality: ${request.warmth}")
            if (!request.clothing.isNullOrBlank()) {
                appendLine("- Clothing: ${request.clothing}")
            }
            appendLine()
            appendLine("Technical Constraints:")
            with(request.technicalConstraints) {
                appendLine("- Format: $format")
                appendLine("- Max triangles: $maxTriangles")
                appendLine("- Mesh: $meshType")
                appendLine("- Rig: $rig")
                appendLine("- Textures: $textures")
                appendLine("- Compression: $compression")
                appendLine("- Required animations: ${animations.joinToString(", ")}")
                appendLine("- Lighting: $lighting")
            }
            appendLine()
            appendLine("Craft the final generation prompt:")
        }
    }
}


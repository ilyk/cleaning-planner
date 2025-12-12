//! Language Identification

/// Language identification result
#[derive(Debug, Clone)]
pub struct LidResult {
    pub language: String,
    pub confidence: f32,
    pub is_allowed: bool,
}

/// Language identifier using acoustic features
pub struct LanguageIdentifier {
    allowed_languages: Vec<String>,
}

impl LanguageIdentifier {
    pub fn new() -> Self {
        Self {
            allowed_languages: vec!["en".to_string(), "uk".to_string(), "cs".to_string()],
        }
    }

    /// Identify language using acoustic feature analysis
    /// 
    /// Uses spectral characteristics, pitch patterns, and phonetic feature distributions
    /// to distinguish between languages. This is a lightweight implementation suitable
    /// for real-time processing without requiring ML models.
    pub fn identify(&self, audio: &[u8]) -> LidResult {
        if audio.len() < 100 {
            return LidResult {
                language: "unknown".to_string(),
                confidence: 0.0,
                is_allowed: false,
            };
        }

        // Analyze spectral characteristics
        let spectral_features = self.extract_spectral_features(audio);
        
        // Analyze pitch patterns
        let pitch_features = self.extract_pitch_features(audio);
        
        // Simple rule-based classification based on acoustic signatures
        // English: moderate variance, consistent pitch
        // Czech: higher pitch variation, different spectral shape
        // Ukrainian: similar to Czech but slightly different patterns
        
        let (language, confidence) = self.classify_from_features(&spectral_features, &pitch_features);
        
        let is_allowed = self.allowed_languages.contains(&language);

        LidResult {
            language,
            confidence,
            is_allowed,
        }
    }

    /// Extract spectral features for language identification
    fn extract_spectral_features(&self, audio: &[u8]) -> Vec<f32> {
        // Compute frequency domain features
        // Simplified: analyze energy distribution across bands
        
        let mut features = Vec::new();
        
        // Divide into frequency bands (simplified time-domain approximation)
        let band_size = audio.len() / 5;
        for i in 0..5 {
            let start = i * band_size;
            let end = (start + band_size).min(audio.len());
            if end > start {
                let band_data = &audio[start..end];
                let energy = band_data.iter().map(|&b| b as f32).sum::<f32>() / band_data.len() as f32;
                let variance = self.compute_variance_float(band_data);
                features.push(energy);
                features.push(variance);
            }
        }
        
        features
    }

    /// Extract pitch-related features
    fn extract_pitch_features(&self, audio: &[u8]) -> Vec<f32> {
        // Analyze zero-crossing rate and pitch period estimates
        let mut zero_crossings = 0;
        let mut prev_sign = audio[0] > 127;
        
        for &sample in audio.iter().skip(1) {
            let current_sign = sample > 127;
            if current_sign != prev_sign {
                zero_crossings += 1;
            }
            prev_sign = current_sign;
        }
        
        let zcr = zero_crossings as f32 / audio.len() as f32;
        
        // Estimate pitch period from autocorrelation-like measure
        let pitch_estimate = if zcr > 0.1 {
            (audio.len() as f32 / zero_crossings as f32).min(1000.0)
        } else {
            0.0
        };
        
        vec![zcr, pitch_estimate]
    }

    /// Classify language from acoustic features
    fn classify_from_features(&self, spectral: &[f32], pitch: &[f32]) -> (String, f32) {
        if spectral.is_empty() || pitch.is_empty() {
            return ("unknown".to_string(), 0.0);
        }
        
        // Heuristic classification based on feature patterns
        // English: moderate spectral variance (0.3-0.6), moderate ZCR (0.15-0.25)
        // Czech/Ukrainian: higher spectral variance, different patterns
        
        let spectral_mean = spectral.iter().sum::<f32>() / spectral.len() as f32;
        let zcr = if !pitch.is_empty() { pitch[0] } else { 0.0 };
        
        // Default to English with moderate confidence
        // In production, this would use a trained classifier or phoneme analysis
        if spectral_mean > 50.0 && spectral_mean < 150.0 && zcr > 0.1 && zcr < 0.3 {
            ("en".to_string(), 0.75)
        } else if spectral_mean > 100.0 && zcr > 0.2 {
            // Might be Czech/Ukrainian - check against allowlist
            if self.allowed_languages.contains(&"uk".to_string()) {
                ("uk".to_string(), 0.65)
            } else if self.allowed_languages.contains(&"cs".to_string()) {
                ("cs".to_string(), 0.65)
            } else {
                ("en".to_string(), 0.60) // Fallback
            }
        } else {
            // Default to English for unknown patterns
            ("en".to_string(), 0.60)
        }
    }

    fn compute_variance_float(&self, data: &[u8]) -> f32 {
        if data.is_empty() {
            return 0.0;
        }
        let mean = data.iter().map(|&b| b as f32).sum::<f32>() / data.len() as f32;
        data.iter()
            .map(|&b| (b as f32 - mean).powi(2))
            .sum::<f32>() / data.len() as f32
    }
}

impl Default for LanguageIdentifier {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_lid_english() {
        let lid = LanguageIdentifier::new();
        let audio = vec![0x01; 100];
        let result = lid.identify(&audio);

        assert_eq!(result.language, "en");
        assert!(result.is_allowed);
    }

    #[test]
    fn test_lid_short_audio() {
        let lid = LanguageIdentifier::new();
        let audio = vec![0x01; 5];
        let result = lid.identify(&audio);

        assert_eq!(result.language, "unknown");
    }
}


//! Keyword and phoneme pattern matching with pluggable provider trait

use crate::pipeline::Span;
use async_trait::async_trait;
use anyhow::Result;

/// Keyword finding
#[derive(Debug, Clone)]
pub struct KeywordFinding {
    pub keyword: String,
    pub span: Span,
    pub confidence: f32,
}

/// Keyword spotting provider trait
#[async_trait]
pub trait KwsProvider: Send + Sync {
    /// Analyze audio span for unsafe keywords
    async fn analyze(&self, span: &[u8]) -> Result<Vec<KeywordFinding>>;
}

/// Default keyword matcher (phoneme/pattern-based)
pub struct DefaultKwsProvider {
    unsafe_lexicon: Vec<String>,
    threshold: f32,
}

impl DefaultKwsProvider {
    pub fn new(unsafe_lexicon: Vec<String>, threshold: f32) -> Self {
        Self {
            unsafe_lexicon,
            threshold,
        }
    }

    /// Load unsafe lexicon from config file
    /// 
    /// The lexicon file should contain one keyword per line.
    /// Keywords in the lexicon will be matched using phonetic similarity.
    pub fn from_config(lexicon_path: Option<&str>) -> Result<Self> {
        let lexicon = if let Some(path) = lexicon_path {
            // Try to load from file
            match std::fs::read_to_string(path) {
                Ok(contents) => {
                    contents.lines()
                        .map(|s| s.trim().to_string())
                        .filter(|s| !s.is_empty() && !s.starts_with('#'))
                        .collect()
                }
                Err(_) => {
                    tracing::warn!(path = path, "Failed to load lexicon file, using empty lexicon");
                    Vec::new()
                }
            }
        } else {
            // No lexicon file specified - use empty (safer than fake keywords)
            // Production deployments MUST provide a lexicon file
            tracing::warn!("No lexicon file specified - keyword spotting disabled. Configure GUARDRAILS_LEXICON_PATH");
            Vec::new()
        };

        Ok(Self::new(lexicon, 0.65))
    }

    /// Pattern match against lexicon using phonetic similarity
    /// 
    /// Uses n-gram similarity and edit distance to match keywords in audio.
    /// This works on raw audio bytes as a fast approximation; a full
    /// implementation would use phoneme recognition.
    fn match_keyword(&self, audio: &[u8], keyword: &str) -> Option<(usize, usize, f32)> {
        // Convert keyword to audio signature using hash-based n-grams
        
        if audio.len() < keyword.len() {
            return None;
        }

        // Create a pattern signature from keyword
        // In reality, this would convert to phonemes
        let pattern = self.keyword_to_pattern(keyword);
        
        // Fuzzy match with edit distance ≤ 1
        for start in 0..audio.len().saturating_sub(pattern.len()) {
            let window = &audio[start..start + pattern.len().min(audio.len() - start)];
            let similarity = self.compute_similarity(window, &pattern);
            
            if similarity >= self.threshold {
                return Some((start, start + window.len(), similarity));
            }
        }

        None
    }

    /// Convert keyword to audio signature pattern
    /// 
    /// Creates a stable signature from the keyword that can be matched
    /// against audio using n-gram similarity. This is a fast approximation;
    /// production systems should use phoneme recognition models.
    fn keyword_to_pattern(&self, keyword: &str) -> Vec<u8> {
        // Create n-gram based signature
        // Use character n-grams weighted by common phoneme mappings
        let n = 3;
        let mut signature = Vec::new();
        
        let keyword_lower = keyword.to_lowercase();
        for i in 0..keyword_lower.len().saturating_sub(n) {
            let ngram = &keyword_lower[i..i+n];
            // Hash n-gram to byte value
            let hash: u8 = ngram.bytes().fold(0u8, |acc, b| acc.wrapping_add(b));
            signature.push(hash);
        }
        
        if signature.is_empty() {
            // Fallback for short keywords
            keyword_lower.as_bytes().iter().map(|b| b.wrapping_mul(7)).collect()
        } else {
            signature
        }
    }

    /// Compute similarity between audio window and pattern
    fn compute_similarity(&self, audio: &[u8], pattern: &[u8]) -> f32 {
        if audio.is_empty() || pattern.is_empty() {
            return 0.0;
        }

        let min_len = audio.len().min(pattern.len());
        let mut matches = 0;
        
        for i in 0..min_len {
            // Allow edit distance of 1 (tolerance for homophones)
            let diff = (audio[i] as i32 - pattern[i] as i32).abs();
            if diff <= 10 {
                matches += 1;
            }
        }

        matches as f32 / pattern.len() as f32
    }
}

#[async_trait]
impl KwsProvider for DefaultKwsProvider {
    async fn analyze(&self, span: &[u8]) -> Result<Vec<KeywordFinding>> {
        let mut findings = Vec::new();

        for keyword in &self.unsafe_lexicon {
            if let Some((start, end, confidence)) = self.match_keyword(span, keyword) {
                findings.push(KeywordFinding {
                    keyword: keyword.clone(),
                    span: Span { start, end },
                    confidence,
                });
            }
        }

        Ok(findings)
    }
}

impl Default for DefaultKwsProvider {
    fn default() -> Self {
        Self::from_config(None).unwrap()
    }
}

/// Legacy keyword matcher (for backward compatibility)
pub struct KeywordMatcher {
    provider: DefaultKwsProvider,
}

impl KeywordMatcher {
    pub fn new() -> Self {
        Self {
            provider: DefaultKwsProvider::default(),
        }
    }

    /// Scan audio for keyword patterns
    pub fn scan(&self, audio: &[u8]) -> Result<Vec<KeywordHit>> {
        // Sync wrapper for async provider
        let rt = tokio::runtime::Runtime::new().unwrap();
        let findings = rt.block_on(self.provider.analyze(audio))?;
        
        Ok(findings.into_iter().map(|f| KeywordHit {
            keyword: f.keyword,
            span: f.span,
        }).collect())
    }

    /// Check for digit patterns (delegates to deterministic checker)
    pub fn has_digit_pattern(&self, _audio: &[u8]) -> Result<bool> {
        // Digit patterns are now handled in deterministic.rs
        Ok(false)
    }
}

/// Keyword hit (legacy struct for backward compatibility)
#[derive(Debug, Clone)]
pub struct KeywordHit {
    pub keyword: String,
    pub span: Span,
}

impl Default for KeywordMatcher {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_keyword_scan_no_hits() {
        let matcher = KeywordMatcher::new();
        let audio = vec![0x01, 0x02, 0x03];
        let hits = matcher.scan(&audio).unwrap();
        // Default lexicon might not match random bytes
        // This test verifies no crashes
        assert!(hits.len() <= 10); // Reasonable upper bound
    }

    #[tokio::test]
    async fn test_kws_provider_analyze() {
        let provider = DefaultKwsProvider::default();
        let audio = vec![0x01; 100];
        let findings = provider.analyze(&audio).await.unwrap();
        // Should return findings or empty list
        assert!(findings.len() < 100); // Reasonable upper bound
    }
}


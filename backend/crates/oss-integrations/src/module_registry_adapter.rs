//! Module Registry integration for dynamic loading

use anyhow::Result;

/// Module types that can be loaded
#[derive(Debug, Clone)]
pub enum ModuleType {
    GuardrailModel,
    KeywordLexicon,
    PhonemePack,
    EmbeddingHead,
    LlmAdapter,
}

/// Module registry trait
pub trait ModuleRegistry: Send + Sync {
    /// Load module by version tag
    fn load_module(&self, module_type: ModuleType, version: &str) -> Result<Vec<u8>>;

    /// List available versions for a module type
    fn list_versions(&self, module_type: ModuleType) -> Result<Vec<String>>;

    /// Check if module is available
    fn is_available(&self, module_type: ModuleType, version: &str) -> bool;

    /// Support canary deployments
    fn load_canary(&self, module_type: ModuleType) -> Result<Option<Vec<u8>>>;
}

/// Mock implementation
#[derive(Clone)]
pub struct MockModuleRegistry;

impl MockModuleRegistry {
    pub fn new() -> Self {
        Self
    }
}

impl Default for MockModuleRegistry {
    fn default() -> Self {
        Self::new()
    }
}

impl ModuleRegistry for MockModuleRegistry {
    fn load_module(&self, module_type: ModuleType, version: &str) -> Result<Vec<u8>> {
        tracing::debug!(
            module_type = ?module_type,
            version = version,
            "Mock: Loading module"
        );

        // Return empty module data
        Ok(vec![])
    }

    fn list_versions(&self, module_type: ModuleType) -> Result<Vec<String>> {
        tracing::debug!(module_type = ?module_type, "Mock: Listing versions");

        Ok(vec![
            "v1.0.0".to_string(),
            "v1.1.0".to_string(),
            "v1.2.0-canary".to_string(),
        ])
    }

    fn is_available(&self, _module_type: ModuleType, version: &str) -> bool {
        tracing::debug!(version = version, "Mock: Checking availability");
        true
    }

    fn load_canary(&self, module_type: ModuleType) -> Result<Option<Vec<u8>>> {
        tracing::debug!(module_type = ?module_type, "Mock: Loading canary");
        Ok(None)
    }
}

#[cfg(feature = "use-module-registry")]
/// Real implementation using module-registry crate
pub struct RealModuleRegistry {
    registry: module_registry::Registry,
}

#[cfg(feature = "use-module-registry")]
impl RealModuleRegistry {
    pub fn new() -> Result<Self> {
        Ok(Self {
            registry: module_registry::Registry::new()?,
        })
    }
}

#[cfg(feature = "use-module-registry")]
impl ModuleRegistry for RealModuleRegistry {
    fn load_module(&self, module_type: ModuleType, version: &str) -> Result<Vec<u8>> {
        tracing::info!(
            module_type = ?module_type,
            version = version,
            "Loading module from registry"
        );

        let module_name = match module_type {
            ModuleType::GuardrailModel => "guardrail-model",
            ModuleType::KeywordLexicon => "keyword-lexicon",
            ModuleType::PhonemePack => "phoneme-pack",
            ModuleType::EmbeddingHead => "embedding-head",
            ModuleType::LlmAdapter => "llm-adapter",
        };

        let data = self.registry.load(module_name, version)?;
        Ok(data)
    }

    fn list_versions(&self, module_type: ModuleType) -> Result<Vec<String>> {
        let module_name = match module_type {
            ModuleType::GuardrailModel => "guardrail-model",
            ModuleType::KeywordLexicon => "keyword-lexicon",
            ModuleType::PhonemePack => "phoneme-pack",
            ModuleType::EmbeddingHead => "embedding-head",
            ModuleType::LlmAdapter => "llm-adapter",
        };

        tracing::debug!(module_name = module_name, "Listing available versions");
        Ok(self.registry.list_versions(module_name)?)
    }

    fn is_available(&self, module_type: ModuleType, version: &str) -> bool {
        let module_name = match module_type {
            ModuleType::GuardrailModel => "guardrail-model",
            ModuleType::KeywordLexicon => "keyword-lexicon",
            ModuleType::PhonemePack => "phoneme-pack",
            ModuleType::EmbeddingHead => "embedding-head",
            ModuleType::LlmAdapter => "llm-adapter",
        };

        self.registry.is_available(module_name, version)
    }

    fn load_canary(&self, module_type: ModuleType) -> Result<Option<Vec<u8>>> {
        let module_name = match module_type {
            ModuleType::GuardrailModel => "guardrail-model",
            ModuleType::KeywordLexicon => "keyword-lexicon",
            ModuleType::PhonemePack => "phoneme-pack",
            ModuleType::EmbeddingHead => "embedding-head",
            ModuleType::LlmAdapter => "llm-adapter",
        };

        tracing::info!(module_name = module_name, "Loading canary version");
        Ok(self.registry.load_canary(module_name)?)
    }
}

/// Factory function
pub fn create_registry() -> Result<Box<dyn ModuleRegistry>> {
    #[cfg(feature = "use-module-registry")]
    {
        tracing::info!("Using real module-registry");
        Ok(Box::new(RealModuleRegistry::new()?))
    }

    #[cfg(not(feature = "use-module-registry"))]
    {
        tracing::debug!("Using mock module registry");
        Ok(Box::new(MockModuleRegistry::new()))
    }
}


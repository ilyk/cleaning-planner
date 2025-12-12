//! Printable PDF generation service

use crate::models::*;
use anyhow::Result;
use async_trait::async_trait;

#[async_trait]
pub trait PrintableService: Send + Sync {
    /// Generate a printable PDF for a plan
    async fn generate_printable(&self, request: PrintableRequest) -> Result<PrintableResponse>;
}

/// Mock implementation for now
pub struct MockPrintableService;

#[async_trait]
impl PrintableService for MockPrintableService {
    async fn generate_printable(&self, request: PrintableRequest) -> Result<PrintableResponse> {
        // Mock implementation - return a fake PDF URL
        let export_id = generate_export_id();
        let pdf_url = format!("https://cdn.cleanflow.app/exports/{}.pdf", export_id);
        
        let qr = vec![
            QrMapping {
                task_id: "t_8x1".to_string(),
                qr_id: generate_qr_id(),
            },
        ];
        
        Ok(PrintableResponse {
            export_id,
            pdf_url,
            qr,
        })
    }
}

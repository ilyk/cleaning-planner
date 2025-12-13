//! Printable PDF generation tool with real PDF generation

use crate::{ToolError, ToolResult};
use cleanflow_store::Store;
use serde::Deserialize;
use serde_json::json;
use std::path::PathBuf;

#[cfg(feature = "use-path-security")]
use cleanflow_oss_integrations::path_security_adapter::{create_provider as create_path_provider, PathSecurityProvider};

#[derive(Debug, Deserialize)]
struct GenerateArgs {
    plan_id: String,
    format: Option<String>,
}

/// Generate printable version of plan
pub async fn generate(
    store: &Store,
    home_id: &str,
    args: serde_json::Value,
) -> Result<ToolResult, ToolError> {
    let args: GenerateArgs = serde_json::from_value(args)
        .map_err(|e| ToolError::InvalidArgs(format!("Invalid args: {}", e)))?;

    let plan_id = uuid::Uuid::parse_str(&args.plan_id)
        .map_err(|e| ToolError::InvalidArgs(format!("Invalid plan_id: {}", e)))?;

    tracing::info!(plan_id = %plan_id, home_id = home_id, "Generating printable");

    // Get plan
    let plan = store
        .plan
        .get_plan(plan_id)
        .await
        .map_err(|e| ToolError::Internal(e))?
        .ok_or_else(|| ToolError::InvalidArgs("Plan not found".to_string()))?;

    // Verify home_id
    if plan.home_id != home_id {
        tracing::warn!(
            plan_id = %plan_id,
            plan_home_id = plan.home_id,
            request_home_id = home_id,
            "Home ID mismatch for printable"
        );
        return Err(ToolError::HomeIdMismatch);
    }

    let format_str = args.format.unwrap_or_else(|| "pdf".to_string());
    
    // Generate PDF
    let pdf_path = generate_pdf(&plan, home_id, &format_str).await?;

    // Validate path with path-security if enabled
    #[cfg(feature = "use-path-security")]
    {
        let path_provider = create_path_provider()
            .map_err(|e| ToolError::Internal(anyhow::anyhow!("Failed to create path security provider: {}", e)))?;
        
        let safe_path = path_provider.validate_and_canonicalize(&pdf_path)
            .map_err(|e| ToolError::Internal(anyhow::anyhow!("Path validation failed: {}", e)))?;
        
        tracing::info!(
            plan_id = %plan_id,
            path = ?safe_path,
            "PDF generated and validated"
        );
    }

    // Generate URL (in production, this would be served via CDN)
    let url = format!("/printables/{}/{}.{}", home_id, plan_id, format_str);

    tracing::info!(
        plan_id = %plan_id,
        format = format_str,
        path = ?pdf_path,
        "Generated printable PDF"
    );

    Ok(ToolResult::success(json!({
        "url": url,
        "format": format_str,
        "plan_title": plan.title,
        "path": pdf_path.to_string_lossy(),
    })))
}

#[cfg(feature = "use-pdf")]
async fn generate_pdf(
    plan: &cleanflow_store::Plan,
    home_id: &str,
    format: &str,
) -> Result<PathBuf, ToolError> {
    use printpdf::*;
    use std::fs::File;
    use std::io::BufWriter;

    // Create output directory if it doesn't exist
    let output_dir = PathBuf::from(format!("/tmp/cleanflow/printables/{}", home_id));
    std::fs::create_dir_all(&output_dir)
        .map_err(|e| ToolError::Internal(anyhow::anyhow!("Failed to create output directory: {}", e)))?;

    let file_path = output_dir.join(format!("{}.pdf", plan.plan_id));

    // Create PDF document
    let (doc, page1, layer1) = PdfDocument::new("Clara Plan", Mm(210.0), Mm(297.0), "Layer 1");
    let current_layer = doc.get_page(page1).get_layer(layer1);

    // Set font
    let font = doc.add_builtin_font(BuiltinFont::HelveticaBold)
        .map_err(|e| ToolError::Internal(anyhow::anyhow!("Failed to add font: {}", e)))?;

    // Add title
    current_layer.use_text(&plan.title, 24.0, Mm(20.0), Mm(270.0), &font);
    
    // Add plan content
    let content_str = plan.content.to_string();
    let lines: Vec<&str> = content_str.lines().take(20).collect(); // Limit to first 20 lines
    
    let mut y_position = 250.0;
    for (i, line) in lines.iter().enumerate() {
        if y_position < 30.0 {
            break; // Stop if we run out of space
        }
        
        let text = if line.len() > 80 {
            &line[..80]
        } else {
            line
        };
        
        current_layer.use_text(text, 10.0, Mm(20.0), Mm(y_position), &font);
        y_position -= 12.0;
    }

    // Save PDF
    doc.save(&mut BufWriter::new(File::create(&file_path)
        .map_err(|e| ToolError::Internal(anyhow::anyhow!("Failed to create PDF file: {}", e)))?))
        .map_err(|e| ToolError::Internal(anyhow::anyhow!("Failed to save PDF: {}", e)))?;

    tracing::info!(path = ?file_path, "PDF file created successfully");

    Ok(file_path)
}

#[cfg(not(feature = "use-pdf"))]
async fn generate_pdf(
    plan: &cleanflow_store::Plan,
    home_id: &str,
    format: &str,
) -> Result<PathBuf, ToolError> {
    // Fallback: create a simple text file when PDF is not enabled
    let output_dir = PathBuf::from(format!("/tmp/cleanflow/printables/{}", home_id));
    std::fs::create_dir_all(&output_dir)
        .map_err(|e| ToolError::Internal(anyhow::anyhow!("Failed to create output directory: {}", e)))?;

    let file_path = output_dir.join(format!("{}.{}", plan.plan_id, format));
    
    let content = format!(
        "Plan: {}\n\n{}\n\nGenerated: {}",
        plan.title,
        plan.content,
        chrono::Utc::now().format("%Y-%m-%d %H:%M:%S UTC")
    );

    std::fs::write(&file_path, content)
        .map_err(|e| ToolError::Internal(anyhow::anyhow!("Failed to write file: {}", e)))?;

    Ok(file_path)
}

//! Simple test for Claude integration
//! Run with: SQLX_OFFLINE=true cargo run --example test_claude -p cleanflow-llm

use std::env;
use cleanflow_llm::{ClaudeAdapter, LlmRealtime, LlmEvent};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize logging
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .init();

    // Load API key from environment
    dotenvy::dotenv().ok();
    let api_key = env::var("ANTHROPIC_API_KEY")
        .expect("ANTHROPIC_API_KEY must be set");

    println!("🚀 Testing Claude integration...");
    println!("API Key: {}...", &api_key[..20.min(api_key.len())]);

    // Create Claude adapter directly
    let adapter = ClaudeAdapter::new(
        api_key,
        "claude-sonnet-4-20250514".to_string(),
    );

    println!("✅ Claude adapter created successfully!");

    // Subscribe to events first (to set up the channel)
    let mut rx = adapter.subscribe();

    // Small delay to let async subscribe complete
    tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;

    // Start a turn
    adapter.start_turn("test-turn-1", "v1", "v1")?;
    println!("✅ Turn started");

    // Add text input (using ClaudeAdapter-specific method)
    adapter.add_text_input("Hello, I'm testing the integration. What rooms would you suggest for a typical family home?".to_string());
    println!("✅ Text added");

    // Small delay to let async add complete
    tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;

    // Commit and wait for response
    adapter.commit_input()?;
    println!("⏳ Waiting for response...\n");

    // Wait for events with timeout
    let timeout = tokio::time::Duration::from_secs(60);
    let start = tokio::time::Instant::now();

    loop {
        tokio::select! {
            event_opt = rx.recv() => {
                match event_opt {
                    Some(event) => match event {
                        LlmEvent::OutputTextDelta { text, .. } => {
                            print!("{}", text);
                            std::io::Write::flush(&mut std::io::stdout())?;
                        }
                        LlmEvent::Finished { usage_in, usage_out } => {
                            println!("\n\n✅ Response complete!");
                            println!("📊 Tokens: {} in, {} out", usage_in, usage_out);
                            break;
                        }
                        LlmEvent::Error { message } => {
                            println!("\n❌ Error: {}", message);
                            break;
                        }
                        other => {
                            tracing::debug!(?other, "Received event");
                        }
                    },
                    None => {
                        println!("\n⚠️ Event channel closed");
                        break;
                    }
                }
            }
            _ = tokio::time::sleep(tokio::time::Duration::from_millis(100)) => {
                if start.elapsed() > timeout {
                    println!("\n⏱️ Timeout waiting for response");
                    break;
                }
            }
        }
    }

    Ok(())
}

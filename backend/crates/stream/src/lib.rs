//! WebSocket streaming hub for Clara
//!
//! Handles WebSocket connections, heartbeats, backpressure, sequence validation,
//! and barge-in support.

pub mod handler;
pub mod heartbeat;
pub mod turn_executor;
pub mod validation;

pub use handler::handle_websocket;


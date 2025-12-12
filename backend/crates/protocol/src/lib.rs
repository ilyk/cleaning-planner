//! Clara Streaming Protocol
//!
//! This crate defines the message types, error codes, and protocol version
//! for the Clara streaming interface between app and backend.

pub mod errors;
pub mod messages;
pub mod version;

pub use errors::{ErrorCode, ProtocolError};
pub use messages::{
    AudioFormat, InboundMessage, OutboundMessage, TurnFinishMetadata, UsageMetadata,
};
pub use version::PROTOCOL_VERSION;

